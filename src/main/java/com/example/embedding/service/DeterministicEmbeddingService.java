package com.example.embedding.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class DeterministicEmbeddingService {

    public float[] embed(String input, int dimensions) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        float[] vector = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            int b = bytes[i % bytes.length] & 0xFF;
            vector[i] = ((b + i) % 255) / 255.0f;
        }
        return vector;
    }
}
