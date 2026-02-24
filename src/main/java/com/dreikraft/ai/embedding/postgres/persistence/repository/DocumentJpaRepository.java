package com.dreikraft.ai.embedding.postgres.persistence.repository;

import com.dreikraft.ai.embedding.postgres.persistence.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, Long> {

    List<DocumentEntity> findByIdIn(List<Long> ids);

    @Query(value = """
            SELECT *
            FROM documents
            WHERE to_tsvector('english', title || ' ' || content) @@ plainto_tsquery('english', :term)
            ORDER BY updated_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentEntity> keywordSearch(@Param("term") String term, @Param("limit") int limit);

    @Query(value = """
            SELECT *
            FROM documents
            WHERE article_document_id IS NULL
              AND to_tsvector('english', title || ' ' || content) @@ plainto_tsquery('english', :term)
            ORDER BY updated_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentEntity> keywordSearchArticles(@Param("term") String term, @Param("limit") int limit);

    @Query(value = """
            SELECT *
            FROM documents
            WHERE article_document_id IS NOT NULL
              AND to_tsvector('english', title || ' ' || content) @@ plainto_tsquery('english', :term)
            ORDER BY updated_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentEntity> keywordSearchDiscussions(@Param("term") String term, @Param("limit") int limit);

    List<DocumentEntity> findByArticleDocumentIdOrderByIdAsc(Long articleDocumentId);
}
