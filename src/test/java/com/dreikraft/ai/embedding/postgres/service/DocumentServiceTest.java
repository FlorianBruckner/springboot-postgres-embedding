package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.Document;
import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.dreikraft.ai.embedding.postgres.model.ThreadedDiscussionItem;
import com.dreikraft.ai.embedding.postgres.repository.DocumentRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    @Test
    void threadedDiscussionsUsePersistedClassificationValues() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);
        DiscussionClassificationService classificationService = mock(DiscussionClassificationService.class);
        SemanticSummaryService semanticSummaryService = mock(SemanticSummaryService.class);
        DocumentService service = new DocumentService(repository, vectorStoreService, classificationService, semanticSummaryService);

        OffsetDateTime now = OffsetDateTime.now();
        DiscussionDocument root = new DiscussionDocument(10L, "Root", "Root message", now, null, "General", "neutral", "substantive");
        DiscussionDocument reply = new DiscussionDocument(11L, "Reply", "Reply message", now, 10L, "General", "positive", "in_depth");

        when(repository.findDiscussionsByArticleId(1L)).thenReturn(List.of(root, reply));

        List<ThreadedDiscussionItem> threaded = service.findThreadedDiscussionsByArticleId(1L);

        assertEquals(2, threaded.size());
        assertEquals("neutral", threaded.get(0).sentiment());
        assertEquals("substantive", threaded.get(0).responseDepth());
        assertEquals("positive", threaded.get(1).sentiment());
        assertEquals("in_depth", threaded.get(1).responseDepth());
        verify(classificationService, never()).classify(any());
    }

    @Test
    void createDiscussionCalculatesAndPersistsClassification() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);
        DiscussionClassificationService classificationService = mock(DiscussionClassificationService.class);
        SemanticSummaryService semanticSummaryService = mock(SemanticSummaryService.class);
        DocumentService service = new DocumentService(repository, vectorStoreService, classificationService, semanticSummaryService);

        OffsetDateTime now = OffsetDateTime.now();
        DiscussionDocument root = new DiscussionDocument(10L, "Root", "Root message", now, null, "General", null, null);
        DiscussionDocument reply = new DiscussionDocument(11L, "Reply", "Reply message", now, 10L, "General", null, null);

        DocumentCreateRequest request = new DocumentCreateRequest(
                "Reply",
                "Reply message",
                Map.of("sampleType", "discussion", "relatedArticleDocumentId", 1L, "respondsToDocumentId", 10L)
        );

        when(repository.create(request)).thenReturn(11L);
        when(repository.findDiscussionsByArticleId(1L)).thenReturn(List.of(root, reply));
        when(repository.findById(1L)).thenReturn(Optional.of(new Document(1L, "Article", "Article content", now)));
        when(classificationService.classify(new DiscussionClassificationService.DiscussionClassificationInput(
                "Article",
                "Article content",
                List.of(root, reply)
        ))).thenReturn(Map.of(
                10L, new DiscussionClassificationService.DiscussionClassification("neutral", "substantive"),
                11L, new DiscussionClassificationService.DiscussionClassification("positive", "in_depth")
        ));

        long createdId = service.create(request);

        assertEquals(11L, createdId);
        verify(repository).updateDiscussionClassification(10L, "neutral", "substantive");
        verify(repository).updateDiscussionClassification(11L, "positive", "in_depth");
        verify(vectorStoreService).upsert(11L, "Reply", "Reply message", request.propertiesOrEmpty());
    }

    @Test
    void createArticleUsesLlmSummaryForEmbedding() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);
        DiscussionClassificationService classificationService = mock(DiscussionClassificationService.class);
        SemanticSummaryService semanticSummaryService = mock(SemanticSummaryService.class);
        DocumentService service = new DocumentService(repository, vectorStoreService, classificationService, semanticSummaryService);

        DocumentCreateRequest request = new DocumentCreateRequest(
                "Java Streams",
                "Long original content",
                Map.of("sampleType", "article")
        );

        when(repository.create(request)).thenReturn(7L);
        when(semanticSummaryService.summarizeDocumentForEmbedding("Java Streams", "Long original content"))
                .thenReturn("Clean plain-language summary");

        long id = service.create(request);

        assertEquals(7L, id);
        verify(vectorStoreService).upsert(7L, "Java Streams", "Clean plain-language summary", request.propertiesOrEmpty());
    }

    @Test
    void semanticSearchUsesSummarizedQuery() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);
        DiscussionClassificationService classificationService = mock(DiscussionClassificationService.class);
        SemanticSummaryService semanticSummaryService = mock(SemanticSummaryService.class);
        DocumentService service = new DocumentService(repository, vectorStoreService, classificationService, semanticSummaryService);

        when(semanticSummaryService.summarizeQueryForSemanticSearch("how streams work"))
                .thenReturn("java streams basics");
        when(vectorStoreService.searchIds("java streams basics", 20, DocumentService.ARTICLE_FILTER_EXPRESSION))
                .thenReturn(List.of(2L));
        when(repository.findByIds(List.of(2L))).thenReturn(List.of(
                new Document(2L, "Streams", "...", OffsetDateTime.now())
        ));

        List<Document> results = service.semanticSearch("how streams work");

        assertEquals(1, results.size());
        verify(vectorStoreService).searchIds("java streams basics", 20, DocumentService.ARTICLE_FILTER_EXPRESSION);
    }
}
