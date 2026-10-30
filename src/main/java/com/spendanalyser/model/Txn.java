package com.spendanalyser.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Minimal view of a money movement, so the analytics code stays independent of the database. */
public interface Txn {
    TxnType getType();

    BigDecimal getAmount();

    String getCategory();

    LocalDate getTxnDate();
}
