package com.dreikraft.ai.embedding.postgres.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentUpdateRequest(
        @NotBlank @Size(max = 10_000_000) String content
) {
}
