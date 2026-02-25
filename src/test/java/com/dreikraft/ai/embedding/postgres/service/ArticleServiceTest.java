package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.mapper.ArticleEntityMapper;
import com.dreikraft.ai.embedding.postgres.model.ArticleCreateRequest;
import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.ArticleJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleServiceTest {

    @Test
    void createUsesTransformationPipelineAndStoresMultipleEmbeddings() {
        ArticleJpaRepository articleRepository = mock(ArticleJpaRepository.class);
        ArticleEntityMapper articleMapper = mock(ArticleEntityMapper.class);
        SemanticSummaryService summaryService = mock(SemanticSummaryService.class);
        EmbeddingTransformationService transformationService = mock(EmbeddingTransformationService.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);

        ArticleService service = new ArticleService(
                articleRepository,
                articleMapper,
                summaryService,
                transformationService,
                vectorStoreService
        );

        ArticleEntity saved = new ArticleEntity();
        saved.setId(5L);
        when(articleRepository.save(org.mockito.ArgumentMatchers.any(ArticleEntity.class))).thenReturn(saved);
        when(summaryService.summarizeDocumentForEmbedding("Article", "Raw body")).thenReturn("Summary");
        when(transformationService.transformForArticle("Article", "Summary")).thenReturn(List.of(
                new EmbeddingTransformationService.EmbeddingVariant("original", "Summary"),
                new EmbeddingTransformationService.EmbeddingVariant("keywords", "k1, k2")
        ));

        service.create(new ArticleCreateRequest("Article", "Raw body"));

        verify(vectorStoreService).upsertVariants(5L, "article", "Article", List.of(
                new EmbeddingTransformationService.EmbeddingVariant("original", "Summary"),
                new EmbeddingTransformationService.EmbeddingVariant("keywords", "k1, k2")
        ), Map.of("sampleType", "article"));
    }
}
