package com.dreikraft.ai.embedding.postgres.service;

public enum EmbeddingStatus {
    SUCCEEDED("succeeded");

    private final String value;

    EmbeddingStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
