package com.example.embedding.repository;

import com.example.embedding.model.Document;
import com.example.embedding.model.DocumentCreateRequest;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository {
    long create(DocumentCreateRequest request, List<Float> embedding);

    void update(long id, String content, List<Float> embedding);

    Optional<Document> findById(long id);

    List<Document> keywordSearch(String term, int limit);

    List<Document> semanticSearch(List<Float> queryEmbedding, int limit);

    long count();

    void createSeedDocument(String title, String content, List<Float> embedding);
}
