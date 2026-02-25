package com.dreikraft.ai.embedding.postgres.persistence.repository;

import com.dreikraft.ai.embedding.postgres.persistence.entity.DocumentIndexingJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIndexingJobJpaRepository extends JpaRepository<DocumentIndexingJobEntity, Long> {
}
