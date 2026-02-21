package com.example.embedding.service;

import com.example.embedding.model.Document;
import com.example.embedding.model.DocumentCreateRequest;
import com.example.embedding.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService {
    private final DocumentRepository repository;
    private final DocumentVectorStoreService vectorStoreService;

    public DocumentService(DocumentRepository repository, DocumentVectorStoreService vectorStoreService) {
        this.repository = repository;
        this.vectorStoreService = vectorStoreService;
    }

    public long create(DocumentCreateRequest request) {
        long id = repository.create(request);
        vectorStoreService.upsert(id, request.title(), request.content());
        return id;
    }

    public void update(long id, String content) {
        repository.update(id, content);
        Document updated = findById(id);
        vectorStoreService.upsert(id, updated.title(), updated.content());
    }

    public Document findById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }

    public List<Document> keywordSearch(String term) {
        return repository.keywordSearch(term, 20);
    }

    public List<Document> semanticSearch(String query) {
        List<Long> ids = vectorStoreService.searchIds(query, 20);
        return repository.findByIds(ids);
    }

    public long count() {
        return repository.count();
    }
}
