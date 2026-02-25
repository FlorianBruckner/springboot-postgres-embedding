package com.dreikraft.ai.embedding.postgres.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class LlmEmbeddingTransformationService implements EmbeddingTransformationService {
    private static final Logger log = LoggerFactory.getLogger(LlmEmbeddingTransformationService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LlmEmbeddingTransformationService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<EmbeddingVariant> transformForArticle(String title, String content) {
        return buildVariants("article", title, content,
                "Generate short embedding-focused rewrites preserving facts and entities.");
    }

    @Override
    public List<EmbeddingVariant> transformForDiscussion(String articleTitle, String discussionTitle, String content) {
        String enrichedTitle = (articleTitle == null || articleTitle.isBlank())
                ? discussionTitle
                : articleTitle + " | " + discussionTitle;
        return buildVariants("discussion", enrichedTitle, content,
                "Generate concise variants capturing intent, stance, and key claims for semantic retrieval.");
    }

    private List<EmbeddingVariant> buildVariants(String kind, String title, String content, String taskInstruction) {
        List<EmbeddingVariant> variants = new ArrayList<>();
        variants.add(new EmbeddingVariant("original", safe(content)));

        try {
            String response = chatClient.prompt()
                    .system("""
                            You create text variants for vector embeddings.
                            Return compact JSON only with shape:
                            {"variants":[{"label":"summary","content":"..."},{"label":"keywords","content":"..."}]}
                            Provide at most 3 variants.
                            %s
                            """.formatted(taskInstruction))
                    .user(user -> user.text("""
                            Type: {kind}
                            Title: {title}
                            Content: {content}
                            """)
                            .param("kind", kind)
                            .param("title", safe(title))
                            .param("content", safe(content)))
                    .call()
                    .content();

            TransformationResponse parsed = objectMapper.readValue(stripCodeFences(response), TransformationResponse.class);
            if (parsed.variants() != null) {
                for (VariantItem item : parsed.variants()) {
                    if (item == null || item.content() == null || item.content().isBlank()) {
                        continue;
                    }
                    String label = (item.label() == null || item.label().isBlank()) ? "variant" : item.label().trim();
                    variants.add(new EmbeddingVariant(label, item.content().trim()));
                }
            }
        } catch (Exception ex) {
            log.warn("Embedding transformation failed for {}. Falling back to original content only.", kind, ex);
        }

        return deduplicate(variants);
    }

    private List<EmbeddingVariant> deduplicate(List<EmbeddingVariant> variants) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<EmbeddingVariant> unique = new ArrayList<>();
        for (EmbeddingVariant variant : variants) {
            String normalized = variant.content() == null ? "" : variant.content().trim();
            if (normalized.isBlank() || !seen.add(normalized)) {
                continue;
            }
            unique.add(new EmbeddingVariant(variant.label(), normalized));
        }
        return unique;
    }

    private String stripCodeFences(String response) {
        if (response == null) {
            return "{}";
        }
        String trimmed = response.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewLine = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewLine == -1 || lastFence <= firstNewLine) {
            return trimmed;
        }
        return trimmed.substring(firstNewLine + 1, lastFence).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record TransformationResponse(List<VariantItem> variants) {
    }

    private record VariantItem(String label, String content) {
    }
}
