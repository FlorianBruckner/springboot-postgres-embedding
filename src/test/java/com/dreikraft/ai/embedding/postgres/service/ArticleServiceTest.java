package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.mapper.ArticleEntityMapper;
import com.dreikraft.ai.embedding.postgres.model.ArticleCreateRequest;
import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.ArticleJpaRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
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

        ArticleService service = new ArticleService(
                articleRepository,
                articleMapper,
                summaryService,
                vectorStoreService,
                documentIndexingJobService
        );

        ArticleEntity saved = new ArticleEntity();
        saved.setId(5L);
        when(articleRepository.save(org.mockito.ArgumentMatchers.any(ArticleEntity.class))).thenReturn(saved);

        service.create(new ArticleCreateRequest("Article", "Raw body"));

        verify(documentIndexingJobService).enqueue(DocumentIndexingJobService.JOB_TYPE_UPSERT_VECTOR, "article", 5L);
    }
}
