package com.dreikraft.ai.embedding.postgres.persistence.repository;

import com.dreikraft.ai.embedding.postgres.persistence.entity.DocumentIndexingJobEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface DocumentIndexingJobJpaRepository extends JpaRepository<DocumentIndexingJobEntity, Long> {

    List<DocumentIndexingJobEntity> findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscIdAsc(
            String status,
            OffsetDateTime dueAt,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DocumentIndexingJobEntity j
            set j.status = :running,
                j.startedAt = :claimedAt,
                j.updatedAt = :updatedAt
            where j.id = :id
              and j.status = :pending
            """)
    int claimPending(
            @Param("id") long id,
            @Param("pending") String pending,
            @Param("running") String running,
            @Param("claimedAt") OffsetDateTime claimedAt,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DocumentIndexingJobEntity j
            set j.status = :succeeded,
                j.completedAt = :completedAt,
                j.updatedAt = :updatedAt
            where j.id = :id
              and j.status = :running
            """)
    int markSucceeded(
            @Param("id") long id,
            @Param("running") String running,
            @Param("succeeded") String succeeded,
            @Param("completedAt") OffsetDateTime completedAt,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DocumentIndexingJobEntity j
            set j.status = :pending,
                j.attempt = j.attempt + 1,
                j.availableAt = :nextAttemptAt,
                j.lastError = :errorMessage,
                j.updatedAt = :updatedAt
            where j.id = :id
              and j.status = :running
              and j.attempt < j.maxAttempts
            """)
    int markFailedWithRetry(
            @Param("id") long id,
            @Param("running") String running,
            @Param("pending") String pending,
            @Param("nextAttemptAt") OffsetDateTime nextAttemptAt,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DocumentIndexingJobEntity j
            set j.status = :deadLetter,
                j.completedAt = :completedAt,
                j.lastError = :errorMessage,
                j.updatedAt = :updatedAt
            where j.id = :id
              and j.status = :running
              and j.attempt >= j.maxAttempts
            """)
    int markDeadLetter(
            @Param("id") long id,
            @Param("running") String running,
            @Param("deadLetter") String deadLetter,
            @Param("completedAt") OffsetDateTime completedAt,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
