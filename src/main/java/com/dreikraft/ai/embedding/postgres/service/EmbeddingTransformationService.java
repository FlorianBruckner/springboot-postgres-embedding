package com.dreikraft.ai.embedding.postgres.service;

import java.util.List;

public interface EmbeddingTransformationService {
    List<EmbeddingVariant> transformForArticle(String title, String content);

    List<EmbeddingVariant> transformForDiscussion(String articleTitle, String discussionTitle, String content);

    record EmbeddingVariant(String label, String content) {
    }
}
