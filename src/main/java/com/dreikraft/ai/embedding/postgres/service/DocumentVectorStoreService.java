package com.dreikraft.ai.embedding.postgres.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class DocumentVectorStoreService {
    private static final List<String> ALLOWED_METADATA_KEYS = List.of(
            "sampleType",
            "relatedArticleDocumentId",
            "respondsToDocumentId",
            "discussionSection"
    );

    private static final String ENTITY_ID_KEY = "entityId";
    private static final String ENTITY_TYPE_KEY = "entityType";

    private final VectorStore vectorStore;
    private final double similarityThreshold;

    public DocumentVectorStoreService(
            VectorStore vectorStore,
            @Value("${app.semantic-search.similarity-threshold:0.75}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.similarityThreshold = similarityThreshold;
    }

    public void upsert(long id, String title, String content, Map<String, Object> additionalProperties) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", title);

        String sampleType = "generic";
        if (additionalProperties != null) {
            for (String key : ALLOWED_METADATA_KEYS) {
                Object value = additionalProperties.get(key);
                if (value != null) {
                    metadata.put(key, value);
                }
            }
            Object rawType = additionalProperties.get("sampleType");
            if (rawType != null && !rawType.toString().isBlank()) {
                sampleType = rawType.toString();
            }
        }

        metadata.put(ENTITY_ID_KEY, id);
        metadata.put(ENTITY_TYPE_KEY, sampleType);

        String vectorDocumentId = sampleType + ":" + id + ":0";
        Document document = new Document(vectorDocumentId, content, metadata);
        vectorStore.add(List.of(document));
    }

    public List<Long> searchIds(String query, int limit, String filterExpression) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(limit)
                .similarityThreshold(similarityThreshold);
        if (filterExpression != null && !filterExpression.isBlank()) {
            builder.filterExpression(filterExpression);
        }

        return vectorStore.similaritySearch(builder.build())
                .stream()
                .map(doc -> doc.getMetadata().get(ENTITY_ID_KEY))
                .filter(java.util.Objects::nonNull)
                .map(this::toLong)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }

    public void delete(long id) {
        vectorStore.delete(List.of("article:" + id + ":0", "discussion:" + id + ":0", "generic:" + id + ":0"));
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
