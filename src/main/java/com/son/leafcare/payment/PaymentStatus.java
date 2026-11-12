package com.son.leafcare.payment;

public enum PaymentStatus {
    PENDING("Chờ thanh toán"), SUCCESS("Thành công"), FAILED("Thất bại");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
