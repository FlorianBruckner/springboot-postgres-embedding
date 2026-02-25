package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.ArticleJpaRepository;
import com.dreikraft.ai.embedding.postgres.persistence.repository.DiscussionJpaRepository;
import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobRecord;
import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobRepository;
import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIndexingWorkerServiceTest {

    @Test
    void runQueueProcessesEmbedUpsertJobsAndMarksSucceeded() {
        DocumentIndexingJobRepository jobRepository = mock(DocumentIndexingJobRepository.class);
        ArticleJpaRepository articleRepository = mock(ArticleJpaRepository.class);
        DiscussionJpaRepository discussionRepository = mock(DiscussionJpaRepository.class);
        EmbeddingTransformationService embeddingTransformationService = mock(EmbeddingTransformationService.class);
        SemanticSummaryService semanticSummaryService = mock(SemanticSummaryService.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);
        DiscussionClassificationService discussionClassificationService = mock(DiscussionClassificationService.class);

        DocumentIndexingWorkerService worker = new DocumentIndexingWorkerService(
                jobRepository,
                articleRepository,
                discussionRepository,
                embeddingTransformationService,
                semanticSummaryService,
                vectorStoreService,
                discussionClassificationService,
                10,
                1000,
                1200,
                "test-model"
        );

        DocumentIndexingJobRecord job = new DocumentIndexingJobRecord(
                1L,
                DocumentIndexingJobType.EMBED_UPSERT.name(),
                DocumentType.ARTICLE.value(),
                42L,
                DocumentIndexingJobStatus.PENDING,
                0,
                5,
                OffsetDateTime.now(),
                null,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        ArticleEntity article = new ArticleEntity();
        article.setId(42L);
        article.setTitle("Title");
        article.setContent("short content");

        when(jobRepository.pollDue(eq(DocumentIndexingJobStatus.PENDING), any(), eq(10))).thenReturn(List.of(job));
        when(jobRepository.claimPending(eq(1L), any())).thenReturn(true);
        when(articleRepository.findArticleById(42L)).thenReturn(Optional.of(article));
        when(embeddingTransformationService.transformForArticle("Title", "short content"))
                .thenReturn(List.of(new EmbeddingTransformationService.EmbeddingVariant("original", "short content")));

        worker.runQueue();

        verify(vectorStoreService).upsertVariants(eq(42L), eq(DocumentType.ARTICLE.value()), eq("Title"), any(), any());
        verify(jobRepository).markSucceeded(eq(1L), any());
    }

    @Test
    void runQueueRetriesTransientFailuresWithBackoff() {
        DocumentIndexingJobRepository jobRepository = mock(DocumentIndexingJobRepository.class);
        ArticleJpaRepository articleRepository = mock(ArticleJpaRepository.class);
        DiscussionJpaRepository discussionRepository = mock(DiscussionJpaRepository.class);
        EmbeddingTransformationService embeddingTransformationService = mock(EmbeddingTransformationService.class);
        SemanticSummaryService semanticSummaryService = mock(SemanticSummaryService.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);
        DiscussionClassificationService discussionClassificationService = mock(DiscussionClassificationService.class);

        DocumentIndexingWorkerService worker = new DocumentIndexingWorkerService(
                jobRepository,
                articleRepository,
                discussionRepository,
                embeddingTransformationService,
                semanticSummaryService,
                vectorStoreService,
                discussionClassificationService,
                10,
                1000,
                1200,
                "test-model"
        );

        DocumentIndexingJobRecord job = new DocumentIndexingJobRecord(
                2L,
                DocumentIndexingJobType.EMBED_UPSERT.name(),
                DocumentType.ARTICLE.value(),
                7L,
                DocumentIndexingJobStatus.PENDING,
                1,
                5,
                OffsetDateTime.now(),
                null,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(jobRepository.pollDue(eq(DocumentIndexingJobStatus.PENDING), any(), eq(10))).thenReturn(List.of(job));
        when(jobRepository.claimPending(eq(2L), any())).thenReturn(true);
        when(articleRepository.findArticleById(7L)).thenThrow(new TransientDataAccessResourceException("db busy"));
        when(jobRepository.markFailedWithRetry(eq(2L), any(), any())).thenReturn(true);

        worker.runQueue();

        verify(jobRepository).markFailedWithRetry(eq(2L), any(), any());
    }
}
