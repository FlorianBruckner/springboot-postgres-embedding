package com.dreikraft.ai.embedding.postgres.repository.impl;

import com.dreikraft.ai.embedding.postgres.mapper.DocumentEntityMapper;
import com.dreikraft.ai.embedding.postgres.model.ArticleDocument;
import com.dreikraft.ai.embedding.postgres.model.DocumentCreateRequest;
import com.dreikraft.ai.embedding.postgres.model.DiscussionDocument;
import com.dreikraft.ai.embedding.postgres.persistence.entity.DocumentEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.DocumentJpaRepository;
import com.dreikraft.ai.embedding.postgres.repository.DocumentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Transactional
@ConditionalOnProperty(prefix = "app.database", name = "vendor", havingValue = "postgres", matchIfMissing = true)
public class PostgresDocumentRepository implements DocumentRepository {
    private static final String TYPE_ARTICLE = "article";
    private static final String TYPE_DISCUSSION = "discussion";

    private final DocumentJpaRepository jpaRepository;
    private final DocumentEntityMapper mapper;

    public PostgresDocumentRepository(DocumentJpaRepository jpaRepository, DocumentEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(DocumentCreateRequest request) {
        Map<String, Object> properties = request.propertiesOrEmpty();

        DocumentEntity entity = new DocumentEntity();
        entity.setTitle(request.title());
        entity.setContent(request.content());
        entity.setArticleDocumentId(toLong(properties.get("relatedArticleDocumentId")));
        entity.setParentDocumentId(toLong(properties.get("respondsToDocumentId")));
        entity.setDiscussionSection(toStringValue(properties.get("discussionSection")));

        return jpaRepository.save(entity).getId();
    }

    @Override
    public void update(long id, String content) {
        DocumentEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        entity.setContent(content);
        entity.setContentHash(hashContent(content));
        entity.setEmbeddingModel(null);
        entity.setEmbeddingVersion(null);
        entity.setEmbeddedAt(null);

        jpaRepository.save(entity);
    }

    @Override
    public void updateDiscussionClassification(long id, String sentiment, String responseDepth) {
        DocumentEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        entity.setSentiment(sentiment);
        entity.setResponseDepth(responseDepth);
        entity.setClassificationStatus("completed");
        entity.setClassifiedAt(OffsetDateTime.now());
        if (entity.getClassificationSource() == null || entity.getClassificationSource().isBlank()) {
            entity.setClassificationSource("llm");
        }

        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArticleDocument> findById(long id) {
        return jpaRepository.findById(id).map(mapper::toArticleDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleDocument> findByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<DocumentEntity> entities = jpaRepository.findByIdIn(ids);
        Map<Long, ArticleDocument> byId = entities.stream()
                .map(mapper::toArticleDocument)
                .collect(Collectors.toMap(ArticleDocument::id, a -> a));

        List<ArticleDocument> ordered = new ArrayList<>();
        for (Long id : ids) {
            ArticleDocument article = byId.get(id);
            if (article != null) {
                ordered.add(article);
            }
        }
        return ordered;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleDocument> keywordSearch(String term, int limit) {
        return jpaRepository.keywordSearch(term, limit).stream()
                .map(mapper::toArticleDocument)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleDocument> keywordSearchBySampleType(String term, int limit, String sampleType) {
        List<DocumentEntity> entities = TYPE_DISCUSSION.equals(sampleType)
                ? jpaRepository.keywordSearchDiscussions(term, limit)
                : jpaRepository.keywordSearchArticles(term, limit);
        return entities.stream().map(mapper::toArticleDocument).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscussionDocument> findDiscussionsByArticleId(long articleDocumentId) {
        return jpaRepository.findByArticleDocumentIdOrderByIdAsc(articleDocumentId)
                .stream()
                .map(mapper::toDiscussionDocument)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> findVectorMetadataById(long id) {
        DocumentEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sampleType", entity.getArticleDocumentId() == null ? TYPE_ARTICLE : TYPE_DISCUSSION);

        if (entity.getArticleDocumentId() != null) {
            metadata.put("relatedArticleDocumentId", entity.getArticleDocumentId());
        }

        if (entity.getParentDocumentId() != null) {
            metadata.put("respondsToDocumentId", entity.getParentDocumentId());
        }

        if (entity.getDiscussionSection() != null && !entity.getDiscussionSection().isBlank()) {
            metadata.put("discussionSection", entity.getDiscussionSection());
        }

        return metadata;
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpaRepository.count();
    }


    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String hashContent(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
