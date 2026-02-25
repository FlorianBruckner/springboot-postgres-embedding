package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.semantic-search.rerank.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpSemanticSearchRerankingService implements SemanticSearchRerankingService {
    @Override
    public List<Long> rerank(String query, List<Long> currentRanking, List<ArticleDocument> candidates) {
        return currentRanking;
    }
}
