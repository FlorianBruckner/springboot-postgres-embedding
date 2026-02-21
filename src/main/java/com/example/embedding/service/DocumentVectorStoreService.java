package com.example.embedding.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentVectorStoreService {
    private final VectorStore vectorStore;

    public DocumentVectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void upsert(long id, String title, String content, Map<String, Object> additionalProperties) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", title);
        if (additionalProperties != null) {
            metadata.putAll(additionalProperties);
        }

        Document document = new Document(Long.toString(id), content, metadata);
        vectorStore.add(List.of(document));
    }

    public List<Long> searchIds(String query, int limit, String filterExpression) {
        SearchRequest.Builder builder = SearchRequest.builder().query(query).topK(limit);
        if (filterExpression != null && !filterExpression.isBlank()) {
            builder.filterExpression(filterExpression);
        }

        return vectorStore.similaritySearch(builder.build())
                .stream()
                .map(doc -> Long.valueOf(doc.getId()))
                .toList();
    }

    public void delete(long id) {
        vectorStore.delete(List.of(Long.toString(id)));
    }
}
