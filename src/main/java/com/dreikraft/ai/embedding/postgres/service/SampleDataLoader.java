package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "sample-loader", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class SampleDataLoader implements ApplicationRunner {
    private static final int SAMPLE_SIZE = 1000;

    private final DocumentService documentService;
    private final WikipediaClient wikipediaClient;
    private final ObjectMapper objectMapper;
    private final Path cacheFile;

    public SampleDataLoader(
            DocumentService documentService,
            WikipediaClient wikipediaClient,
            ObjectMapper objectMapper,
            @Value("${sample-loader.directory:sampledata}") String sampleDataDirectory,
            @Value("${sample-loader.file-name:articles.json}") String sampleDataFileName
    ) {
        this.documentService = documentService;
        this.wikipediaClient = wikipediaClient;
        this.objectMapper = objectMapper;
        this.cacheFile = Path.of(sampleDataDirectory, sampleDataFileName);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (documentService.count() > 0) {
            log.info("Skipping sample data loading because documents already exist.");
            return;
        }

        log.info("Starting sample data loading process.");
        CachedSampleData cachedSampleData = loadPersistedData();
        if (cachedSampleData.articleBundles().isEmpty()) {
            log.info("No cached sample data found. Fetching fresh sample data from Wikipedia.");
            cachedSampleData = fetchAndBuildSampleData();
            persistSampleData(cachedSampleData);
        }

        persistDocuments(cachedSampleData);
        log.info("Sample data loading completed. Persisted {} article bundles.", cachedSampleData.articleBundles().size());
    }

    private void persistDocuments(CachedSampleData cachedSampleData) {
        int totalBundles = cachedSampleData.articleBundles().size();
        int processedBundles = 0;

        for (CachedArticleBundle articleBundle : cachedSampleData.articleBundles()) {
            processedBundles++;
            log.info("Persisting article bundle {}/{}: {}", processedBundles, totalBundles, articleBundle.article().title());
            long articleDocumentId = documentService.create(new DocumentCreateRequest(
                    articleBundle.article().title(),
                    articleBundle.article().extract(),
                    Map.of("sampleType", "article", "articleTitle", articleBundle.article().title())
            ));

            Map<String, Long> discussionIdToDocumentId = new HashMap<>();
            log.info("Persisting {} discussion items for article '{}'", articleBundle.discussionItems().size(), articleBundle.article().title());
            for (WikipediaClient.WikipediaDiscussionItem item : articleBundle.discussionItems()) {
                Map<String, Object> properties = new HashMap<>();
                properties.put("sampleType", "discussion");
                properties.put("articleTitle", articleBundle.article().title());
                properties.put("relatedArticleDocumentId", articleDocumentId);
                properties.put("discussionItemId", item.itemId());
                properties.put("discussionSection", item.section());

                if (item.parentItemId() != null) {
                    properties.put("parentDiscussionItemId", item.parentItemId());
                    Long parentDocumentId = discussionIdToDocumentId.get(item.parentItemId());
                    if (parentDocumentId != null) {
                        properties.put("respondsToDocumentId", parentDocumentId);
                    }
                }

                long discussionDocumentId = documentService.create(new DocumentCreateRequest(
                        "%s - Diskussion %s".formatted(articleBundle.article().title(), item.itemId()),
                        item.text(),
                        properties
                ));
                discussionIdToDocumentId.put(item.itemId(), discussionDocumentId);
            }

            documentService.classifyUnclassifiedDiscussionsAsync(articleDocumentId);
        }
    }

    private CachedSampleData fetchAndBuildSampleData() {
        log.info("Fetching {} random German Wikipedia articles.", SAMPLE_SIZE);
        List<WikipediaClient.WikipediaArticle> articles = wikipediaClient.fetchRandomGermanArticles(SAMPLE_SIZE);
        List<CachedArticleBundle> bundles = new ArrayList<>();

        int articleCount = articles.size();
        int processedArticles = 0;
        for (WikipediaClient.WikipediaArticle article : articles) {
            processedArticles++;
            log.info("Fetching discussion items for article {}/{}: {}", processedArticles, articleCount, article.title());
            List<WikipediaClient.WikipediaDiscussionItem> discussionItems = wikipediaClient.fetchDiscussionItems(article.title());
            bundles.add(new CachedArticleBundle(article, discussionItems));
        }

        log.info("Finished fetching sample data. Built {} article bundles.", bundles.size());
        return new CachedSampleData(bundles);
    }

    private CachedSampleData loadPersistedData() {
        if (!Files.exists(cacheFile)) {
            return new CachedSampleData(List.of());
        }

        try {
            CachedSampleData data = objectMapper.readValue(cacheFile.toFile(), CachedSampleData.class);
            if (!data.articleBundles().isEmpty()) {
                log.info("Loaded {} cached sample article bundles from {}", data.articleBundles().size(), cacheFile.toAbsolutePath());
            }
            return data;
        } catch (IOException ex) {
            log.warn("Failed to read cached sample data from {}. New sample data will be fetched.", cacheFile.toAbsolutePath(), ex);
            return new CachedSampleData(List.of());
        }
    }

    private void persistSampleData(CachedSampleData data) {
        if (data.articleBundles().isEmpty()) {
            return;
        }

        try {
            Files.createDirectories(cacheFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(cacheFile.toFile(), data);
            log.info("Persisted {} sample article bundles to {}", data.articleBundles().size(), cacheFile.toAbsolutePath());
        } catch (IOException ex) {
            log.warn("Failed to persist sample data to {}", cacheFile.toAbsolutePath(), ex);
        }
    }

    public record CachedSampleData(List<CachedArticleBundle> articleBundles) {
    }

    public record CachedArticleBundle(
            WikipediaClient.WikipediaArticle article,
            List<WikipediaClient.WikipediaDiscussionItem> discussionItems
    ) {
    }
}
