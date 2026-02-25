package com.dreikraft.ai.embedding.postgres.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface DocumentIndexingJobRepository {

    long enqueue(String jobType, String documentType, long documentId, OffsetDateTime availableAt, int maxAttempts);

    List<Long> enqueueBatch(Collection<DocumentIndexingJobCreateRequest> jobs);

    List<DocumentIndexingJobRecord> pollDue(DocumentIndexingJobStatus status, OffsetDateTime dueAt, int limit);

    boolean claimPending(long jobId, OffsetDateTime claimedAt);

    boolean markSucceeded(long jobId, OffsetDateTime completedAt);

    boolean markFailedWithRetry(long jobId, OffsetDateTime nextAttemptAt, String errorMessage);

    boolean markDeadLetter(long jobId, OffsetDateTime completedAt, String errorMessage);
}
