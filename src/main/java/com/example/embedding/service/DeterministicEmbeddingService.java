package com.example.embedding.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class DeterministicEmbeddingService {

    public List<Float> embed(String input, int dimensions) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        List<Float> vector = new ArrayList<>(dimensions);
        for (int i = 0; i < dimensions; i++) {
            int b = bytes[i % bytes.length] & 0xFF;
            float normalized = ((b + i) % 255) / 255.0f;
            vector.add(normalized);
        }
        return vector;
    }
}
