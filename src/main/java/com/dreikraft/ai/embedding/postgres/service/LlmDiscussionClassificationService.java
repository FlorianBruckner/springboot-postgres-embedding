package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmDiscussionClassificationService implements DiscussionClassificationService {
    private static final Logger log = LoggerFactory.getLogger(LlmDiscussionClassificationService.class);
    private static final String SENTIMENT_NEUTRAL = "neutral";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LlmDiscussionClassificationService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<Long, DiscussionClassification> classify(DiscussionClassificationInput input) {
        List<DiscussionDocument> discussions = input.discussions();
        if (discussions.isEmpty()) {
            return Map.of();
        }

        try {
            String inputJson = objectMapper.writeValueAsString(
                    discussions.stream()
                            .map(this::toPromptItem)
                            .toList()
            );

            String response = chatClient.prompt()
                    .system("""
                            You classify online discussion entries for both sentiment and response depth.

                            Classification labels:
                            - sentiment: one of positive, negative, neutral
                            - responseDepth: one of trivial, substantive, in_depth, off_topic

                            Rules:
                            - For entries with parentDocumentId != null: compare to the parent discussion item.
                            - For entries with parentDocumentId == null: compare to the article itself.
                            - For every item, responseDepth must be non-null.

                            Return only compact JSON with shape:
                            {"items":[{"id":123,"sentiment":"neutral","responseDepth":"substantive"}]}
                            """)
                    .user(user -> user.text("""
                            Article title:
                            {articleTitle}

                            Article content:
                            {articleContent}

                            Discussion items:
                            {items}
                            """)
                            .param("articleTitle", safeValue(input.articleTitle()))
                            .param("articleContent", safeValue(input.articleContent()))
                            .param("items", inputJson))
                    .call()
                    .content();

            ClassificationResponse parsed = objectMapper.readValue(stripCodeFences(response), ClassificationResponse.class);
            Map<Long, DiscussionClassification> classifications = new LinkedHashMap<>();
            for (ClassificationItem item : parsed.items()) {
                if (item.id() == null) {
                    continue;
                }
                String sentiment = normalizeSentiment(item.sentiment());
                String responseDepth = normalizeResponseDepth(item.responseDepth());
                classifications.put(item.id(), new DiscussionClassification(sentiment, responseDepth));
            }

            for (DiscussionDocument discussion : discussions) {
                classifications.computeIfAbsent(discussion.id(), ignored -> fallback());
            }
            return classifications;
        } catch (Exception ex) {
            log.warn("Failed to classify discussion entries with LLM. Falling back to neutral defaults.", ex);
            Map<Long, DiscussionClassification> fallback = new LinkedHashMap<>();
            for (DiscussionDocument discussion : discussions) {
                fallback.put(discussion.id(), fallback());
            }
            return fallback;
        }
    }

    private PromptItem toPromptItem(DiscussionDocument discussion) {
        return new PromptItem(discussion.id(), discussion.parentDocumentId(), discussion.content());
    }

    private DiscussionClassification fallback() {
        return new DiscussionClassification(SENTIMENT_NEUTRAL, "substantive");
    }

    private String normalizeSentiment(String value) {
        if ("positive".equals(value) || "negative".equals(value) || SENTIMENT_NEUTRAL.equals(value)) {
            return value;
        }
        return SENTIMENT_NEUTRAL;
    }

    private String normalizeResponseDepth(String value) {
        if ("trivial".equals(value) || "substantive".equals(value) || "in_depth".equals(value) || "off_topic".equals(value)) {
            return value;
        }
        return "substantive";
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private String stripCodeFences(String response) throws JsonProcessingException {
        if (response == null || response.isBlank()) {
            throw new JsonProcessingException("Empty LLM response") {
            };
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

    private record PromptItem(Long id, Long parentDocumentId, String content) {
    }

    private record ClassificationResponse(List<ClassificationItem> items) {
    }

    private record ClassificationItem(Long id, String sentiment, String responseDepth) {
    }
}
