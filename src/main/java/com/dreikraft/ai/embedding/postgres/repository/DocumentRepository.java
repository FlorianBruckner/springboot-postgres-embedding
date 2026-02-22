package com.dreikraft.ai.embedding.postgres.repository;

import com.dreikraft.ai.embedding.postgres.model.Document;
import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;

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
