package com.dreikraft.ai.embedding.postgres.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WikipediaClient {
    private static final String DE_WIKIPEDIA_API = "https://de.wikipedia.org/w/api.php";
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("(?<text>.*?)(?<author>\\[\\[(?:Benutzer|User)(?::|_talk:)[^\\]]+\\]\\].*)?(?<timestamp>\\d{1,2}:\\d{2},\\s\\d{1,2}\\.\\s[^\\d]+\\s\\d{4}\\s\\((?:CET|CEST|UTC)\\))");

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

    public List<WikipediaDiscussionItem> fetchDiscussionItems(String articleTitle) {
        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("action", "query")
                        .queryParam("format", "json")
                        .queryParam("formatversion", "2")
                        .queryParam("prop", "revisions")
                        .queryParam("rvprop", "content")
                        .queryParam("rvslots", "main")
                        .queryParam("titles", "Diskussion:" + articleTitle)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode page = root.path("query").path("pages").path(0);
            if (page.path("missing").asBoolean(false)) {
                return List.of();
            }
            String wikitext = page.path("revisions").path(0).path("slots").path("main").path("content").asText("");
            return parseDiscussionItems(wikitext);
        } catch (Exception e) {
            return List.of();
        }
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

    public static List<WikipediaDiscussionItem> parseDiscussionItems(String wikiText) {
        if (wikiText == null || wikiText.isBlank()) {
            return List.of();
        }

        List<WikipediaDiscussionItem> items = new ArrayList<>();
        Map<Integer, String> lastItemIdByDepth = new LinkedHashMap<>();
        String currentSection = "Allgemein";

        String[] lines = wikiText.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("==") && trimmed.endsWith("==")) {
                currentSection = trimmed.replace("=", "").trim();
                continue;
            }

            int depth = countLeadingColons(line);
            String content = trimmed.replaceFirst("^:+", "").trim();
            Matcher matcher = SIGNATURE_PATTERN.matcher(content);
            if (!matcher.find()) {
                continue;
            }

            String text = matcher.group("text").trim();
            if (text.isBlank()) {
                continue;
            }

            String id = "discussion-item-" + (items.size() + 1);
            String parentItemId = depth > 0 ? lastItemIdByDepth.get(depth - 1) : null;

            items.add(new WikipediaDiscussionItem(id, parentItemId, currentSection, text));
            lastItemIdByDepth.put(depth, id);
            lastItemIdByDepth.keySet().removeIf(level -> level > depth);
        }
        return items;
    }

    private static int countLeadingColons(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ':') {
            count++;
        }
        return count;
    }

    public record WikipediaArticle(String title, String extract) {
    }

    public record WikipediaDiscussionItem(String itemId, String parentItemId, String section, String text) {
    }
}
