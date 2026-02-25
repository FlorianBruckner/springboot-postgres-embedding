package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import com.dreikraft.ai.embedding.postgres.persistence.entity.DiscussionEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.ArticleJpaRepository;
import com.dreikraft.ai.embedding.postgres.persistence.repository.DiscussionJpaRepository;
import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobRecord;
import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobRepository;
import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class DocumentIndexingWorkerService {

    private final DocumentIndexingJobRepository jobRepository;
    private final ArticleJpaRepository articleRepository;
    private final DiscussionJpaRepository discussionRepository;
    private final EmbeddingTransformationService embeddingTransformationService;
    private final SemanticSummaryService semanticSummaryService;
    private final DocumentVectorStoreService vectorStoreService;
    private final DiscussionClassificationService discussionClassificationService;

    private final int batchSize;
    private final long baseBackoffMillis;
    private final int summarizeThresholdChars;
    private final String embeddingModel;

    public DocumentIndexingWorkerService(
            DocumentIndexingJobRepository jobRepository,
            ArticleJpaRepository articleRepository,
            DiscussionJpaRepository discussionRepository,
            EmbeddingTransformationService embeddingTransformationService,
            SemanticSummaryService semanticSummaryService,
            DocumentVectorStoreService vectorStoreService,
            DiscussionClassificationService discussionClassificationService,
            @Value("${app.document-indexing.worker.batch-size:10}") int batchSize,
            @Value("${app.document-indexing.worker.base-backoff-ms:2000}") long baseBackoffMillis,
            @Value("${app.document-indexing.worker.summarize-threshold-chars:1200}") int summarizeThresholdChars,
            @Value("${spring.ai.openai.embedding.options.model:unknown}") String embeddingModel) {
        this.jobRepository = jobRepository;
        this.articleRepository = articleRepository;
        this.discussionRepository = discussionRepository;
        this.embeddingTransformationService = embeddingTransformationService;
        this.semanticSummaryService = semanticSummaryService;
        this.vectorStoreService = vectorStoreService;
        this.discussionClassificationService = discussionClassificationService;
        this.batchSize = batchSize;
        this.baseBackoffMillis = baseBackoffMillis;
        this.summarizeThresholdChars = summarizeThresholdChars;
        this.embeddingModel = embeddingModel;
    }

    @Scheduled(fixedDelayString = "${app.document-indexing.worker.fixed-delay-ms:1000}")
    @Transactional
    public void runQueue() {
        OffsetDateTime now = OffsetDateTime.now();
        List<DocumentIndexingJobRecord> dueJobs = jobRepository.pollDue(DocumentIndexingJobStatus.PENDING, now, batchSize);
        if (dueJobs.isEmpty()) {
            log.debug("No due indexing jobs found");
            return;
        }

        log.info("Polled {} due indexing jobs", dueJobs.size());
        for (DocumentIndexingJobRecord job : dueJobs) {
            if (!jobRepository.claimPending(job.id(), now)) {
                log.debug("Skipping job {} because claim failed", job.id());
                continue;
            }
            processClaimedJob(job);
        }
    }

    private void processClaimedJob(DocumentIndexingJobRecord job) {
        log.info("Processing job id={}, type={}, documentType={}, documentId={}, attempt={}/{}",
                job.id(), job.jobType(), job.documentType(), job.documentId(), job.attempt() + 1, job.maxAttempts());
        try {
            dispatch(job);
            jobRepository.markSucceeded(job.id(), OffsetDateTime.now());
            log.info("Job {} completed successfully", job.id());
        } catch (Exception ex) {
            handleFailure(job, ex);
        }
    }

    private void dispatch(DocumentIndexingJobRecord job) {
        DocumentIndexingJobType jobType = parseJobType(job.jobType());
        log.debug("Dispatching job {} as {}", job.id(), jobType);
        switch (jobType) {
            case EMBED_UPSERT -> processEmbedUpsert(job.documentType(), job.documentId());
            case DISCUSSION_CLASSIFY -> processDiscussionClassify(job.documentType(), job.documentId());
            default -> throw new PermanentJobFailureException("Unknown job_type: " + job.jobType());
        }
    }

    private DocumentIndexingJobType parseJobType(String value) {
        try {
            return DocumentIndexingJobType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new PermanentJobFailureException("Unknown job_type: " + value);
        }
    }

    private void processEmbedUpsert(String documentType, long documentId) {
        switch (DocumentType.fromValue(documentType)) {
            case ARTICLE -> embedArticle(documentId);
            case DISCUSSION -> embedDiscussion(documentId);
            default -> throw new PermanentJobFailureException("Unsupported document_type for EMBED_UPSERT: " + documentType);
        }
    }

    private void processDiscussionClassify(String documentType, long documentId) {
        if (DocumentType.fromValue(documentType) != DocumentType.ARTICLE) {
            throw new PermanentJobFailureException("DISCUSSION_CLASSIFY expects article document_type but got: " + documentType);
        }

        log.info("Starting discussion classification for article {}", documentId);
        ArticleEntity article = articleRepository.findArticleById(documentId)
                .orElseThrow(() -> new PermanentJobFailureException("Article not found: " + documentId));
        List<DiscussionEntity> discussions = flattenDiscussionTree(documentId);
        if (discussions.isEmpty()) {
            log.info("No discussions to classify for article {}", documentId);
            return;
        }

        List<com.dreikraft.ai.embedding.postgres.model.DiscussionDocument> input = discussions.stream()
                .map(this::toDiscussionDocument)
                .toList();

        Map<Long, DiscussionClassificationService.DiscussionClassification> classified = discussionClassificationService.classify(
                new DiscussionClassificationService.DiscussionClassificationInput(
                        article.getTitle(),
                        article.getContent(),
                        input
                )
        );

        OffsetDateTime now = OffsetDateTime.now();
        for (DiscussionEntity discussion : discussions) {
            DiscussionClassificationService.DiscussionClassification result = classified.get(discussion.getId());
            if (result != null) {
                discussion.setSentiment(result.sentiment());
                discussion.setResponseDepth(result.responseDepth());
            }
            discussion.setClassificationStatus(ClassificationStatus.SUCCEEDED);
            discussion.setClassificationSource("worker:llm");
            discussion.setClassifiedAt(now);
        }
        discussionRepository.saveAll(discussions);
        log.info("Completed classification for article {} with {} discussion items", documentId, discussions.size());
    }

    private void embedArticle(long articleId) {
        log.info("Starting embedding upsert for article {}", articleId);
        ArticleEntity article = articleRepository.findArticleById(articleId)
                .orElseThrow(() -> new PermanentJobFailureException("Article not found: " + articleId));

        String materialized = summarizeIfNeeded(article.getTitle(), article.getContent());
        List<EmbeddingTransformationService.EmbeddingVariant> variants = embeddingTransformationService
                .transformForArticle(article.getTitle(), materialized);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sampleType", DocumentType.ARTICLE.value());
        vectorStoreService.upsertVariants(article.getId(), DocumentType.ARTICLE.value(), article.getTitle(), variants, metadata);

        article.setEmbeddingContentHash(hashContent(materialized));
        article.setEmbeddingStatus(EmbeddingStatus.SUCCEEDED);
        article.setEmbeddingSource("worker");
        article.setEmbeddingModel(embeddingModel);
        article.setEmbeddedAt(OffsetDateTime.now());
        articleRepository.save(article);
        log.info("Completed embedding upsert for article {} with {} variants", articleId, variants.size());
    }

    private void embedDiscussion(long discussionId) {
        log.info("Starting embedding upsert for discussion {}", discussionId);
        DiscussionEntity discussion = discussionRepository.findDiscussionById(discussionId)
                .orElseThrow(() -> new PermanentJobFailureException("Discussion not found: " + discussionId));

        String articleTitle = resolveArticleTitle(discussion);
        String materialized = summarizeIfNeeded(discussion.getTitle(), discussion.getContent());
        List<EmbeddingTransformationService.EmbeddingVariant> variants = embeddingTransformationService
                .transformForDiscussion(articleTitle, discussion.getTitle(), materialized);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sampleType", DocumentType.DISCUSSION.value());
        metadata.put("relatedArticleDocumentId", resolveArticleId(discussion));
        metadata.put("respondsToDocumentId", discussion.getParentDocumentId());
        metadata.put("discussionSection", discussion.getDiscussionSection());

        vectorStoreService.upsertVariants(discussion.getId(), DocumentType.DISCUSSION.value(), discussion.getTitle(), variants, metadata);

        discussion.setEmbeddingContentHash(hashContent(materialized));
        discussion.setEmbeddingStatus(EmbeddingStatus.SUCCEEDED);
        discussion.setEmbeddingSource("worker");
        discussion.setEmbeddingModel(embeddingModel);
        discussion.setEmbeddedAt(OffsetDateTime.now());
        discussionRepository.save(discussion);
        log.info("Completed embedding upsert for discussion {} with {} variants", discussionId, variants.size());
    }

    private List<DiscussionEntity> flattenDiscussionTree(long articleDocumentId) {
        List<DiscussionEntity> roots = discussionRepository.findRootDiscussionsByArticleDocumentIdOrderByIdAsc(articleDocumentId);
        List<DiscussionEntity> flattened = new ArrayList<>();
        for (DiscussionEntity root : roots) {
            appendDiscussion(flattened, root);
        }
        return flattened;
    }

    private void appendDiscussion(List<DiscussionEntity> target, DiscussionEntity entity) {
        target.add(entity);
        for (DiscussionEntity response : entity.getResponses()) {
            appendDiscussion(target, response);
        }
    }

    private com.dreikraft.ai.embedding.postgres.model.DiscussionDocument toDiscussionDocument(DiscussionEntity entity) {
        return new com.dreikraft.ai.embedding.postgres.model.DiscussionDocument(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getUpdatedAt(),
                entity.getParentDocumentId(),
                entity.getDiscussionSection(),
                entity.getSentiment(),
                entity.getResponseDepth()
        );
    }

    private String summarizeIfNeeded(String title, String content) {
        if (content == null) {
            return "";
        }
        if (content.length() < summarizeThresholdChars) {
            return content;
        }
        log.debug("Summarizing content for embedding title={} length={} threshold={}", title, content.length(), summarizeThresholdChars);
        return semanticSummaryService.summarizeDocumentForEmbedding(title, content);
    }

    private void handleFailure(DocumentIndexingJobRecord job, Exception ex) {
        String message = trimError(ex);
        if (isTransient(ex) && job.attempt() < job.maxAttempts()) {
            OffsetDateTime next = OffsetDateTime.now().plus(calculateBackoff(job.attempt()));
            if (jobRepository.markFailedWithRetry(job.id(), next, message)) {
                log.warn("Retrying job {} at {} after transient failure: {}", job.id(), next, message);
                return;
            }
        }

        log.error("Marking job {} as dead-letter after failure: {}", job.id(), message);
        jobRepository.markDeadLetter(job.id(), OffsetDateTime.now(), message);
        log.error("Moved job {} to dead-letter after failure: {}", job.id(), message, ex);
    }

    private Duration calculateBackoff(int attempt) {
        long multiplier = 1L << Math.min(attempt, 10);
        return Duration.ofMillis(baseBackoffMillis * multiplier);
    }

    private boolean isTransient(Throwable throwable) {
        if (throwable instanceof PermanentJobFailureException) {
            return false;
        }
        if (throwable instanceof DataAccessException || throwable instanceof TimeoutException) {
            return true;
        }
        return throwable.getCause() != null && isTransient(throwable.getCause());
    }

    private String trimError(Throwable throwable) {
        String text = throwable.getMessage();
        if (text == null || text.isBlank()) {
            text = throwable.getClass().getSimpleName();
        }
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }

    private long resolveArticleId(DiscussionEntity discussion) {
        if (discussion.getArticle() != null) {
            return discussion.getArticle().getId();
        }

        DiscussionEntity current = discussion.getParentDiscussion();
        while (current != null) {
            if (current.getArticle() != null) {
                return current.getArticle().getId();
            }
            current = current.getParentDiscussion();
        }
        throw new PermanentJobFailureException("Unable to resolve related article for discussion " + discussion.getId());
    }

    private String resolveArticleTitle(DiscussionEntity discussion) {
        if (discussion.getArticle() != null) {
            return discussion.getArticle().getTitle();
        }

        DiscussionEntity current = discussion.getParentDiscussion();
        while (current != null) {
            if (current.getArticle() != null) {
                return current.getArticle().getTitle();
            }
            current = current.getParentDiscussion();
        }
        return null;
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
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private static class PermanentJobFailureException extends RuntimeException {
        PermanentJobFailureException(String message) {
            super(message);
        }
    }
}
