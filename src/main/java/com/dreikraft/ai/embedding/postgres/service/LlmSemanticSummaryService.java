package com.dreikraft.ai.embedding.postgres.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LlmSemanticSummaryService implements SemanticSummaryService {
    private static final Logger log = LoggerFactory.getLogger(LlmSemanticSummaryService.class);

    private final ChatClient chatClient;

    public LlmSemanticSummaryService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String summarizeDocumentForEmbedding(String title, String content) {
        try {
            String response = chatClient.prompt()
                    .system("""
                            Create a short semantic-search summary in clean, plain language.
                            Keep key entities and intent.
                            Avoid jargon unless essential.
                            Output only the summary text.
                            """)
                    .user(user -> user.text("""
                            Title:
                            {title}

                            Content:
                            {content}
                            """)
                            .param("title", safeValue(title))
                            .param("content", safeValue(content)))
                    .call()
                    .content();
            return normalizeOrFallback(response, content);
        } catch (Exception ex) {
            log.warn("Failed to summarize article for embedding. Falling back to original content.", ex);
            return content;
        }
    }

    @Override
    public String summarizeQueryForSemanticSearch(String query) {
        try {
            String response = chatClient.prompt()
                    .system("""
                            Rewrite this search query into a short, clean, plain-language semantic-search query.
                            Preserve user intent and important terms.
                            Output only the rewritten query.
                            """)
                    .user(user -> user.text("""
                            Query:
                            {query}
                            """)
                            .param("query", safeValue(query)))
                    .call()
                    .content();
            return normalizeOrFallback(response, query);
        } catch (Exception ex) {
            log.warn("Failed to summarize semantic query. Falling back to original query.", ex);
            return query;
        }
    }

    private String normalizeOrFallback(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
