package com.example.embedding.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input cannot be blank");
        }

        return embeddingModel.embed(input);
    }
}
