package com.example.embedding.model;

import java.time.OffsetDateTime;

public record Document(
        Long id,
        String title,
        String content,
        OffsetDateTime updatedAt
) {
}
