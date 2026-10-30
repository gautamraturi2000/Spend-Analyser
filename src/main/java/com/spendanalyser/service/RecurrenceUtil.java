package com.spendanalyser.service;

import com.spendanalyser.model.Frequency;
import java.time.LocalDate;

public final class RecurrenceUtil {

    private RecurrenceUtil() {
    }

    /**
     * Date of the n-th occurrence (0 = the start date). Always counted from the start date,
     * so a bill on the 31st does not drift to the 28th forever after February.
     */
    public static LocalDate occurrence(LocalDate start, Frequency frequency, int n) {
        switch (frequency) {
            case WEEKLY:
                return start.plusWeeks(n);
            case MONTHLY:
                return start.plusMonths(n);
            case YEARLY:
                return start.plusYears(n);
            default:
                throw new IllegalArgumentException("Unknown frequency: " + frequency);
        }
    }
}
