package com.dreikraft.ai.embedding.postgres.persistence.repository;

import com.dreikraft.ai.embedding.postgres.persistence.entity.DiscussionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DiscussionJpaRepository extends JpaRepository<DiscussionEntity, Long> {

    @Query(value = """
            SELECT *
            FROM documents
            WHERE article_document_id IS NOT NULL
              AND to_tsvector('english', title || ' ' || content) @@ plainto_tsquery('english', :term)
            ORDER BY updated_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<DiscussionEntity> keywordSearchDiscussions(@Param("term") String term, @Param("limit") int limit);

    List<DiscussionEntity> findByArticleDocumentIdOrderByIdAsc(Long articleDocumentId);
}
