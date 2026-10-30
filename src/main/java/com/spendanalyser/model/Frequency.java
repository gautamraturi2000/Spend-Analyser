package com.spendanalyser.model;

public enum Frequency {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    private final String label;

    Frequency(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
