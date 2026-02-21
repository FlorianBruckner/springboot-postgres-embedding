package com.example.embedding.repository;

import com.example.embedding.model.Document;
import com.example.embedding.model.DocumentCreateRequest;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository {
    long create(DocumentCreateRequest request);

    void update(long id, String content);

    Optional<Document> findById(long id);

    List<Document> findByIds(List<Long> ids);

    List<Document> keywordSearch(String term, int limit);

    long count();

}
