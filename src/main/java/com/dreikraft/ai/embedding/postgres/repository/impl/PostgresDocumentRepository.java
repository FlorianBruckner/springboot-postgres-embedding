package com.dreikraft.ai.embedding.postgres.repository.impl;

import com.dreikraft.ai.embedding.postgres.mapper.ArticleEntityMapper;
import com.dreikraft.ai.embedding.postgres.mapper.DiscussionEntityMapper;
import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import com.dreikraft.ai.embedding.postgres.persistence.entity.DiscussionEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.ArticleJpaRepository;
import com.dreikraft.ai.embedding.postgres.persistence.repository.DiscussionJpaRepository;
import com.dreikraft.ai.embedding.postgres.repository.DocumentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Transactional
@ConditionalOnProperty(prefix = "app.database", name = "vendor", havingValue = "postgres", matchIfMissing = true)
public class PostgresDocumentRepository implements DocumentRepository {
    private static final String TYPE_ARTICLE = "article";
    private static final String TYPE_DISCUSSION = "discussion";

    private final ArticleJpaRepository articleRepository;
    private final DiscussionJpaRepository discussionRepository;
    private final ArticleEntityMapper articleMapper;
    private final DiscussionEntityMapper discussionMapper;

    public PostgresDocumentRepository(ArticleJpaRepository articleRepository,
                                      DiscussionJpaRepository discussionRepository,
                                      ArticleEntityMapper articleMapper,
                                      DiscussionEntityMapper discussionMapper) {
        this.articleRepository = articleRepository;
        this.discussionRepository = discussionRepository;
        this.articleMapper = articleMapper;
        this.discussionMapper = discussionMapper;
    }

    @Override
    public long create(DocumentCreateRequest request) {
        Map<String, Object> properties = request.propertiesOrEmpty();
        Long relatedArticleId = toLong(properties.get("relatedArticleDocumentId"));
        if (relatedArticleId == null) {
            ArticleEntity entity = new ArticleEntity();
            entity.setTitle(request.title());
            entity.setContent(request.content());
            return articleRepository.save(entity).getId();
        }

        DiscussionEntity entity = new DiscussionEntity();
        entity.setTitle(request.title());
        entity.setContent(request.content());
        entity.setArticleDocumentId(relatedArticleId);
        entity.setParentDocumentId(toLong(properties.get("respondsToDocumentId")));
        entity.setDiscussionSection(toStringValue(properties.get("discussionSection")));
        return discussionRepository.save(entity).getId();
    }

    @Override
    public void update(long id, String content) {
        Optional<ArticleEntity> article = articleRepository.findById(id);
        if (article.isPresent()) {
            ArticleEntity entity = article.get();
            entity.setContent(content);
            entity.setContentHash(hashContent(content));
            entity.setEmbeddedAt(null);
            articleRepository.save(entity);
            return;
        }

        DiscussionEntity discussion = discussionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
        discussion.setContent(content);
        discussionRepository.save(discussion);
    }

    @Override
    public void updateDiscussionClassification(long id, String sentiment, String responseDepth) {
        DiscussionEntity entity = discussionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        entity.setSentiment(sentiment);
        entity.setResponseDepth(responseDepth);
        entity.setClassificationStatus("completed");
        entity.setClassifiedAt(OffsetDateTime.now());
        if (entity.getClassificationSource() == null || entity.getClassificationSource().isBlank()) {
            entity.setClassificationSource("llm");
        }

        discussionRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArticleDocument> findById(long id) {
        return articleRepository.findById(id).map(articleMapper::toArticleDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleDocument> findByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ArticleEntity> entities = articleRepository.findArticlesByIdIn(ids);
        Map<Long, ArticleDocument> byId = entities.stream()
                .map(articleMapper::toArticleDocument)
                .collect(Collectors.toMap(ArticleDocument::id, a -> a));

        List<ArticleDocument> ordered = new ArrayList<>();
        for (Long id : ids) {
            ArticleDocument article = byId.get(id);
            if (article != null) {
                ordered.add(article);
            }
        }
        return ordered;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleDocument> keywordSearch(String term, int limit) {
        return articleRepository.keywordSearchArticles(term, limit).stream()
                .map(articleMapper::toArticleDocument)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleDocument> keywordSearchBySampleType(String term, int limit, String sampleType) {
        if (TYPE_DISCUSSION.equals(sampleType)) {
            return List.of();
        }
        return articleRepository.keywordSearchArticles(term, limit).stream()
                .map(articleMapper::toArticleDocument)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscussionDocument> findDiscussionsByArticleId(long articleDocumentId) {
        return discussionRepository.findByArticleDocumentIdOrderByIdAsc(articleDocumentId)
                .stream()
                .map(discussionMapper::toDiscussionDocument)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> findVectorMetadataById(long id) {
        Optional<DiscussionEntity> discussionOpt = discussionRepository.findById(id);
        if (discussionOpt.isPresent()) {
            DiscussionEntity discussion = discussionOpt.get();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sampleType", TYPE_DISCUSSION);
            metadata.put("relatedArticleDocumentId", discussion.getArticleDocumentId());
            if (discussion.getParentDocumentId() != null) {
                metadata.put("respondsToDocumentId", discussion.getParentDocumentId());
            }
            if (discussion.getDiscussionSection() != null && !discussion.getDiscussionSection().isBlank()) {
                metadata.put("discussionSection", discussion.getDiscussionSection());
            }
            return metadata;
        }

        if (articleRepository.countArticleById(id) > 0) {
            return Map.of("sampleType", TYPE_ARTICLE);
        }
        throw new IllegalArgumentException("Document not found: " + id);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return articleRepository.countArticles() + discussionRepository.count();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String hashContent(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
