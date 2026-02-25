package com.dreikraft.ai.embedding.postgres.repository;

import java.time.OffsetDateTime;

public record DocumentIndexingJobRecord(
        Long id,
        String jobType,
        String documentType,
        Long documentId,
        DocumentIndexingJobStatus status,
        int attempt,
        int maxAttempts,
        OffsetDateTime availableAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
