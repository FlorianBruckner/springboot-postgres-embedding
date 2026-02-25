package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.mapper.DiscussionEntityMapper;
import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.dreikraft.ai.embedding.postgres.model.DiscussionCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.dreikraft.ai.embedding.postgres.model.ThreadedDiscussionItem;
import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import com.dreikraft.ai.embedding.postgres.persistence.entity.DiscussionEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.ArticleJpaRepository;
import com.dreikraft.ai.embedding.postgres.persistence.repository.DiscussionJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class DiscussionService {

    private final DiscussionJpaRepository discussionRepository;
    private final ArticleJpaRepository articleRepository;
    private final DiscussionEntityMapper discussionMapper;
    private final DiscussionClassificationService discussionClassificationService;
    private final EmbeddingTransformationService embeddingTransformationService;
    private final DocumentVectorStoreService vectorStoreService;

    public DiscussionService(DiscussionJpaRepository discussionRepository,
                             ArticleJpaRepository articleRepository,
                             DiscussionEntityMapper discussionMapper,
                             DiscussionClassificationService discussionClassificationService,
                             EmbeddingTransformationService embeddingTransformationService,
                             DocumentVectorStoreService vectorStoreService) {
        this.discussionRepository = discussionRepository;
        this.articleRepository = articleRepository;
        this.discussionMapper = discussionMapper;
        this.discussionClassificationService = discussionClassificationService;
        this.embeddingTransformationService = embeddingTransformationService;
        this.vectorStoreService = vectorStoreService;
    }

    public long create(DiscussionCreateRequest request) {
        DiscussionEntity entity = new DiscussionEntity();
        entity.setTitle(request.title());
        entity.setContent(request.content());
        entity.setDiscussionSection(request.discussionSection());

        if (request.respondsToDocumentId() != null) {
            DiscussionEntity parent = discussionRepository.findDiscussionById(request.respondsToDocumentId())
                    .orElseThrow(() -> new IllegalArgumentException("Discussion not found: " + request.respondsToDocumentId()));
            entity.setParentDiscussion(parent);
        } else {
            ArticleEntity article = articleRepository.findArticleById(request.relatedArticleDocumentId())
                    .orElseThrow(() -> new IllegalArgumentException("Article not found: " + request.relatedArticleDocumentId()));
            entity.setArticle(article);
        }

        DiscussionEntity saved = discussionRepository.save(entity);
        ArticleDocument article = findArticle(resolveArticleId(saved));

        List<EmbeddingTransformationService.EmbeddingVariant> variants = embeddingTransformationService
                .transformForDiscussion(article.title(), saved.getTitle(), saved.getContent());
        vectorStoreService.upsertVariants(saved.getId(), "discussion", saved.getTitle(), variants, metadata(saved, resolveArticleId(saved)));

        refreshDiscussionClassifications(resolveArticleId(saved));
        return saved.getId();
    }

    public void update(long discussionId, String content) {
        DiscussionEntity discussion = discussionRepository.findDiscussionById(discussionId)
                .orElseThrow(() -> new IllegalArgumentException("Discussion not found: " + discussionId));
        discussion.setContent(content);
        DiscussionEntity saved = discussionRepository.save(discussion);

        long articleId = resolveArticleId(saved);
        ArticleDocument article = findArticle(articleId);
        List<EmbeddingTransformationService.EmbeddingVariant> variants = embeddingTransformationService
                .transformForDiscussion(article.title(), saved.getTitle(), saved.getContent());
        vectorStoreService.upsertVariants(saved.getId(), "discussion", saved.getTitle(), variants, metadata(saved, articleId));

        refreshDiscussionClassifications(articleId);
    }

    @Transactional(readOnly = true)
    public List<ThreadedDiscussionItem> findThreadedDiscussionsByArticleId(long articleDocumentId) {
        List<DiscussionDocument> discussions = findDiscussionsByArticleId(articleDocumentId);
        if (discussions.isEmpty()) {
            return List.of();
        }

        Map<Long, List<DiscussionDocument>> childrenByParent = new LinkedHashMap<>();
        for (DiscussionDocument discussion : discussions) {
            childrenByParent.computeIfAbsent(discussion.parentDocumentId(), ignored -> new ArrayList<>())
                    .add(discussion);
        }
        childrenByParent.values().forEach(children -> children.sort(Comparator.comparing(DiscussionDocument::id)));

        List<ThreadedDiscussionItem> threaded = new ArrayList<>();
        appendThread(threaded, childrenByParent, null, 0);
        return threaded;
    }

    @Transactional(readOnly = true)
    public long count() {
        return discussionRepository.countDiscussions();
    }

    private void refreshDiscussionClassifications(long articleDocumentId) {
        List<DiscussionDocument> discussions = findDiscussionsByArticleId(articleDocumentId);
        if (discussions.isEmpty()) {
            return;
        }

        ArticleDocument article = findArticle(articleDocumentId);
        Map<Long, DiscussionClassificationService.DiscussionClassification> classifications =
                discussionClassificationService.classify(new DiscussionClassificationService.DiscussionClassificationInput(
                        article.title(),
                        article.content(),
                        discussions
                ));

        for (DiscussionDocument discussion : discussions) {
            DiscussionClassificationService.DiscussionClassification classification = classifications.get(discussion.id());
            DiscussionEntity entity = discussionRepository.findDiscussionById(discussion.id())
                    .orElseThrow(() -> new IllegalStateException("Discussion missing during classification update: " + discussion.id()));
            entity.setSentiment(classification == null ? "neutral" : classification.sentiment());
            entity.setResponseDepth(classification == null ? "substantive" : classification.responseDepth());
            entity.setClassificationStatus("completed");
            entity.setClassificationSource("llm");
            entity.setClassifiedAt(java.time.OffsetDateTime.now());
            discussionRepository.save(entity);
        }
    }

    private List<DiscussionDocument> findDiscussionsByArticleId(long articleDocumentId) {
        List<DiscussionEntity> roots = discussionRepository.findRootDiscussionsByArticleDocumentIdOrderByIdAsc(articleDocumentId);
        List<DiscussionDocument> flattened = new ArrayList<>();
        for (DiscussionEntity root : roots) {
            appendDiscussion(flattened, root);
        }
        return flattened;
    }

    private void appendDiscussion(List<DiscussionDocument> target, DiscussionEntity entity) {
        target.add(discussionMapper.toDiscussionDocument(entity));
        for (DiscussionEntity response : entity.getResponses()) {
            appendDiscussion(target, response);
        }
    }

    private void appendThread(List<ThreadedDiscussionItem> threaded,
                              Map<Long, List<DiscussionDocument>> childrenByParent,
                              Long parentDocumentId,
                              int depth) {
        for (DiscussionDocument child : childrenByParent.getOrDefault(parentDocumentId, List.of())) {
            threaded.add(new ThreadedDiscussionItem(
                    child,
                    depth,
                    child.sentiment() == null ? "neutral" : child.sentiment(),
                    child.responseDepth() == null ? "substantive" : child.responseDepth()
            ));
            appendThread(threaded, childrenByParent, child.id(), depth + 1);
        }
    }

    private long resolveArticleId(DiscussionEntity discussion) {
        if (discussion.getArticle() != null) {
            return discussion.getArticle().getId();
        }
        DiscussionEntity parent = discussion.getParentDiscussion();
        while (parent != null) {
            if (parent.getArticle() != null) {
                return parent.getArticle().getId();
            }
            parent = parent.getParentDiscussion();
        }
        throw new IllegalStateException("Discussion does not resolve to an article: " + discussion.getId());
    }

    private ArticleDocument findArticle(long id) {
        ArticleEntity article = articleRepository.findArticleById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));
        return new ArticleDocument(article.getId(), article.getTitle(), article.getContent(), article.getUpdatedAt());
    }

    private Map<String, Object> metadata(DiscussionEntity discussion, long articleId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sampleType", "discussion");
        metadata.put("relatedArticleDocumentId", articleId);
        if (discussion.getParentDiscussion() != null) {
            metadata.put("respondsToDocumentId", discussion.getParentDiscussion().getId());
        }
        if (discussion.getDiscussionSection() != null && !discussion.getDiscussionSection().isBlank()) {
            metadata.put("discussionSection", discussion.getDiscussionSection());
        }
        return metadata;
    }
}
