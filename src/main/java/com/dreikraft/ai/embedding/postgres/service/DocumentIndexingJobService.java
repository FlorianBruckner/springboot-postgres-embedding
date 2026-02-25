package com.dreikraft.ai.embedding.postgres.service;

import com.dreikraft.ai.embedding.postgres.repository.DocumentIndexingJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@Slf4j
public class DocumentIndexingJobService {
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final DocumentIndexingJobRepository repository;

    public DocumentIndexingJobService(DocumentIndexingJobRepository repository) {
        this.repository = repository;
    }

    public void enqueue(DocumentIndexingJobType jobType, DocumentType documentType, long documentId) {
        log.debug("Enqueuing indexing job type={}, documentType={}, documentId={}", jobType, documentType, documentId);
        repository.enqueue(jobType.name(), documentType.value(), documentId, OffsetDateTime.now(), DEFAULT_MAX_ATTEMPTS);
    }
}
