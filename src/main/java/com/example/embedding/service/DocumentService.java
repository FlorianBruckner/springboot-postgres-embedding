package com.example.embedding.service;

import com.example.embedding.model.Document;
import com.example.embedding.model.DocumentCreateRequest;
import com.example.embedding.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService {
    private final DocumentRepository repository;
    private final EmbeddingService embeddingService;

    public DocumentService(DocumentRepository repository, EmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    public long create(DocumentCreateRequest request) {
        List<Float> embedding = embeddingService.embed(request.content());
        return repository.create(request, embedding);
    }

    public void update(long id, String content) {
        List<Float> embedding = embeddingService.embed(content);
        repository.update(id, content, embedding);
    }

    public Document findById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }

    public List<Document> keywordSearch(String term) {
        return repository.keywordSearch(term, 20);
    }

    public List<Document> semanticSearch(String query) {
        List<Float> queryEmbedding = embeddingService.embed(query);
        return repository.semanticSearch(queryEmbedding, 20);
    }

    public long count() {
        return repository.count();
    }
}
