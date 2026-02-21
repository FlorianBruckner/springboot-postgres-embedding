package com.example.embedding.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 10_000_000) String content
) {
}
