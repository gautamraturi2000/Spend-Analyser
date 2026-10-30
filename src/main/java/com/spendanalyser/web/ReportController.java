package com.spendanalyser.web;

import com.spendanalyser.model.Transaction;
import com.spendanalyser.model.TxnType;
import com.spendanalyser.repo.TransactionRepository;
import com.spendanalyser.service.Analytics;
import com.spendanalyser.service.Analytics.MonthTotal;
import com.spendanalyser.service.Analytics.YearStats;
import com.spendanalyser.service.CurrentUser;
import com.spendanalyser.service.RecurringService;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ReportController {

    /** One row of the category table, with its share of total spending. */
    public static class CategoryRow {
        private final String category;
        private final BigDecimal amount;
        private final BigDecimal share;

        CategoryRow(String category, BigDecimal amount, BigDecimal share) {
            this.category = category;
            this.amount = amount;
            this.share = share;
        }

        public String getCategory() { return category; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getShare() { return share; }
    }

    private final CurrentUser currentUser;
    private final TransactionRepository transactions;
    private final RecurringService recurring;

    public ReportController(CurrentUser currentUser, TransactionRepository transactions, RecurringService recurring) {
        this.currentUser = currentUser;
        this.transactions = transactions;
        this.recurring = recurring;
    }

    private List<Transaction> loadYear(Long uid, int year) {
        return transactions.findByUserIdAndTxnDateBetweenOrderByTxnDateDescIdDesc(
                uid, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    private int clampYear(Integer year) {
        int now = LocalDate.now().getYear();
        if (year == null || year < 2000 || year > now + 1) {
            return now;
        }
        return year;
    }

    private int monthsElapsed(int year) {
        LocalDate today = LocalDate.now();
        if (year < today.getYear()) {
            return 12;
        }
        return year == today.getYear() ? today.getMonthValue() : 0;
    }

    @GetMapping("/reports")
    public String report(@RequestParam(required = false) Integer year, Principal principal, Model model) {
        Long uid = currentUser.id(principal);
        recurring.generateDue(uid);
        int y = clampYear(year);

        List<Transaction> list = loadYear(uid, y);
        List<MonthTotal> months = Analytics.monthlySeries(list, YearMonth.of(y, 1), 12);
        YearStats stats = Analytics.yearStats(months, monthsElapsed(y));
        Map<String, BigDecimal> byCategory = Analytics.expenseByCategory(list);

        List<String> labels = new ArrayList<>();
        List<BigDecimal> income = new ArrayList<>();
        List<BigDecimal> expense = new ArrayList<>();
        for (MonthTotal m : months) {
            labels.add(m.getShortLabel());
            income.add(m.getIncome());
            expense.add(m.getExpense());
        }

        List<Transaction> biggest = new ArrayList<>();
        for (Transaction t : list) {
            if (t.getType() == TxnType.EXPENSE) {
                biggest.add(t);
            }
        }
        biggest.sort(new Comparator<Transaction>() {
            @Override
            public int compare(Transaction a, Transaction b) {
                return b.getAmount().compareTo(a.getAmount());
            }
        });

        List<Integer> years = new ArrayList<>();
        int now = LocalDate.now().getYear();
        for (int i = now; i >= now - 5; i--) {
            years.add(i);
        }

        model.addAttribute("active", "reports");
        model.addAttribute("year", y);
        model.addAttribute("years", years);
        model.addAttribute("months", months);
        model.addAttribute("stats", stats);
        List<CategoryRow> categoryRows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : byCategory.entrySet()) {
            BigDecimal share = stats.getExpense().signum() > 0
                    ? e.getValue().multiply(new BigDecimal("100")).divide(stats.getExpense(), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            categoryRows.add(new CategoryRow(e.getKey(), e.getValue(), share));
        }
        model.addAttribute("categoryRows", categoryRows);
        model.addAttribute("catLabels", new ArrayList<>(byCategory.keySet()));
        model.addAttribute("catValues", Analytics.toDoubles(new ArrayList<>(byCategory.values())));
        model.addAttribute("labels", labels);
        model.addAttribute("incomeSeries", Analytics.toDoubles(income));
        model.addAttribute("expenseSeries", Analytics.toDoubles(expense));
        model.addAttribute("biggest", biggest.size() > 5 ? biggest.subList(0, 5) : biggest);
        model.addAttribute("hasData", !list.isEmpty());
        return "reports";
    }

    @GetMapping("/reports/export")
    public void export(@RequestParam(required = false) Integer year, Principal principal,
                       HttpServletResponse response) throws IOException {
        int y = clampYear(year);
        List<MonthTotal> months = Analytics.monthlySeries(loadYear(currentUser.id(principal), y), YearMonth.of(y, 1), 12);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"yearly-report-" + y + ".csv\"");
        StringBuilder sb = new StringBuilder("Month,Income,Expense,Net,Savings rate %\n");
        for (MonthTotal m : months) {
            sb.append(m.getLabel()).append(',')
              .append(m.getIncome().toPlainString()).append(',')
              .append(m.getExpense().toPlainString()).append(',')
              .append(m.getNet().toPlainString()).append(',')
              .append(m.getSavingsRate().toPlainString()).append('\n');
        }
        response.getWriter().write(sb.toString());
    }
}
