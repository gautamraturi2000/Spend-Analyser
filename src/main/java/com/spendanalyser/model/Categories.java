package com.spendanalyser.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Categories {

    public static final List<String> EXPENSE = Collections.unmodifiableList(Arrays.asList(
            "Food", "Rent", "Transport", "Shopping", "Bills", "Entertainment",
            "Health", "Education", "Travel", "Subscriptions", "Other"));

    public static final List<String> INCOME = Collections.unmodifiableList(Arrays.asList(
            "Salary", "Business", "Investments", "Gifts", "Other"));

    private Categories() {
    }

    public static List<String> forType(TxnType type) {
        return type == TxnType.INCOME ? INCOME : EXPENSE;
    }

    public static List<String> all() {
        List<String> all = new ArrayList<>(EXPENSE);
        for (String c : INCOME) {
            if (!all.contains(c)) {
                all.add(c);
            }
        }
        return all;
    }
}
