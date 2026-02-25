package com.dreikraft.ai.embedding.postgres.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    public void upsertVariants(long id,
                               String entityType,
                               String title,
                               List<EmbeddingTransformationService.EmbeddingVariant> variants,
                               Map<String, Object> additionalProperties) {
        if (variants == null || variants.isEmpty()) {
            return;
        }

        List<Document> vectorDocuments = new ArrayList<>();
        for (int i = 0; i < variants.size(); i++) {
            EmbeddingTransformationService.EmbeddingVariant variant = variants.get(i);
            Map<String, Object> metadata = buildMetadata(id, entityType, title, additionalProperties, variant.label());
            String vectorDocumentId = entityType + ":" + id + ":" + i;
            vectorDocuments.add(new Document(vectorDocumentId, variant.content(), metadata));
        }
        vectorStore.add(vectorDocuments);
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

    private Map<String, Object> buildMetadata(long id,
                                              String entityType,
                                              String title,
                                              Map<String, Object> additionalProperties,
                                              String variantLabel) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", title);

        if (additionalProperties != null) {
            for (String key : ALLOWED_METADATA_KEYS) {
                Object value = additionalProperties.get(key);
                if (value != null) {
                    metadata.put(key, value);
                }
            }
        }

        metadata.put("embeddingVariant", variantLabel == null ? "variant" : variantLabel);
        metadata.put(ENTITY_ID_KEY, id);
        metadata.put(ENTITY_TYPE_KEY, entityType);
        return metadata;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
