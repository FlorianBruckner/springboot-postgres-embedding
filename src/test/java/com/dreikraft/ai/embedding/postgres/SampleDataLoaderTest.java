package com.dreikraft.ai.embedding.postgres;

import com.dreikraft.ai.embedding.postgres.model.ArticleCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.DiscussionCreateRequest;
import com.dreikraft.ai.embedding.postgres.service.ArticleService;
import com.dreikraft.ai.embedding.postgres.service.DiscussionService;
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
        ArticleService articleService = mock(ArticleService.class);
        DiscussionService discussionService = mock(DiscussionService.class);
        WikipediaClient wikipediaClient = mock(WikipediaClient.class);
        when(articleService.count()).thenReturn(0L);
        when(discussionService.count()).thenReturn(0L);

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

        when(articleService.create(any(ArticleCreateRequest.class))).thenReturn(11L);
        when(discussionService.create(any(DiscussionCreateRequest.class))).thenReturn(12L);

        SampleDataLoader loader = new SampleDataLoader(
                articleService,
                discussionService,
                wikipediaClient,
                new ObjectMapper(),
                sampleDir.toString(),
                "articles.json"
        );

        loader.run(new DefaultApplicationArguments(new String[0]));

        verify(wikipediaClient, never()).fetchRandomGermanArticles(any(Integer.class));
        verify(wikipediaClient, never()).fetchDiscussionItems(any(String.class));
        verify(articleService, times(1)).create(any(ArticleCreateRequest.class));
        verify(discussionService, times(1)).create(any(DiscussionCreateRequest.class));
    }

    @Test
    void shouldFetchArticleAndDiscussionsAndPersistWhenNoSampleDataIsCached() throws Exception {
        ArticleService articleService = mock(ArticleService.class);
        DiscussionService discussionService = mock(DiscussionService.class);
        WikipediaClient wikipediaClient = mock(WikipediaClient.class);
        when(articleService.count()).thenReturn(0L);
        when(discussionService.count()).thenReturn(0L);

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
        when(articleService.create(any(ArticleCreateRequest.class))).thenReturn(21L);
        when(discussionService.create(any(DiscussionCreateRequest.class))).thenReturn(22L, 23L);

        SampleDataLoader loader = new SampleDataLoader(
                articleService,
                discussionService,
                wikipediaClient,
                new ObjectMapper(),
                sampleDir.toString(),
                "articles.json"
        );

        loader.run(new DefaultApplicationArguments(new String[0]));

        verify(wikipediaClient, times(1)).fetchRandomGermanArticles(any(Integer.class));
        verify(wikipediaClient, times(1)).fetchDiscussionItems(eq("Fetched title"));
        verify(articleService, times(1)).create(any(ArticleCreateRequest.class));
        verify(discussionService, times(2)).create(any(DiscussionCreateRequest.class));

        Path cacheFile = sampleDir.resolve("articles.json");
        assertTrue(Files.exists(cacheFile));

        SampleDataLoader.CachedSampleData persistedData = new ObjectMapper()
                .readValue(cacheFile.toFile(), SampleDataLoader.CachedSampleData.class);
        assertEquals(1, persistedData.articleBundles().size());
        assertEquals(2, persistedData.articleBundles().getFirst().discussionItems().size());
    }
}
