package com.son.leafcare.entity;

public enum ChatMessageStatus {
    COMPLETED("Hoàn tất"),
    NEEDS_CLARIFICATION("Cần làm rõ"),
    INSUFFICIENT_EVIDENCE("Chưa đủ tài liệu"),
    AI_UNAVAILABLE("AI tạm ngưng"),
    ERROR("Lỗi xử lý");

    private final String label;

    ChatMessageStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

