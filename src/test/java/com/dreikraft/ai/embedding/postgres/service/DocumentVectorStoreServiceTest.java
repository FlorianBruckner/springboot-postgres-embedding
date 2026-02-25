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
    void searchUsesConfiguredSimilarityThresholdAndEntityMetadata() {
        VectorStore vectorStore = mock(VectorStore.class);
        Document result = mock(Document.class);
        when(result.getMetadata()).thenReturn(Map.of("entityId", 42L));
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
    void upsertVariantsStoresMultipleEmbeddingDocumentsPerEntity() {
        VectorStore vectorStore = mock(VectorStore.class);
        DocumentVectorStoreService service = new DocumentVectorStoreService(vectorStore, 0.75);

        service.upsertVariants(7L, "article", "A title", List.of(
                new EmbeddingTransformationService.EmbeddingVariant("original", "A body"),
                new EmbeddingTransformationService.EmbeddingVariant("keywords", "a, body")
        ), Map.of(
                "sampleType", "article",
                "discussionItemId", "x-1",
                "relatedArticleDocumentId", 1L
        ));

        ArgumentCaptor<List<Document>> addedDocumentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(addedDocumentsCaptor.capture());
        List<Document> added = addedDocumentsCaptor.getValue();

        assertEquals(2, added.size());
        assertEquals("article:7:0", added.get(0).getId());
        assertEquals("article:7:1", added.get(1).getId());
        assertEquals("article", added.get(0).getMetadata().get("sampleType"));
        assertEquals(1L, added.get(0).getMetadata().get("relatedArticleDocumentId"));
        assertEquals(7L, added.get(0).getMetadata().get("entityId"));
        assertEquals("article", added.get(0).getMetadata().get("entityType"));
        assertEquals("original", added.get(0).getMetadata().get("embeddingVariant"));
        assertFalse(added.get(0).getMetadata().containsKey("discussionItemId"));
    }
}
