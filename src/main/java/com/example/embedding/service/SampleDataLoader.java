package com.example.embedding.service;

import com.example.embedding.repository.DocumentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "sample-loader", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SampleDataLoader implements ApplicationRunner {
    private static final int SAMPLE_SIZE = 100;

    private final DocumentRepository repository;
    private final EmbeddingService deterministicEmbeddingService;
    private final WikipediaClient wikipediaClient;

    public SampleDataLoader(DocumentRepository repository,
                            EmbeddingService deterministicEmbeddingService,
                            WikipediaClient wikipediaClient) {
        this.repository = repository;
        this.deterministicEmbeddingService = deterministicEmbeddingService;
        this.wikipediaClient = wikipediaClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }

        List<WikipediaClient.WikipediaArticle> articles = wikipediaClient.fetchRandomGermanArticles(SAMPLE_SIZE);
        for (WikipediaClient.WikipediaArticle article : articles) {
            float[] embedding = deterministicEmbeddingService.embed(article.extract());
            repository.createSeedDocument(article.title(), article.extract(), embedding);
        }
    }
}
