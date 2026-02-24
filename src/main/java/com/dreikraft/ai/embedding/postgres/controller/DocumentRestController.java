package com.dreikraft.ai.embedding.postgres.controller;

import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.DocumentUpdateRequest;
import com.dreikraft.ai.embedding.postgres.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentRestController {
    private final DocumentService service;

    public DocumentRestController(DocumentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@Valid @RequestBody DocumentCreateRequest request) {
        return Map.of("id", service.create(request));
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable long id, @Valid @RequestBody DocumentUpdateRequest request) {
        service.update(id, request.content());
    }

    @GetMapping("/{id}")
    public ArticleDocument byId(@PathVariable long id) {
        return service.findById(id);
    }

    @GetMapping("/search")
    public List<ArticleDocument> keywordSearch(@RequestParam String query) {
        return service.keywordSearch(query);
    }

    @GetMapping("/semantic-search")
    public List<ArticleDocument> semanticSearch(@RequestParam String query,
                                         @RequestParam(required = false) String filterExpression) {
        return service.semanticSearch(query, filterExpression);
    }
}
