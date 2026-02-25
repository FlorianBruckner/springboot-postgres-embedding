package com.dreikraft.ai.embedding.postgres.persistence.repository;

import com.dreikraft.ai.embedding.postgres.persistence.entity.DiscussionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiscussionJpaRepository extends JpaRepository<DiscussionEntity, Long> {

    @Query(value = """
            SELECT *
            FROM discussion_documents
            WHERE id = :id
            """, nativeQuery = true)
    Optional<DiscussionEntity> findDiscussionById(@Param("id") Long id);

    @Query(value = """
            SELECT *
            FROM discussion_documents
            WHERE to_tsvector('english', title || ' ' || content) @@ plainto_tsquery('english', :term)
            ORDER BY updated_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<DiscussionEntity> keywordSearchDiscussions(@Param("term") String term, @Param("limit") int limit);

    @Query(value = """
            SELECT *
            FROM discussion_documents
            WHERE article_id = :articleDocumentId
              AND parent_discussion_id IS NULL
            ORDER BY id
            """, nativeQuery = true)
    List<DiscussionEntity> findRootDiscussionsByArticleDocumentIdOrderByIdAsc(@Param("articleDocumentId") Long articleDocumentId);

    @Query(value = """
            SELECT COUNT(*)
            FROM discussion_documents
            """, nativeQuery = true)
    long countDiscussions();
}
