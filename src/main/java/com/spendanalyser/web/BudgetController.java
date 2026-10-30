package com.spendanalyser.web;

import com.spendanalyser.model.Budget;
import com.spendanalyser.model.Categories;
import com.spendanalyser.model.Transaction;
import com.spendanalyser.repo.BudgetRepository;
import com.spendanalyser.repo.TransactionRepository;
import com.spendanalyser.service.Analytics;
import com.spendanalyser.service.CurrentUser;
import com.spendanalyser.service.DateUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class BudgetController {

    private final CurrentUser currentUser;
    private final BudgetRepository budgets;
    private final TransactionRepository transactions;

    public BudgetController(CurrentUser currentUser, BudgetRepository budgets, TransactionRepository transactions) {
        this.currentUser = currentUser;
        this.budgets = budgets;
        this.transactions = transactions;
    }

    @GetMapping("/budgets")
    public String page(@RequestParam(required = false) String error, Principal principal, Model model) {
        Long uid = currentUser.id(principal);
        YearMonth ym = YearMonth.now();
        List<Transaction> monthTx = transactions.findByUserIdAndTxnDateBetweenOrderByTxnDateDescIdDesc(
                uid, DateUtil.startOf(ym), DateUtil.endOf(ym));
        Map<String, BigDecimal> spent = Analytics.expenseByCategory(monthTx);

        Map<String, BigDecimal> limits = new LinkedHashMap<>();
        Map<String, Long> ids = new LinkedHashMap<>();
        for (Budget b : budgets.findByUserIdOrderByCategoryAsc(uid)) {
            limits.put(b.getCategory(), b.getMonthlyLimit());
            ids.put(b.getCategory(), b.getId());
        }

        model.addAttribute("active", "budgets");
        model.addAttribute("monthLabel", DateUtil.label(ym));
        model.addAttribute("statuses", Analytics.budgetStatuses(limits, spent));
        model.addAttribute("ids", ids);
        model.addAttribute("categories", Categories.EXPENSE);
        model.addAttribute("error", error == null ? null : "Enter a limit greater than zero and choose a category.");
        return "budgets";
    }

    @PostMapping("/budgets")
    public String save(@RequestParam String category, @RequestParam String limitText, Principal principal) {
        BigDecimal limit;
        try {
            limit = new BigDecimal(limitText.trim());
        } catch (NumberFormatException e) {
            return "redirect:/budgets?error";
        }
        if (limit.signum() <= 0 || limit.compareTo(new BigDecimal("999999999")) > 0
                || !Categories.EXPENSE.contains(category)) {
            return "redirect:/budgets?error";
        }
        Long uid = currentUser.id(principal);
        Budget b = budgets.findByUserIdAndCategory(uid, category).orElseGet(Budget::new);
        b.setUserId(uid);
        b.setCategory(category);
        b.setMonthlyLimit(limit.setScale(2, RoundingMode.HALF_UP));
        budgets.save(b);
        return "redirect:/budgets";
    }

    @PostMapping("/budgets/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal) {
        budgets.findByIdAndUserId(id, currentUser.id(principal)).ifPresent(budgets::delete);
        return "redirect:/budgets";
    }
}
