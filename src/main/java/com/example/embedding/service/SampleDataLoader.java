package com.example.embedding.service;

import com.example.embedding.config.EmbeddingApiProperties;
import com.example.embedding.repository.DocumentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Component
public class SampleDataLoader implements ApplicationRunner {
    private final DocumentRepository repository;
    private final DeterministicEmbeddingService deterministicEmbeddingService;
    private final EmbeddingApiProperties properties;

    public SampleDataLoader(DocumentRepository repository,
                            DeterministicEmbeddingService deterministicEmbeddingService,
                            EmbeddingApiProperties properties) {
        this.repository = repository;
        this.deterministicEmbeddingService = deterministicEmbeddingService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }

        IntStream.rangeClosed(1, 1000).forEach(i -> {
            String title = "Java Knowledge Note #" + i;
            String content = "Java topic " + i + ": Java uses a virtual machine model, strong static typing, and a rich standard library. " +
                    "This sample explains collections, streams, concurrency, records, and memory management with practical examples. " +
                    "Item " + i + " also references Spring Boot practices and JDBC transaction boundaries.";
            List<Float> embedding = deterministicEmbeddingService.embed(content, properties.dimensions());
            repository.createSeedDocument(title, content, embedding);
        });
    }
}
