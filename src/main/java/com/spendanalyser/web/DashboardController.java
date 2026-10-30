package com.spendanalyser.web;

import com.spendanalyser.model.Budget;
import com.spendanalyser.model.Transaction;
import com.spendanalyser.model.TxnType;
import com.spendanalyser.repo.BudgetRepository;
import com.spendanalyser.repo.TransactionRepository;
import com.spendanalyser.service.Analytics;
import com.spendanalyser.service.Analytics.BudgetStatus;
import com.spendanalyser.service.Analytics.MonthTotal;
import com.spendanalyser.service.CurrentUser;
import com.spendanalyser.service.DateUtil;
import com.spendanalyser.service.RecurringService;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private final CurrentUser currentUser;
    private final TransactionRepository transactions;
    private final BudgetRepository budgets;
    private final RecurringService recurring;

    public DashboardController(CurrentUser currentUser, TransactionRepository transactions,
                               BudgetRepository budgets, RecurringService recurring) {
        this.currentUser = currentUser;
        this.transactions = transactions;
        this.budgets = budgets;
        this.recurring = recurring;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@RequestParam(required = false) String month, Principal principal, Model model) {
        Long uid = currentUser.id(principal);
        recurring.generateDue(uid);

        YearMonth ym = DateUtil.parseMonth(month);
        LocalDate today = LocalDate.now();

        // This month plus the five months before it (for the trend chart and the comparison).
        YearMonth trendStart = ym.minusMonths(5);
        List<Transaction> range = transactions.findByUserIdAndTxnDateBetweenOrderByTxnDateDescIdDesc(
                uid, DateUtil.startOf(trendStart), DateUtil.endOf(ym));
        List<Transaction> monthTx = new ArrayList<>();
        for (Transaction t : range) {
            if (YearMonth.from(t.getTxnDate()).equals(ym)) {
                monthTx.add(t);
            }
        }

        BigDecimal income = Analytics.sum(monthTx, TxnType.INCOME);
        BigDecimal expense = Analytics.sum(monthTx, TxnType.EXPENSE);
        List<MonthTotal> series = Analytics.monthlySeries(range, trendStart, 6);
        BigDecimal previousExpense = series.get(4).getExpense();

        Map<String, BigDecimal> byCategory = Analytics.expenseByCategory(monthTx);
        Map<String, BigDecimal> limits = new LinkedHashMap<>();
        for (Budget b : budgets.findByUserIdOrderByCategoryAsc(uid)) {
            limits.put(b.getCategory(), b.getMonthlyLimit());
        }
        List<BudgetStatus> statuses = Analytics.budgetStatuses(limits, byCategory);
        List<BudgetStatus> alerts = new ArrayList<>();
        for (BudgetStatus s : statuses) {
            if (!"ok".equals(s.getLevel())) {
                alerts.add(s);
            }
        }

        List<String> trendLabels = new ArrayList<>();
        List<BigDecimal> trendIncome = new ArrayList<>();
        List<BigDecimal> trendExpense = new ArrayList<>();
        for (MonthTotal m : series) {
            trendLabels.add(m.getShortLabel());
            trendIncome.add(m.getIncome());
            trendExpense.add(m.getExpense());
        }

        model.addAttribute("active", "dashboard");
        model.addAttribute("month", ym.toString());
        model.addAttribute("monthLabel", DateUtil.label(ym));
        model.addAttribute("prevMonth", ym.minusMonths(1).toString());
        model.addAttribute("nextMonth", ym.plusMonths(1).toString());
        model.addAttribute("income", income);
        model.addAttribute("expense", expense);
        model.addAttribute("balance", income.subtract(expense));
        model.addAttribute("savingsRate", Analytics.savingsRate(income, expense));
        model.addAttribute("expenseChange", Analytics.percentChange(previousExpense, expense));
        model.addAttribute("forecast", Analytics.forecast(expense, ym, today));
        model.addAttribute("budgets", statuses);
        model.addAttribute("alerts", alerts);
        model.addAttribute("catLabels", new ArrayList<>(byCategory.keySet()));
        model.addAttribute("catValues", Analytics.toDoubles(new ArrayList<>(byCategory.values())));
        model.addAttribute("trendLabels", trendLabels);
        model.addAttribute("trendIncome", Analytics.toDoubles(trendIncome));
        model.addAttribute("trendExpense", Analytics.toDoubles(trendExpense));
        model.addAttribute("recent", monthTx.size() > 8 ? monthTx.subList(0, 8) : monthTx);
        model.addAttribute("txCount", monthTx.size());
        return "dashboard";
    }
}
