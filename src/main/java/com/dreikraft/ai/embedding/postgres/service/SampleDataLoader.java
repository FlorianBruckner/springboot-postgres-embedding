package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "sample-loader", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SampleDataLoader implements ApplicationRunner {
    private static final int SAMPLE_SIZE = 100;

    private final DocumentService documentService;
    private final WikipediaClient wikipediaClient;

    public SampleDataLoader(DocumentService documentService, WikipediaClient wikipediaClient) {
        this.documentService = documentService;
        this.wikipediaClient = wikipediaClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (documentService.count() > 0) {
            return;
        }

        List<WikipediaClient.WikipediaArticle> articles = wikipediaClient.fetchRandomGermanArticles(SAMPLE_SIZE);
        for (WikipediaClient.WikipediaArticle article : articles) {
            documentService.create(new DocumentCreateRequest(article.title(), article.extract(), Map.of()));
        }
    }
}
