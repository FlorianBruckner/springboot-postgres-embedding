package com.example.embedding.model;

import jakarta.validation.constraints.NotBlank;

public record RagQueryRequest(@NotBlank String query) {
}
