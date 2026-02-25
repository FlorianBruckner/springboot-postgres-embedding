package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.mapper.ArticleEntityMapper;
import com.dreikraft.ai.embedding.postgres.model.ArticleCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.ArticleJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ArticleService {
    public static final String ARTICLE_FILTER_EXPRESSION = "sampleType == 'article'";

    private final ArticleJpaRepository articleRepository;
    private final ArticleEntityMapper articleMapper;
    private final SemanticSummaryService semanticSummaryService;
    private final DocumentVectorStoreService vectorStoreService;
    private final DocumentIndexingJobService documentIndexingJobService;

    public ArticleService(ArticleJpaRepository articleRepository,
                          ArticleEntityMapper articleMapper,
                          SemanticSummaryService semanticSummaryService,
                          DocumentVectorStoreService vectorStoreService,
                          DocumentIndexingJobService documentIndexingJobService) {
        this.articleRepository = articleRepository;
        this.articleMapper = articleMapper;
        this.semanticSummaryService = semanticSummaryService;
        this.vectorStoreService = vectorStoreService;
        this.documentIndexingJobService = documentIndexingJobService;
    }

    public long create(ArticleCreateRequest request) {
        ArticleEntity entity = new ArticleEntity();
        entity.setTitle(request.title());
        entity.setContent(request.content());
        ArticleEntity saved = articleRepository.save(entity);
        documentIndexingJobService.enqueue(DocumentIndexingJobType.EMBED_UPSERT, DocumentType.ARTICLE, saved.getId());
        return saved.getId();
    }

    public void update(long id, String content) {
        ArticleEntity entity = articleRepository.findArticleById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));
        entity.setContent(content);
        entity.setContentHash(hashContent(content));
        entity.setEmbeddedAt(null);
        articleRepository.save(entity);
        documentIndexingJobService.enqueue(DocumentIndexingJobType.EMBED_UPSERT, DocumentType.ARTICLE, id);
    }

    @Transactional(readOnly = true)
    public ArticleDocument findById(long id) {
        return articleRepository.findArticleById(id)
                .map(articleMapper::toArticleDocument)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ArticleDocument> keywordSearch(String term) {
        return articleRepository.keywordSearchArticles(term, 20).stream().map(articleMapper::toArticleDocument).toList();
    }

    @Transactional(readOnly = true)
    public List<ArticleDocument> semanticSearch(String query) {
        return semanticSearch(query, ARTICLE_FILTER_EXPRESSION);
    }

    @Transactional(readOnly = true)
    public List<ArticleDocument> semanticSearch(String query, String filterExpression) {
        String summarizedQuery = semanticSummaryService.summarizeQueryForSemanticSearch(query);
        List<Long> ids = vectorStoreService.searchIds(summarizedQuery, 20, filterExpression);
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, ArticleDocument> byId = articleRepository.findArticlesByIdIn(ids)
                .stream()
                .map(articleMapper::toArticleDocument)
                .collect(Collectors.toMap(ArticleDocument::id, article -> article));

        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public long count() {
        return articleRepository.countArticles();
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
