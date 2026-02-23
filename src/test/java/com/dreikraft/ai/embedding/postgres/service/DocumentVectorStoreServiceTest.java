package com.dreikraft.ai.embedding.postgres.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentVectorStoreServiceTest {

    @Test
    void searchUsesConfiguredSimilarityThreshold() {
        VectorStore vectorStore = mock(VectorStore.class);
        Document result = mock(Document.class);
        when(result.getId()).thenReturn("42");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(result));

        DocumentVectorStoreService service = new DocumentVectorStoreService(vectorStore, 0.75);

        List<Long> ids = service.searchIds("java", 10, null);

        ArgumentCaptor<SearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(searchRequestCaptor.capture());
        assertEquals(0.75, searchRequestCaptor.getValue().getSimilarityThreshold());
        assertEquals(List.of(42L), ids);
    }

    @Test
    void upsertKeepsOnlyMetadataUsedForVectorQueries() {
        VectorStore vectorStore = mock(VectorStore.class);
        DocumentVectorStoreService service = new DocumentVectorStoreService(vectorStore, 0.75);

        service.upsert(7L, "A title", "A body", Map.of(
                "sampleType", "article",
                "discussionItemId", "x-1",
                "relatedArticleDocumentId", 1L
        ));

        ArgumentCaptor<List<Document>> addedDocumentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(addedDocumentsCaptor.capture());
        Document added = addedDocumentsCaptor.getValue().getFirst();

        assertEquals("article", added.getMetadata().get("sampleType"));
        assertEquals(1L, added.getMetadata().get("relatedArticleDocumentId"));
        assertFalse(added.getMetadata().containsKey("discussionItemId"));
    }
}
