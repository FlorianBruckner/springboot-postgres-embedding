package com.dreikraft.ai.embedding.postgres.repository;

import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DocumentRepository {
    long create(DocumentCreateRequest request);

    void update(long id, String content);

    void updateDiscussionClassification(long id, String sentiment, String responseDepth);

    Optional<ArticleDocument> findById(long id);

    List<ArticleDocument> findByIds(List<Long> ids);

    List<ArticleDocument> keywordSearch(String term, int limit);

    List<ArticleDocument> keywordSearchBySampleType(String term, int limit, String sampleType);

    List<DiscussionDocument> findDiscussionsByArticleId(long articleDocumentId);

    Map<String, Object> findVectorMetadataById(long id);

    long count();

}
