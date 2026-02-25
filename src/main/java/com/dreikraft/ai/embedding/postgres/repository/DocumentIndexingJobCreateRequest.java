package com.dreikraft.ai.embedding.postgres.repository;

import java.time.OffsetDateTime;

public record DocumentIndexingJobCreateRequest(
        String jobType,
        String documentType,
        long documentId,
        OffsetDateTime availableAt,
        int maxAttempts
) {
}
