package com.spendanalyser.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class DateUtil {

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private DateUtil() {
    }

    /** Parses "2026-10"; falls back to the current month for null or bad input. */
    public static YearMonth parseMonth(String text) {
        if (text == null || text.trim().isEmpty()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException e) {
            return YearMonth.now();
        }
    }

    public static String label(YearMonth ym) {
        return ym.format(LABEL);
    }

    public static LocalDate startOf(YearMonth ym) {
        return ym.atDay(1);
    }

    public static LocalDate endOf(YearMonth ym) {
        return ym.atEndOfMonth();
    }
}
