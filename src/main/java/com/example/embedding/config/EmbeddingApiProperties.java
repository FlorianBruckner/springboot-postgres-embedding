package com.example.embedding.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "embedding.api")
public record EmbeddingApiProperties(
        @NotBlank String baseUrl,
        @NotBlank String path,
        @NotBlank String model,
        String apiKey,
        int dimensions
) {
}
