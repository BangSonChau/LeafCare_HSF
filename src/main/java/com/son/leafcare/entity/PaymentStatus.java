package com.son.leafcare.entity;

public enum PaymentStatus {
    PENDING("Đang xử lý"), SUCCESS("Thành công"), FAILED("Thất bại");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
