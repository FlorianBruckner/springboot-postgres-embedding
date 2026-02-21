package com.example.embedding.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.http.HttpHeaders;

@Component
public class WikipediaClient {
    private static final String DE_WIKIPEDIA_API = "https://de.wikipedia.org/w/api.php";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WikipediaClient(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(DE_WIKIPEDIA_API).defaultHeader(HttpHeaders.USER_AGENT, "MyApp/1.0 (bfl@florianbruckner.com)").build();
        this.objectMapper = objectMapper;
    }

    public List<WikipediaArticle> fetchRandomGermanArticles(int count) {
        List<WikipediaArticle> results = new ArrayList<>(count);
        int maxAttempts = Math.max(60, count / 10);

        for (int attempt = 0; attempt < maxAttempts && results.size() < count; attempt++) {
            int remaining = count - results.size();
            int batchSize = Math.min(20, remaining);
            results.addAll(fetchBatch(batchSize));
        }

        if (results.size() > count) {
            return results.subList(0, count);
        }
        return results;
    }

    private List<WikipediaArticle> fetchBatch(int batchSize) {
        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("action", "query")
                        .queryParam("format", "json")
                        .queryParam("generator", "random")
                        .queryParam("grnnamespace", 0)
                        .queryParam("grnlimit", batchSize)
                        .queryParam("prop", "extracts")
                        .queryParam("explaintext", 1)
                        .queryParam("exintro", 0)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(body);
            return parseArticles(root);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<WikipediaArticle> parseArticles(JsonNode root) {
        JsonNode pages = root.path("query").path("pages");
        if (!pages.isObject()) {
            return List.of();
        }

        List<WikipediaArticle> articles = new ArrayList<>();
        Iterator<JsonNode> iterator = pages.elements();
        while (iterator.hasNext()) {
            JsonNode page = iterator.next();
            String title = page.path("title").asText();
            String extract = page.path("extract").asText();
            if (!title.isBlank() && !extract.isBlank()) {
                articles.add(new WikipediaArticle(title, extract));
            }
        }
        return articles;
    }

    public record WikipediaArticle(String title, String extract) {
    }
}
