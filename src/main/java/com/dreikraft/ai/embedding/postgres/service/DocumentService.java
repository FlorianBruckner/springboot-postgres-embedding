package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.Document;
import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.dreikraft.ai.embedding.postgres.model.ThreadedDiscussionItem;
import com.dreikraft.ai.embedding.postgres.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentService {
    public static final String ARTICLE_FILTER_EXPRESSION = "sampleType == 'article'";

    private final DocumentRepository repository;
    private final DocumentVectorStoreService vectorStoreService;

    public DocumentService(DocumentRepository repository, DocumentVectorStoreService vectorStoreService) {
        this.repository = repository;
        this.vectorStoreService = vectorStoreService;
    }

    public long create(DocumentCreateRequest request) {
        long id = repository.create(request);
        vectorStoreService.upsert(id, request.title(), request.content(), request.propertiesOrEmpty());
        return id;
    }

    public void update(long id, String content) {
        repository.update(id, content);
        Document updated = findById(id);
        Map<String, Object> metadata = repository.findVectorMetadataById(id);
        vectorStoreService.upsert(id, updated.title(), updated.content(), metadata);
    }

    public Document findById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }

    public List<Document> keywordSearch(String term) {
        return repository.keywordSearch(term, 20);
    }

    public List<Document> keywordArticleSearch(String term) {
        return repository.keywordSearchBySampleType(term, 20, "article");
    }

    public List<Document> semanticSearch(String query) {
        return semanticSearch(query, ARTICLE_FILTER_EXPRESSION);
    }

    public List<Document> semanticSearch(String query, String filterExpression) {
        List<Long> ids = vectorStoreService.searchIds(query, 20, filterExpression);
        return repository.findByIds(ids);
    }

    public long count() {
        return repository.count();
    }

    public List<ThreadedDiscussionItem> findThreadedDiscussionsByArticleId(long articleDocumentId) {
        List<DiscussionDocument> discussions = repository.findDiscussionsByArticleId(articleDocumentId);
        if (discussions.isEmpty()) {
            return List.of();
        }

        Map<Long, List<DiscussionDocument>> childrenByParent = new LinkedHashMap<>();
        for (DiscussionDocument discussion : discussions) {
            childrenByParent.computeIfAbsent(discussion.parentDocumentId(), ignored -> new ArrayList<>())
                    .add(discussion);
        }
        childrenByParent.values().forEach(children -> children.sort(Comparator.comparing(DiscussionDocument::id)));

        List<ThreadedDiscussionItem> threaded = new ArrayList<>();
        appendThread(threaded, childrenByParent, null, 0);
        return threaded;
    }

    private void appendThread(List<ThreadedDiscussionItem> threaded,
                              Map<Long, List<DiscussionDocument>> childrenByParent,
                              Long parentDocumentId,
                              int depth) {
        for (DiscussionDocument child : childrenByParent.getOrDefault(parentDocumentId, List.of())) {
            threaded.add(new ThreadedDiscussionItem(child, depth));
            appendThread(threaded, childrenByParent, child.id(), depth + 1);
        }
    }
}
