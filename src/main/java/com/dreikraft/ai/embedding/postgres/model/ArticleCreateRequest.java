package com.dreikraft.ai.embedding.postgres.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArticleCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 10_000_000) String content
) {
}
