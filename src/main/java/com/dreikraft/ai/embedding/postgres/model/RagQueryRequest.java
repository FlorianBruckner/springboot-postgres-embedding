package com.dreikraft.ai.embedding.postgres.model;

import jakarta.validation.constraints.NotBlank;

public record RagQueryRequest(@NotBlank String query) {
}
