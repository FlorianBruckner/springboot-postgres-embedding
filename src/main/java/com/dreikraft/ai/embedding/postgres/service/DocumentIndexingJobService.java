package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class DocumentIndexingJobService {

    public static final String JOB_TYPE_UPSERT_VECTOR = "upsert_vector";
    public static final String JOB_TYPE_REFRESH_DISCUSSION_CLASSIFICATION = "refresh_discussion_classification";
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final DocumentIndexingJobRepository repository;

    public DocumentIndexingJobService(DocumentIndexingJobRepository repository) {
        this.repository = repository;
    }

    public void enqueue(String jobType, String documentType, long documentId) {
        repository.enqueue(jobType, documentType, documentId, OffsetDateTime.now(), DEFAULT_MAX_ATTEMPTS);
    }
}
