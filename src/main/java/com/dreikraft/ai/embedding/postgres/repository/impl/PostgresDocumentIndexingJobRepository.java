package com.dreikraft.ai.embedding.postgres.repository.impl;

import com.dreikraft.ai.embedding.postgres.persistence.entity.DocumentIndexingJobEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.DocumentIndexingJobJpaRepository;
import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobCreateRequest;
import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobRecord;
import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobRepository;
import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Repository
@Transactional
public class PostgresDocumentIndexingJobRepository implements DocumentIndexingJobRepository {

    private final DocumentIndexingJobJpaRepository jpaRepository;

    public PostgresDocumentIndexingJobRepository(DocumentIndexingJobJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public long enqueue(String jobType, String documentType, long documentId, OffsetDateTime availableAt, int maxAttempts) {
        DocumentIndexingJobEntity entity = new DocumentIndexingJobEntity();
        entity.setJobType(jobType);
        entity.setDocumentType(documentType);
        entity.setDocumentId(documentId);
        entity.setStatus(DocumentIndexingJobStatus.PENDING.value());
        entity.setAttempt(0);
        entity.setMaxAttempts(maxAttempts);
        entity.setAvailableAt(availableAt);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return jpaRepository.save(entity).getId();
    }

    @Override
    public List<Long> enqueueBatch(Collection<DocumentIndexingJobCreateRequest> jobs) {
        List<DocumentIndexingJobEntity> entities = jobs.stream()
                .map(this::toEntity)
                .toList();
        return jpaRepository.saveAll(entities).stream()
                .map(DocumentIndexingJobEntity::getId)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentIndexingJobRecord> pollDue(DocumentIndexingJobStatus status, OffsetDateTime dueAt, int limit) {
        return jpaRepository.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscIdAsc(
                        status.value(),
                        dueAt,
                        PageRequest.of(0, limit)
                ).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public boolean claimPending(long jobId, OffsetDateTime claimedAt) {
        return jpaRepository.claimPending(
                jobId,
                DocumentIndexingJobStatus.PENDING.value(),
                DocumentIndexingJobStatus.RUNNING.value(),
                claimedAt,
                OffsetDateTime.now()) == 1;
    }

    @Override
    public boolean markSucceeded(long jobId, OffsetDateTime completedAt) {
        return jpaRepository.markSucceeded(
                jobId,
                DocumentIndexingJobStatus.RUNNING.value(),
                DocumentIndexingJobStatus.SUCCEEDED.value(),
                completedAt,
                OffsetDateTime.now()) == 1;
    }

    @Override
    public boolean markFailedWithRetry(long jobId, OffsetDateTime nextAttemptAt, String errorMessage) {
        return jpaRepository.markFailedWithRetry(
                jobId,
                DocumentIndexingJobStatus.RUNNING.value(),
                DocumentIndexingJobStatus.PENDING.value(),
                nextAttemptAt,
                errorMessage,
                OffsetDateTime.now()) == 1;
    }

    @Override
    public boolean markDeadLetter(long jobId, OffsetDateTime completedAt, String errorMessage) {
        return jpaRepository.markDeadLetter(
                jobId,
                DocumentIndexingJobStatus.RUNNING.value(),
                DocumentIndexingJobStatus.DEAD_LETTER.value(),
                completedAt,
                errorMessage,
                OffsetDateTime.now()) == 1;
    }

    private DocumentIndexingJobEntity toEntity(DocumentIndexingJobCreateRequest request) {
        DocumentIndexingJobEntity entity = new DocumentIndexingJobEntity();
        entity.setJobType(request.jobType());
        entity.setDocumentType(request.documentType());
        entity.setDocumentId(request.documentId());
        entity.setStatus(DocumentIndexingJobStatus.PENDING.value());
        entity.setAttempt(0);
        entity.setMaxAttempts(request.maxAttempts());
        entity.setAvailableAt(request.availableAt());
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }

    private DocumentIndexingJobRecord toRecord(DocumentIndexingJobEntity entity) {
        return new DocumentIndexingJobRecord(
                entity.getId(),
                entity.getJobType(),
                entity.getDocumentType(),
                entity.getDocumentId(),
                DocumentIndexingJobStatus.valueOf(entity.getStatus().toUpperCase()),
                entity.getAttempt(),
                entity.getMaxAttempts(),
                entity.getAvailableAt(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
