package com.example.embedding.service;

import com.example.embedding.config.EmbeddingApiProperties;
import com.example.embedding.repository.DocumentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "sample-loader", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SampleDataLoader implements ApplicationRunner {
    private static final int SAMPLE_SIZE = 1000;

    private final DocumentRepository repository;
    private final DeterministicEmbeddingService deterministicEmbeddingService;
    private final EmbeddingApiProperties properties;
    private final WikipediaClient wikipediaClient;

    public SampleDataLoader(DocumentRepository repository,
                            DeterministicEmbeddingService deterministicEmbeddingService,
                            EmbeddingApiProperties properties,
                            WikipediaClient wikipediaClient) {
        this.repository = repository;
        this.deterministicEmbeddingService = deterministicEmbeddingService;
        this.properties = properties;
        this.wikipediaClient = wikipediaClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }

        List<WikipediaClient.WikipediaArticle> articles = wikipediaClient.fetchRandomGermanArticles(SAMPLE_SIZE);
        for (WikipediaClient.WikipediaArticle article : articles) {
            List<Float> embedding = deterministicEmbeddingService.embed(article.extract(), properties.dimensions());
            repository.createSeedDocument(article.title(), article.extract(), embedding);
        }
    }
}
