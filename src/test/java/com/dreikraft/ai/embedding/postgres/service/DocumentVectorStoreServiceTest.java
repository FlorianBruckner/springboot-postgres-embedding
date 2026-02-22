package com.dreikraft.ai.embedding.postgres.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
