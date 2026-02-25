package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.semantic-search.rerank.enabled", havingValue = "true")
public class LlmSemanticSearchRerankingService implements SemanticSearchRerankingService {
    private static final Logger log = LoggerFactory.getLogger(LlmSemanticSearchRerankingService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LlmSemanticSearchRerankingService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Long> rerank(String query, List<Long> currentRanking, List<ArticleDocument> candidates) {
        if (currentRanking == null || currentRanking.isEmpty()) {
            return List.of();
        }

        Map<Long, ArticleDocument> byId = candidates.stream()
                .collect(Collectors.toMap(ArticleDocument::id, article -> article, (a, b) -> a));

        List<Map<String, Object>> compactCandidates = new ArrayList<>();
        for (Long id : currentRanking) {
            ArticleDocument article = byId.get(id);
            if (article != null) {
                compactCandidates.add(Map.of(
                        "id", article.id(),
                        "title", safe(article.title()),
                        "content", truncate(safe(article.content()), 1000)
                ));
            }
        }

        if (compactCandidates.isEmpty()) {
            return currentRanking;
        }

        try {
            String candidatesJson = objectMapper.writeValueAsString(compactCandidates);
            String response = chatClient.prompt()
                    .system("""
                            You are a search reranking model.
                            Re-rank candidates by relevance to the user query.
                            Return ONLY a JSON array of candidate IDs in best-to-worst order.
                            Include each ID at most once.
                            Only use IDs from the provided list.
                            """)
                    .user(user -> user.text("""
                            Query:
                            {query}

                            Candidates:
                            {candidates}
                            """)
                            .param("query", safe(query))
                            .param("candidates", candidatesJson))
                    .call()
                    .content();

            List<Long> llmOrder = objectMapper.readValue(cleanJson(response), new TypeReference<>() {});
            LinkedHashSet<Long> merged = new LinkedHashSet<>();
            for (Long id : llmOrder) {
                if (id != null && byId.containsKey(id)) {
                    merged.add(id);
                }
            }
            merged.addAll(currentRanking);
            return List.copyOf(merged);
        } catch (Exception ex) {
            log.warn("Failed to rerank semantic-search candidates. Falling back to baseline ranking.", ex);
            return currentRanking;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String cleanJson(String value) {
        if (value == null) {
            return "[]";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "[]";
    }
}
