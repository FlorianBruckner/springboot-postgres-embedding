package com.dreikraft.ai.embedding.postgres.model;

public record ThreadedDiscussionItem(
        DiscussionDocument discussion,
        int depth,
        String sentiment,
        String responseDepth
) {
}
