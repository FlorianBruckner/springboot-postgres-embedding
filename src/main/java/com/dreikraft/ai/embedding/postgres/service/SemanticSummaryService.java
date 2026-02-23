package com.dreikraft.ai.embedding.postgres.service;

public interface SemanticSummaryService {
    String summarizeDocumentForEmbedding(String title, String content);

    String summarizeQueryForSemanticSearch(String query);
}
