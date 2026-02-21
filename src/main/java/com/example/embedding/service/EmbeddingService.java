package com.example.embedding.service;

import com.example.embedding.config.EmbeddingApiProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {
    private final RestClient restClient;
    private final EmbeddingApiProperties properties;

    public EmbeddingService(RestClient.Builder builder, EmbeddingApiProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    public List<Float> embed(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input cannot be blank");
        }

        EmbeddingResponse response = restClient.post()
                .uri(properties.path())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> addAuthHeader(headers, properties.apiKey()))
                .body(Map.of("model", properties.model(), "input", input))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("Embedding API returned no vectors");
        }
        return response.data().getFirst().embedding();
    }

    private static void addAuthHeader(HttpHeaders headers, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }
    }

    public record EmbeddingResponse(List<EmbeddingData> data) {
    }

    public record EmbeddingData(List<Float> embedding) {
    }
}
