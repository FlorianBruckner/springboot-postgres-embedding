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
    private final DiscussionClassificationService discussionClassificationService;

    public DocumentService(DocumentRepository repository,
                           DocumentVectorStoreService vectorStoreService,
                           DiscussionClassificationService discussionClassificationService) {
        this.repository = repository;
        this.vectorStoreService = vectorStoreService;
        this.discussionClassificationService = discussionClassificationService;
    }

    public long create(DocumentCreateRequest request) {
        long id = repository.create(request);
        Map<String, Object> properties = request.propertiesOrEmpty();
        if (isDiscussion(properties)) {
            refreshDiscussionClassifications(properties);
        }
        vectorStoreService.upsert(id, request.title(), request.content(), properties);
        return id;
    }

    public void update(long id, String content) {
        repository.update(id, content);
        Document updated = findById(id);
        Map<String, Object> metadata = repository.findVectorMetadataById(id);
        if (isDiscussion(metadata)) {
            refreshDiscussionClassifications(metadata);
        }
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
            threaded.add(new ThreadedDiscussionItem(
                    child,
                    depth,
                    child.sentiment() == null ? "neutral" : child.sentiment(),
                    child.responseDepth() == null ? "substantive" : child.responseDepth()
            ));
            appendThread(threaded, childrenByParent, child.id(), depth + 1);
        }
    }

    private void refreshDiscussionClassifications(Map<String, Object> properties) {
        Long articleDocumentId = toLong(properties.get("relatedArticleDocumentId"));
        if (articleDocumentId == null) {
            return;
        }

        List<DiscussionDocument> discussions = repository.findDiscussionsByArticleId(articleDocumentId);
        if (discussions.isEmpty()) {
            return;
        }

        Document article = findById(articleDocumentId);
        Map<Long, DiscussionClassificationService.DiscussionClassification> classifications =
                discussionClassificationService.classify(new DiscussionClassificationService.DiscussionClassificationInput(
                        article.title(),
                        article.content(),
                        discussions
                ));

        for (DiscussionDocument discussion : discussions) {
            DiscussionClassificationService.DiscussionClassification classification = classifications.get(discussion.id());
            String sentiment = classification == null ? "neutral" : classification.sentiment();
            String responseDepth = classification == null ? "substantive" : classification.responseDepth();
            repository.updateDiscussionClassification(discussion.id(), sentiment, responseDepth);
        }
    }

    private boolean isDiscussion(Map<String, Object> properties) {
        return "discussion".equals(properties.get("sampleType"));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
