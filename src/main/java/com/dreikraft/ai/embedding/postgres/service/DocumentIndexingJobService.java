package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.persistence.entity.DocumentIndexingJobEntity;
import com.dreikraft.ai.embedding.postgres.persistence.repository.DocumentIndexingJobJpaRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class DocumentIndexingJobService {

    public static final String JOB_TYPE_UPSERT_VECTOR = "upsert_vector";
    public static final String JOB_TYPE_REFRESH_DISCUSSION_CLASSIFICATION = "refresh_discussion_classification";
    public static final String STATUS_PENDING = "pending";

    private final DocumentIndexingJobJpaRepository repository;

    public DocumentIndexingJobService(DocumentIndexingJobJpaRepository repository) {
        this.repository = repository;
    }

    public void enqueue(String jobType, String documentType, long documentId) {
        DocumentIndexingJobEntity job = new DocumentIndexingJobEntity();
        job.setJobType(jobType);
        job.setDocumentType(documentType);
        job.setDocumentId(documentId);
        job.setStatus(STATUS_PENDING);
        job.setCreatedAt(OffsetDateTime.now());
        repository.save(job);
    }
}
