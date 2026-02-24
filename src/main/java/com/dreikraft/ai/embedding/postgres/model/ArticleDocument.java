package com.dreikraft.ai.embedding.postgres.model;

import java.time.OffsetDateTime;

public record ArticleDocument(
        Long id,
        String title,
        String content,
        OffsetDateTime updatedAt,
        String contentHash,
        String embeddingModel,
        String embeddingVersion,
        OffsetDateTime embeddedAt
) {
    public ArticleDocument(Long id,
                           String title,
                           String content,
                           OffsetDateTime updatedAt) {
        this(id, title, content, updatedAt, null, null, null, null);
    }
}
