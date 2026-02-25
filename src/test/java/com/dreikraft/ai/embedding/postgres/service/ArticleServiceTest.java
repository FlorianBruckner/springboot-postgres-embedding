package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.mapper.ArticleEntityMapper;
import com.dreikraft.ai.embedding.postgres.model.ArticleCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.ArticleJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleServiceTest {

    @Test
    void createPersistsArticleAndEnqueuesIndexingJob() {
        ArticleJpaRepository articleRepository = mock(ArticleJpaRepository.class);
        ArticleEntityMapper articleMapper = mock(ArticleEntityMapper.class);
        SemanticSummaryService summaryService = mock(SemanticSummaryService.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);
        DocumentIndexingJobService documentIndexingJobService = mock(DocumentIndexingJobService.class);
        SemanticSearchRerankingService rerankingService = mock(SemanticSearchRerankingService.class);

        ArticleService service = new ArticleService(
                articleRepository,
                articleMapper,
                summaryService,
                vectorStoreService,
                documentIndexingJobService,
                rerankingService,
                true,
                false
        );

        ArticleEntity saved = new ArticleEntity();
        saved.setId(5L);
        when(articleRepository.save(org.mockito.ArgumentMatchers.any(ArticleEntity.class))).thenReturn(saved);

        service.create(new ArticleCreateRequest("Article", "Raw body"));

        verify(documentIndexingJobService).enqueue(DocumentIndexingJobType.EMBED_UPSERT, DocumentType.ARTICLE, 5L);
    }

    @Test
    void semanticSearchUsesOriginalQueryWhenQueryRewriteDisabled() {
        ArticleJpaRepository articleRepository = mock(ArticleJpaRepository.class);
        ArticleEntityMapper articleMapper = mock(ArticleEntityMapper.class);
        SemanticSummaryService summaryService = mock(SemanticSummaryService.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);
        DocumentIndexingJobService documentIndexingJobService = mock(DocumentIndexingJobService.class);
        SemanticSearchRerankingService rerankingService = mock(SemanticSearchRerankingService.class);

        ArticleService service = new ArticleService(
                articleRepository,
                articleMapper,
                summaryService,
                vectorStoreService,
                documentIndexingJobService,
                rerankingService,
                false,
                false
        );

        when(vectorStoreService.searchIds("raw query", 20, "sampleType == 'article'"))
                .thenReturn(List.of(3L));

        ArticleEntity articleEntity = new ArticleEntity();
        articleEntity.setId(3L);
        when(articleRepository.findArticlesByIdIn(List.of(3L))).thenReturn(List.of(articleEntity));
        when(articleMapper.toArticleDocument(articleEntity)).thenReturn(new ArticleDocument(3L, "t", "c", null));
        when(rerankingService.rerank(org.mockito.ArgumentMatchers.eq("raw query"), org.mockito.ArgumentMatchers.eq(List.of(3L)), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(3L));

        List<ArticleDocument> results = service.semanticSearch("raw query", "sampleType == 'article'");

        assertEquals(1, results.size());
        verify(summaryService, never()).summarizeQueryForSemanticSearch("raw query");
        verify(vectorStoreService).searchIds("raw query", 20, "sampleType == 'article'");
        verify(rerankingService).rerank(org.mockito.ArgumentMatchers.eq("raw query"), org.mockito.ArgumentMatchers.eq(List.of(3L)), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void semanticSearchReranksAndDedupesOriginalAndRewrittenResultsWhenDualQueryEnabled() {
        ArticleJpaRepository articleRepository = mock(ArticleJpaRepository.class);
        ArticleEntityMapper articleMapper = mock(ArticleEntityMapper.class);
        SemanticSummaryService summaryService = mock(SemanticSummaryService.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);
        DocumentIndexingJobService documentIndexingJobService = mock(DocumentIndexingJobService.class);
        SemanticSearchRerankingService rerankingService = mock(SemanticSearchRerankingService.class);

        ArticleService service = new ArticleService(
                articleRepository,
                articleMapper,
                summaryService,
                vectorStoreService,
                documentIndexingJobService,
                rerankingService,
                true,
                true
        );

        when(summaryService.summarizeQueryForSemanticSearch("climate impact")).thenReturn("climate change impact");
        when(vectorStoreService.searchIds("climate impact", 20, "sampleType == 'article'"))
                .thenReturn(List.of(10L, 11L));
        when(vectorStoreService.searchIds("climate change impact", 20, "sampleType == 'article'"))
                .thenReturn(List.of(11L, 12L));

        ArticleEntity entity10 = new ArticleEntity();
        entity10.setId(10L);
        ArticleEntity entity11 = new ArticleEntity();
        entity11.setId(11L);
        ArticleEntity entity12 = new ArticleEntity();
        entity12.setId(12L);

        when(articleRepository.findArticlesByIdIn(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(entity10, entity11, entity12));
        when(articleMapper.toArticleDocument(entity10)).thenReturn(new ArticleDocument(10L, "a", "a", null));
        when(articleMapper.toArticleDocument(entity11)).thenReturn(new ArticleDocument(11L, "b", "b", null));
        when(articleMapper.toArticleDocument(entity12)).thenReturn(new ArticleDocument(12L, "c", "c", null));
        when(rerankingService.rerank(org.mockito.ArgumentMatchers.eq("climate impact"), org.mockito.ArgumentMatchers.eq(List.of(11L, 10L, 12L)), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(12L, 11L, 10L));

        List<ArticleDocument> results = service.semanticSearch("climate impact", "sampleType == 'article'");

        assertEquals(List.of(12L, 11L, 10L), results.stream().map(ArticleDocument::id).toList());
        verify(vectorStoreService).searchIds("climate impact", 20, "sampleType == 'article'");
        verify(vectorStoreService).searchIds("climate change impact", 20, "sampleType == 'article'");
        verify(rerankingService).rerank(org.mockito.ArgumentMatchers.eq("climate impact"), org.mockito.ArgumentMatchers.eq(List.of(11L, 10L, 12L)), org.mockito.ArgumentMatchers.anyList());
    }
}
