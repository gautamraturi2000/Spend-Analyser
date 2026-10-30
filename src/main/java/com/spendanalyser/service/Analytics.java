package com.spendanalyser.service;

import com.spendanalyser.model.Txn;
import com.spendanalyser.model.TxnType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All the money maths in one place: totals, category splits, budgets, trends, forecasts. */
public final class Analytics {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Analytics() {
    }

    // ---------- small result types ----------

    public static class MonthTotal {
        private final YearMonth month;
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;

        public MonthTotal(YearMonth month) {
            this.month = month;
        }

        void add(TxnType type, BigDecimal amount) {
            if (type == TxnType.INCOME) {
                income = income.add(amount);
            } else {
                expense = expense.add(amount);
            }
        }

        public YearMonth getMonth() { return month; }
        public BigDecimal getIncome() { return income; }
        public BigDecimal getExpense() { return expense; }
        public BigDecimal getNet() { return income.subtract(expense); }

        public String getShortLabel() {
            return month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        }

        public String getLabel() {
            return getShortLabel() + " " + month.getYear();
        }

        public String getKey() {
            return month.toString();
        }

        public BigDecimal getSavingsRate() {
            return savingsRate(income, expense);
        }
    }

    public static class BudgetStatus {
        private final String category;
        private final BigDecimal limit;
        private final BigDecimal spent;
        private final int percent;
        private final String level;

        BudgetStatus(String category, BigDecimal limit, BigDecimal spent, int percent, String level) {
            this.category = category;
            this.limit = limit;
            this.spent = spent;
            this.percent = percent;
            this.level = level;
        }

        public String getCategory() { return category; }
        public BigDecimal getLimit() { return limit; }
        public BigDecimal getSpent() { return spent; }
        /** Real percentage, can be above 100. */
        public int getPercent() { return percent; }
        /** Percentage for the progress bar, never above 100. */
        public int getBarPercent() { return Math.min(percent, 100); }
        /** "ok", "warn" (80% or more) or "over" (more than the limit). */
        public String getLevel() { return level; }
        public BigDecimal getRemaining() { return limit.subtract(spent); }
    }

    public static class YearStats {
        private final BigDecimal income;
        private final BigDecimal expense;
        private final BigDecimal avgMonthlyExpense;
        private final String peakMonth;
        private final BigDecimal peakExpense;

        YearStats(BigDecimal income, BigDecimal expense, BigDecimal avgMonthlyExpense,
                  String peakMonth, BigDecimal peakExpense) {
            this.income = income;
            this.expense = expense;
            this.avgMonthlyExpense = avgMonthlyExpense;
            this.peakMonth = peakMonth;
            this.peakExpense = peakExpense;
        }

        public BigDecimal getIncome() { return income; }
        public BigDecimal getExpense() { return expense; }
        public BigDecimal getNet() { return income.subtract(expense); }
        public BigDecimal getAvgMonthlyExpense() { return avgMonthlyExpense; }
        public String getPeakMonth() { return peakMonth; }
        public BigDecimal getPeakExpense() { return peakExpense; }
        public BigDecimal getSavingsRate() { return savingsRate(income, expense); }
    }

    // ---------- calculations ----------

    public static BigDecimal sum(Collection<? extends Txn> list, TxnType type) {
        BigDecimal total = BigDecimal.ZERO;
        for (Txn t : list) {
            if (t.getType() == type && t.getAmount() != null) {
                total = total.add(t.getAmount());
            }
        }
        return total;
    }

    /** Expense totals per category, biggest first. */
    public static Map<String, BigDecimal> expenseByCategory(Collection<? extends Txn> list) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (Txn t : list) {
            if (t.getType() == TxnType.EXPENSE && t.getAmount() != null) {
                totals.merge(t.getCategory(), t.getAmount(), BigDecimal::add);
            }
        }
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(totals.entrySet());
        entries.sort(new Comparator<Map.Entry<String, BigDecimal>>() {
            @Override
            public int compare(Map.Entry<String, BigDecimal> a, Map.Entry<String, BigDecimal> b) {
                int byAmount = b.getValue().compareTo(a.getValue());
                return byAmount != 0 ? byAmount : a.getKey().compareTo(b.getKey());
            }
        });
        Map<String, BigDecimal> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : entries) {
            sorted.put(e.getKey(), e.getValue());
        }
        return sorted;
    }

    /** Income and expense for {@code count} consecutive months starting at {@code start}. */
    public static List<MonthTotal> monthlySeries(Collection<? extends Txn> list, YearMonth start, int count) {
        List<MonthTotal> series = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            series.add(new MonthTotal(start.plusMonths(i)));
        }
        for (Txn t : list) {
            if (t.getTxnDate() == null || t.getAmount() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(t.getTxnDate());
            int index = (ym.getYear() - start.getYear()) * 12 + (ym.getMonthValue() - start.getMonthValue());
            if (index >= 0 && index < count) {
                series.get(index).add(t.getType(), t.getAmount());
            }
        }
        return series;
    }

    public static List<BudgetStatus> budgetStatuses(Map<String, BigDecimal> limits, Map<String, BigDecimal> spent) {
        List<BudgetStatus> out = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : limits.entrySet()) {
            BigDecimal limit = e.getValue();
            BigDecimal used = spent.containsKey(e.getKey()) ? spent.get(e.getKey()) : BigDecimal.ZERO;
            int percent = 0;
            if (limit.signum() > 0) {
                percent = used.multiply(HUNDRED).divide(limit, 0, RoundingMode.HALF_UP).intValue();
            }
            String level = "ok";
            if (limit.signum() > 0 && used.compareTo(limit) > 0) {
                level = "over";
            } else if (percent >= 80) {
                level = "warn";
            }
            out.add(new BudgetStatus(e.getKey(), limit, used, percent, level));
        }
        out.sort(new Comparator<BudgetStatus>() {
            @Override
            public int compare(BudgetStatus a, BudgetStatus b) {
                int byPercent = Integer.compare(b.getPercent(), a.getPercent());
                return byPercent != 0 ? byPercent : a.getCategory().compareTo(b.getCategory());
            }
        });
        return out;
    }

    /** (income - expense) / income as a percentage; 0 when there is no income. */
    public static BigDecimal savingsRate(BigDecimal income, BigDecimal expense) {
        if (income.signum() <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return income.subtract(expense).multiply(HUNDRED).divide(income, 1, RoundingMode.HALF_UP);
    }

    /** Percentage change from previous to current, or null when there is nothing to compare with. */
    public static BigDecimal percentChange(BigDecimal previous, BigDecimal current) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(HUNDRED).divide(previous, 1, RoundingMode.HALF_UP);
    }

    /** Expected total spend by the end of the month at the current pace; null for other months. */
    public static BigDecimal forecast(BigDecimal expenseSoFar, YearMonth month, LocalDate today) {
        if (!month.equals(YearMonth.from(today))) {
            return null;
        }
        int day = today.getDayOfMonth();
        return expenseSoFar.multiply(new BigDecimal(month.lengthOfMonth()))
                .divide(new BigDecimal(day), 2, RoundingMode.HALF_UP);
    }

    public static YearStats yearStats(List<MonthTotal> months, int monthsElapsed) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        String peakMonth = "-";
        BigDecimal peak = BigDecimal.ZERO;
        for (MonthTotal m : months) {
            income = income.add(m.getIncome());
            expense = expense.add(m.getExpense());
            if (m.getExpense().compareTo(peak) > 0) {
                peak = m.getExpense();
                peakMonth = m.getLabel();
            }
        }
        BigDecimal avg = monthsElapsed <= 0
                ? BigDecimal.ZERO.setScale(2)
                : expense.divide(new BigDecimal(monthsElapsed), 2, RoundingMode.HALF_UP);
        return new YearStats(income, expense, avg, peakMonth, peak);
    }

    public static List<Double> toDoubles(List<BigDecimal> values) {
        List<Double> out = new ArrayList<>();
        for (BigDecimal v : values) {
            out.add(v.doubleValue());
        }
        return out;
    }
}
