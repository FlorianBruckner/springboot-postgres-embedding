package com.dreikraft.ai.embedding.postgres;

import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import com.dreikraft.ai.embedding.postgres.service.DocumentService;
import com.dreikraft.ai.embedding.postgres.service.SampleDataLoader;
import com.dreikraft.ai.embedding.postgres.service.WikipediaClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SampleDataLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadFromPersistedSampleDataBeforeFetchingWikipedia() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        WikipediaClient wikipediaClient = mock(WikipediaClient.class);
        when(documentService.count()).thenReturn(0L);

        Path sampleDir = tempDir.resolve("sampledata");
        Files.createDirectories(sampleDir);
        Path cacheFile = sampleDir.resolve("articles.json");

        SampleDataLoader.CachedSampleData persistedData = new SampleDataLoader.CachedSampleData(List.of(
                new SampleDataLoader.CachedArticleBundle(
                        new WikipediaClient.WikipediaArticle("Persisted title", "Persisted extract"),
                        List.of(new WikipediaClient.WikipediaDiscussionItem("d1", null, "Abschnitt", "Kommentar"))
                )
        ));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(cacheFile.toFile(), persistedData);

        when(documentService.create(any(DocumentCreateRequest.class))).thenReturn(11L, 12L);

        SampleDataLoader loader = new SampleDataLoader(
                documentService,
                wikipediaClient,
                new ObjectMapper(),
                sampleDir.toString(),
                "articles.json"
        );

        loader.run(new DefaultApplicationArguments(new String[0]));

        verify(wikipediaClient, never()).fetchRandomGermanArticles(any(Integer.class));
        verify(wikipediaClient, never()).fetchDiscussionItems(any(String.class));
        verify(documentService, times(2)).create(any(DocumentCreateRequest.class));
    }

    @Test
    void shouldFetchArticleAndDiscussionsAndPersistWhenNoSampleDataIsCached() throws Exception {
        DocumentService documentService = mock(DocumentService.class);
        WikipediaClient wikipediaClient = mock(WikipediaClient.class);
        when(documentService.count()).thenReturn(0L);

        Path sampleDir = tempDir.resolve("sampledata");
        List<WikipediaClient.WikipediaArticle> freshArticles = List.of(
                new WikipediaClient.WikipediaArticle("Fetched title", "Fetched extract")
        );
        List<WikipediaClient.WikipediaDiscussionItem> discussions = List.of(
                new WikipediaClient.WikipediaDiscussionItem("d1", null, "Thema", "Erster Kommentar"),
                new WikipediaClient.WikipediaDiscussionItem("d2", "d1", "Thema", "Antwort")
        );

        when(wikipediaClient.fetchRandomGermanArticles(any(Integer.class))).thenReturn(freshArticles);
        when(wikipediaClient.fetchDiscussionItems(eq("Fetched title"))).thenReturn(discussions);
        when(documentService.create(any(DocumentCreateRequest.class))).thenReturn(21L, 22L, 23L);

        SampleDataLoader loader = new SampleDataLoader(
                documentService,
                wikipediaClient,
                new ObjectMapper(),
                sampleDir.toString(),
                "articles.json"
        );

        loader.run(new DefaultApplicationArguments(new String[0]));

        verify(wikipediaClient, times(1)).fetchRandomGermanArticles(any(Integer.class));
        verify(wikipediaClient, times(1)).fetchDiscussionItems(eq("Fetched title"));
        verify(documentService, times(3)).create(any(DocumentCreateRequest.class));

        Path cacheFile = sampleDir.resolve("articles.json");
        assertTrue(Files.exists(cacheFile));

        SampleDataLoader.CachedSampleData persistedData = new ObjectMapper()
                .readValue(cacheFile.toFile(), SampleDataLoader.CachedSampleData.class);
        assertEquals(1, persistedData.articleBundles().size());
        assertEquals(2, persistedData.articleBundles().getFirst().discussionItems().size());
    }
}
