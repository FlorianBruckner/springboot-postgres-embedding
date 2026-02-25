package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.mapper.ArticleEntityMapper;
import com.dreikraft.ai.embedding.postgres.model.ArticleCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.ArticleJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
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
    private final SemanticSearchRerankingService rerankingService;
    private final boolean queryRewriteEnabled;
    private final boolean dualQueryEnabled;

    public ArticleService(ArticleJpaRepository articleRepository,
                          ArticleEntityMapper articleMapper,
                          SemanticSummaryService semanticSummaryService,
                          DocumentVectorStoreService vectorStoreService,
                          DocumentIndexingJobService documentIndexingJobService,
                          SemanticSearchRerankingService rerankingService,
                          @Value("${app.semantic-search.query-rewrite.enabled:true}") boolean queryRewriteEnabled,
                          @Value("${app.semantic-search.dual-query.enabled:false}") boolean dualQueryEnabled) {
        this.articleRepository = articleRepository;
        this.articleMapper = articleMapper;
        this.semanticSummaryService = semanticSummaryService;
        this.vectorStoreService = vectorStoreService;
        this.documentIndexingJobService = documentIndexingJobService;
        this.rerankingService = rerankingService;
        this.queryRewriteEnabled = queryRewriteEnabled;
        this.dualQueryEnabled = dualQueryEnabled;
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
        String rewrittenQuery = queryRewriteEnabled
                ? semanticSummaryService.summarizeQueryForSemanticSearch(query)
                : query;

        List<Long> ids = dualQueryEnabled
                ? mergeRankedIds(
                vectorStoreService.searchIds(query, 20, filterExpression),
                vectorStoreService.searchIds(rewrittenQuery, 20, filterExpression)
        )
                : vectorStoreService.searchIds(rewrittenQuery, 20, filterExpression);
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, ArticleDocument> byId = articleRepository.findArticlesByIdIn(ids)
                .stream()
                .map(articleMapper::toArticleDocument)
                .collect(Collectors.toMap(ArticleDocument::id, article -> article));

        List<ArticleDocument> candidates = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        List<Long> rerankedIds = rerankingService.rerank(query, ids, candidates);

        return rerankedIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private List<Long> mergeRankedIds(List<Long> originalQueryIds, List<Long> rewrittenQueryIds) {
        Map<Long, Double> rrfScores = new HashMap<>();
        Map<Long, Integer> bestRank = new HashMap<>();

        accumulateRrfScores(originalQueryIds, rrfScores, bestRank);
        accumulateRrfScores(rewrittenQueryIds, rrfScores, bestRank);

        return rrfScores.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<Long, Double>>comparingDouble(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(entry -> bestRank.get(entry.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
    }

    private void accumulateRrfScores(List<Long> ids, Map<Long, Double> rrfScores, Map<Long, Integer> bestRank) {
        final int rankConstant = 60;
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            int rank = i + 1;
            double score = 1.0d / (rankConstant + rank);
            rrfScores.merge(id, score, Double::sum);
            bestRank.merge(id, rank, Math::min);
        }
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
