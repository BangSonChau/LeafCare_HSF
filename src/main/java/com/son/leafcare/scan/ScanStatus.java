package com.son.leafcare.scan;

public enum ScanStatus {
    PENDING("Chờ phân tích"),
    DONE("Đã phân tích"),
    FAILED("Chưa phân tích được");

    private final String label;

    ScanStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
