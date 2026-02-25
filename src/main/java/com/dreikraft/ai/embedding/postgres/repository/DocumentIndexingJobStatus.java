package com.dreikraft.ai.embedding.postgres.repository;

public enum DocumentIndexingJobStatus {
    PENDING("pending"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    DEAD_LETTER("dead_letter");

    private final String value;

    DocumentIndexingJobStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
