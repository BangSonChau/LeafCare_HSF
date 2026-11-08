package com.son.leafcare.entity;

public enum ReviewStatus {
    DRAFT("Bản nháp"),
    APPROVED("Đã duyệt"),
    WITHDRAWN("Đã rút");

    private final String label;

    ReviewStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

