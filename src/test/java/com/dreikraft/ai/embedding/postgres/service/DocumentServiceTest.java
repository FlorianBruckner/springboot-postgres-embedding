package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.dreikraft.ai.embedding.postgres.model.Document;
import com.dreikraft.ai.embedding.postgres.model.ThreadedDiscussionItem;
import com.dreikraft.ai.embedding.postgres.repository.DocumentRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    @Test
    void threadedDiscussionsContainLlmClassifications() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);
        DiscussionClassificationService classificationService = mock(DiscussionClassificationService.class);
        DocumentService service = new DocumentService(repository, vectorStoreService, classificationService);

        OffsetDateTime now = OffsetDateTime.now();
        DiscussionDocument root = new DiscussionDocument(10L, "Root", "Root message", now, null, "General");
        DiscussionDocument reply = new DiscussionDocument(11L, "Reply", "Reply message", now, 10L, "General");
        DiscussionDocument offTopicReply = new DiscussionDocument(12L, "Reply 2", "Completely unrelated tangent", now, 10L, "General");

        when(repository.findDiscussionsByArticleId(1L)).thenReturn(List.of(root, reply, offTopicReply));
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(
                new Document(1L, "Article", "Article content", now)
        ));
        when(classificationService.classify(new DiscussionClassificationService.DiscussionClassificationInput(
                "Article",
                "Article content",
                List.of(root, reply, offTopicReply)
        ))).thenReturn(Map.of(
                10L, new DiscussionClassificationService.DiscussionClassification("neutral", "substantive"),
                11L, new DiscussionClassificationService.DiscussionClassification("positive", "in_depth"),
                12L, new DiscussionClassificationService.DiscussionClassification("negative", "off_topic")
        ));

        List<ThreadedDiscussionItem> threaded = service.findThreadedDiscussionsByArticleId(1L);

        assertEquals(3, threaded.size());
        assertEquals("neutral", threaded.get(0).sentiment());
        assertEquals("substantive", threaded.get(0).responseDepth());

        assertEquals("positive", threaded.get(1).sentiment());
        assertEquals("in_depth", threaded.get(1).responseDepth());

        assertEquals("negative", threaded.get(2).sentiment());
        assertEquals("off_topic", threaded.get(2).responseDepth());
    }
}
