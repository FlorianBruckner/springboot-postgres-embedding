package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;

import java.util.List;

public interface SemanticSearchRerankingService {
    List<Long> rerank(String query, List<Long> currentRanking, List<ArticleDocument> candidates);
}
