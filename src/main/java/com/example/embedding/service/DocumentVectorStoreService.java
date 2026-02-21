package com.example.embedding.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DocumentVectorStoreService {
    private final VectorStore vectorStore;

    public DocumentVectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void upsert(long id, String title, String content) {
        Document document = new Document(Long.toString(id), content, Map.of("title", title));
        vectorStore.add(List.of(document));
    }

    public List<Long> searchIds(String query, int limit) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(limit).build())
                .stream()
                .map(doc -> Long.valueOf(doc.getId()))
                .toList();
    }

    public void delete(long id) {
        vectorStore.delete(List.of(Long.toString(id)));
    }
}
