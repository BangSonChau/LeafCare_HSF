package com.son.leafcare.entity;

public enum ScanStatus {
    PENDING("Chờ phân tích");

    private final String label;

    ScanStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
