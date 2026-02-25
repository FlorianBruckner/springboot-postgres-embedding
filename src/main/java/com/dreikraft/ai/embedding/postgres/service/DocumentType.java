package com.dreikraft.ai.embedding.postgres.service;

public enum DocumentType {
    ARTICLE("article"),
    DISCUSSION("discussion");

    private final String value;

    DocumentType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static DocumentType fromValue(String value) {
        for (DocumentType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported document_type: " + value);
    }
}
