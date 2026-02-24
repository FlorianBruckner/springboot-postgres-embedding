package com.dreikraft.ai.embedding.postgres.persistence.repository;

import com.dreikraft.ai.embedding.postgres.persistence.entity.ArticleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArticleJpaRepository extends JpaRepository<ArticleEntity, Long> {

    @Query(value = """
            SELECT *
            FROM documents
            WHERE article_document_id IS NULL
              AND id = :id
            """, nativeQuery = true)
    Optional<ArticleEntity> findArticleById(@Param("id") Long id);

    @Query(value = """
            SELECT *
            FROM documents
            WHERE article_document_id IS NULL
              AND id IN (:ids)
            """, nativeQuery = true)
    List<ArticleEntity> findArticlesByIdIn(@Param("ids") List<Long> ids);

    @Query(value = """
            SELECT *
            FROM documents
            WHERE article_document_id IS NULL
              AND to_tsvector('english', title || ' ' || content) @@ plainto_tsquery('english', :term)
            ORDER BY updated_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ArticleEntity> keywordSearchArticles(@Param("term") String term, @Param("limit") int limit);

    @Query(value = """
            SELECT COUNT(*)
            FROM documents
            WHERE article_document_id IS NULL
            """, nativeQuery = true)
    long countArticles();

    @Query(value = """
            SELECT COUNT(*)
            FROM documents
            WHERE article_document_id IS NULL
              AND id = :id
            """, nativeQuery = true)
    long countArticleById(@Param("id") long id);
}
