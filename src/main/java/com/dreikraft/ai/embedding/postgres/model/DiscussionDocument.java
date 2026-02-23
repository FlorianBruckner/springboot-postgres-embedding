package com.dreikraft.ai.embedding.postgres.model;

import java.time.OffsetDateTime;

public record DiscussionDocument(
        Long id,
        String title,
        String content,
        OffsetDateTime updatedAt,
        Long parentDocumentId,
        String section
) {
}
