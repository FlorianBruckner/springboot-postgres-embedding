package com.dreikraft.ai.embedding.postgres.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record DocumentCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 10_000_000) String content,
        Map<String, Object> properties
) {
    public Map<String, Object> propertiesOrEmpty() {
        return properties == null ? Map.of() : properties;
    }
}
