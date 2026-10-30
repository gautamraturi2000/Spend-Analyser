package com.spendanalyser.model;

public enum TxnType {
    INCOME("Income"),
    EXPENSE("Expense");

    private final String label;

    TxnType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
