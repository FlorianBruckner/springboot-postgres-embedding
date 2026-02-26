package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        Map<Long, DiscussionDocument> byId = discussions.stream()
                .filter(discussion -> discussion.id() != null)
                .collect(LinkedHashMap::new, (map, discussion) -> map.put(discussion.id(), discussion), LinkedHashMap::putAll);

        List<DiscussionDocument> roots = discussions.stream()
                .filter(discussion -> discussion.parentDocumentId() == null)
                .toList();
        List<DiscussionDocument> responses = collectResponsesRecursively(discussions, roots);

        Map<Long, DiscussionClassification> classifications = new LinkedHashMap<>();
        try {
            if (!roots.isEmpty()) {
                classifications.putAll(classifyRoots(input, roots));
            }
            if (!responses.isEmpty()) {
                classifications.putAll(classifyResponses(responses, byId));
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

    private Map<Long, DiscussionClassification> classifyRoots(DiscussionClassificationInput input,
                                                              List<DiscussionDocument> roots) throws JsonProcessingException {
        String inputJson = objectMapper.writeValueAsString(
                roots.stream()
                        .map(this::toRootPromptItem)
                        .toList()
        );

        String response = chatClient.prompt()
                .system("""
                        You classify root online discussion entries for both sentiment and response depth.

                        Classification labels:
                        - sentiment: one of positive, negative, neutral
                        - responseDepth: one of trivial, substantive, in_depth, off_topic

                        Rules:
                        - All items are root entries with no parent discussion item.
                        - Compare each item to the provided article itself.
                        - For every item, responseDepth must be non-null.

                        Return only compact JSON with shape:
                        {"items":[{"id":123,"sentiment":"neutral","responseDepth":"substantive"}]}
                        """)
                .user(user -> user.text("""
                        Article title:
                        {articleTitle}

                        Article content:
                        {articleContent}

                        Root discussion items:
                        {items}
                        """)
                        .param("articleTitle", safeValue(input.articleTitle()))
                        .param("articleContent", safeValue(input.articleContent()))
                        .param("items", inputJson))
                .call()
                .content();

        return parseClassifications(response);
    }

    private Map<Long, DiscussionClassification> classifyResponses(List<DiscussionDocument> responses,
                                                                  Map<Long, DiscussionDocument> discussionsById) throws JsonProcessingException {
        Map<Long, DiscussionClassification> classifications = new LinkedHashMap<>();
        for (DiscussionDocument responseDiscussion : responses) {
            ResponsePromptItem promptItem = toResponsePromptItem(
                    responseDiscussion,
                    discussionsById.get(responseDiscussion.parentDocumentId())
            );
            if (promptItem == null) {
                continue;
            }
            classifications.putAll(classifySingleResponse(promptItem));
        }
        return classifications;
    }

    private Map<Long, DiscussionClassification> classifySingleResponse(ResponsePromptItem promptItem) throws JsonProcessingException {
        String inputJson = objectMapper.writeValueAsString(List.of(promptItem));

        String response = chatClient.prompt()
                .system("""
                        You classify a single response discussion entry for both sentiment and response depth.

                        Classification labels:
                        - sentiment: one of positive, negative, neutral
                        - responseDepth: one of trivial, substantive, in_depth, off_topic

                        Rules:
                        - Compare the response item only to its direct parent discussion item.
                        - Ignore article-level context.
                        - responseDepth must be non-null.

                        Return only compact JSON with shape:
                        {"items":[{"id":123,"sentiment":"neutral","responseDepth":"substantive"}]}
                        """)
                .user(user -> user.text("""
                        Response item and direct parent context:
                        {items}
                        """)
                        .param("items", inputJson))
                .call()
                .content();

        return parseClassifications(response);
    }

    private Map<Long, DiscussionClassification> parseClassifications(String response) throws JsonProcessingException {
        ClassificationResponse parsed = objectMapper.readValue(stripCodeFences(response), ClassificationResponse.class);
        Map<Long, DiscussionClassification> classifications = new LinkedHashMap<>();
        if (parsed.items() == null) {
            return classifications;
        }
        for (ClassificationItem item : parsed.items()) {
            if (item.id() == null) {
                continue;
            }
            String sentiment = normalizeSentiment(item.sentiment());
            String responseDepth = normalizeResponseDepth(item.responseDepth());
            classifications.put(item.id(), new DiscussionClassification(sentiment, responseDepth));
        }
        return classifications;
    }

    private List<DiscussionDocument> collectResponsesRecursively(List<DiscussionDocument> discussions,
                                                                 List<DiscussionDocument> roots) {
        Map<Long, List<DiscussionDocument>> childrenByParentId = new LinkedHashMap<>();
        for (DiscussionDocument discussion : discussions) {
            if (discussion.parentDocumentId() == null) {
                continue;
            }
            childrenByParentId.computeIfAbsent(discussion.parentDocumentId(), ignored -> new ArrayList<>())
                    .add(discussion);
        }

        List<DiscussionDocument> responses = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (DiscussionDocument root : roots) {
            appendResponsesRecursively(root.id(), childrenByParentId, responses, visited);
        }

        for (DiscussionDocument discussion : discussions) {
            if (discussion.parentDocumentId() == null || !visited.add(discussion.id())) {
                continue;
            }
            responses.add(discussion);
            appendResponsesRecursively(discussion.id(), childrenByParentId, responses, visited);
        }
        return responses;
    }

    private void appendResponsesRecursively(Long parentId,
                                            Map<Long, List<DiscussionDocument>> childrenByParentId,
                                            List<DiscussionDocument> target,
                                            Set<Long> visited) {
        for (DiscussionDocument child : childrenByParentId.getOrDefault(parentId, List.of())) {
            if (!visited.add(child.id())) {
                continue;
            }
            target.add(child);
            appendResponsesRecursively(child.id(), childrenByParentId, target, visited);
        }
    }

    private RootPromptItem toRootPromptItem(DiscussionDocument discussion) {
        return new RootPromptItem(discussion.id(), discussion.content());
    }

    private ResponsePromptItem toResponsePromptItem(DiscussionDocument response, DiscussionDocument parent) {
        if (parent == null) {
            return null;
        }
        return new ResponsePromptItem(response.id(), response.content(), parent.id(), parent.content());
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

    private record RootPromptItem(Long id, String content) {
    }

    private record ResponsePromptItem(Long id, String content, Long parentDocumentId, String parentContent) {
    }

    private record ClassificationResponse(List<ClassificationItem> items) {
    }

    private record ClassificationItem(Long id, String sentiment, String responseDepth) {
    }
}
