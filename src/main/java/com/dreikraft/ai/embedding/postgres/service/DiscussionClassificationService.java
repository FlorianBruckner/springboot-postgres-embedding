package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;

import java.util.List;
import java.util.Map;

public interface DiscussionClassificationService {
    Map<Long, DiscussionClassification> classify(DiscussionClassificationInput input);

    record DiscussionClassificationInput(String articleTitle,
                                         String articleContent,
                                         List<DiscussionDocument> discussions) {
    }

    record DiscussionClassification(String sentiment, String responseDepth) {
    }
}
