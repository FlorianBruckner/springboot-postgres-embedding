package com.dreikraft.ai.embedding.postgres.service;

public enum ClassificationStatus {
    SUCCEEDED("succeeded");

    private final String value;

    ClassificationStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
