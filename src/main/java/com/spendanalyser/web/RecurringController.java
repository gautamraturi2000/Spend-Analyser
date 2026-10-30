package com.spendanalyser.web;

import com.spendanalyser.model.Categories;
import com.spendanalyser.model.Frequency;
import com.spendanalyser.model.RecurringRule;
import com.spendanalyser.model.TxnType;
import com.spendanalyser.repo.RecurringRuleRepository;
import com.spendanalyser.service.CurrentUser;
import com.spendanalyser.service.RecurrenceUtil;
import com.spendanalyser.service.RecurringService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RecurringController {

    /** A rule together with the date of its next occurrence, for display. */
    public static class RuleRow {
        private final RecurringRule rule;
        private final LocalDate next;

        RuleRow(RecurringRule rule, LocalDate next) {
            this.rule = rule;
            this.next = next;
        }

        public RecurringRule getRule() { return rule; }
        public LocalDate getNext() { return next; }
    }

    private final CurrentUser currentUser;
    private final RecurringRuleRepository rules;
    private final RecurringService recurring;

    public RecurringController(CurrentUser currentUser, RecurringRuleRepository rules, RecurringService recurring) {
        this.currentUser = currentUser;
        this.rules = rules;
        this.recurring = recurring;
    }

    @GetMapping("/recurring")
    public String page(@RequestParam(required = false) String error, Principal principal, Model model) {
        Long uid = currentUser.id(principal);
        recurring.generateDue(uid);

        List<RuleRow> rows = new ArrayList<>();
        for (RecurringRule r : rules.findByUserIdOrderByIdDesc(uid)) {
            LocalDate next = null;
            if (r.isActive()) {
                next = RecurrenceUtil.occurrence(r.getStartDate(), r.getFrequency(), r.getOccurrences());
                if (r.getEndDate() != null && next.isAfter(r.getEndDate())) {
                    next = null;
                }
            }
            rows.add(new RuleRow(r, next));
        }

        model.addAttribute("active", "recurring");
        model.addAttribute("rows", rows);
        model.addAttribute("types", TxnType.values());
        model.addAttribute("frequencies", Frequency.values());
        model.addAttribute("expenseCats", Categories.EXPENSE);
        model.addAttribute("incomeCats", Categories.INCOME);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("error", error == null ? null
                : "Please check the amount, category and dates (the end date cannot be before the start date).");
        return "recurring";
    }

    @PostMapping("/recurring")
    public String add(@RequestParam TxnType type,
                      @RequestParam String amount,
                      @RequestParam String category,
                      @RequestParam(required = false) String description,
                      @RequestParam Frequency frequency,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                      Principal principal) {
        BigDecimal value;
        try {
            value = new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            return "redirect:/recurring?error";
        }
        if (value.signum() <= 0 || value.compareTo(new BigDecimal("999999999")) > 0
                || !Categories.forType(type).contains(category)
                || (endDate != null && endDate.isBefore(startDate))) {
            return "redirect:/recurring?error";
        }
        RecurringRule r = new RecurringRule();
        r.setUserId(currentUser.id(principal));
        r.setType(type);
        r.setAmount(value.setScale(2, RoundingMode.HALF_UP));
        r.setCategory(category);
        String d = description == null ? "" : description.trim();
        r.setDescription(d.length() > 200 ? d.substring(0, 200) : d);
        r.setFrequency(frequency);
        r.setStartDate(startDate);
        r.setEndDate(endDate);
        r.setActive(true);
        rules.save(r);
        recurring.generateDue(r.getUserId());
        return "redirect:/recurring";
    }

    @PostMapping("/recurring/{id}/toggle")
    public String toggle(@PathVariable Long id, Principal principal) {
        rules.findByIdAndUserId(id, currentUser.id(principal)).ifPresent(r -> {
            r.setActive(!r.isActive());
            rules.save(r);
        });
        return "redirect:/recurring";
    }

    @PostMapping("/recurring/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal) {
        rules.findByIdAndUserId(id, currentUser.id(principal)).ifPresent(rules::delete);
        return "redirect:/recurring";
    }
}
