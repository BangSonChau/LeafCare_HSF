package com.son.leafcare.entity;

public enum Plan {
    FREE("Free"), PREMIUM("Premium");

    private final String label;

    Plan(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
