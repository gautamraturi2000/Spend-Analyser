package com.spendanalyser.service;

import com.spendanalyser.model.RecurringRule;
import com.spendanalyser.model.Transaction;
import com.spendanalyser.repo.RecurringRuleRepository;
import com.spendanalyser.repo.TransactionRepository;
import java.time.LocalDate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns recurring rules (rent, salary, subscriptions) into real transactions when they fall due. */
@Service
public class RecurringService {

    private static final int SAFETY_LIMIT = 1000;

    private final RecurringRuleRepository rules;
    private final TransactionRepository transactions;

    public RecurringService(RecurringRuleRepository rules, TransactionRepository transactions) {
        this.rules = rules;
        this.transactions = transactions;
    }

    /** Creates every due transaction for one user. Called whenever the dashboard opens. */
    @Transactional
    public int generateDue(Long userId) {
        int created = 0;
        LocalDate today = LocalDate.now();
        for (RecurringRule rule : rules.findByUserIdAndActiveTrue(userId)) {
            created += generate(rule, today);
        }
        return created;
    }

    /** Runs every night so rules are also applied for users who do not log in. */
    @Scheduled(cron = "0 10 0 * * *")
    @Transactional
    public void generateForEveryone() {
        LocalDate today = LocalDate.now();
        for (RecurringRule rule : rules.findByActiveTrue()) {
            generate(rule, today);
        }
    }

    private int generate(RecurringRule rule, LocalDate today) {
        int created = 0;
        while (created < SAFETY_LIMIT) {
            LocalDate due = RecurrenceUtil.occurrence(rule.getStartDate(), rule.getFrequency(), rule.getOccurrences());
            if (due.isAfter(today)) {
                break;
            }
            if (rule.getEndDate() != null && due.isAfter(rule.getEndDate())) {
                rule.setActive(false);
                break;
            }
            Transaction t = new Transaction();
            t.setUserId(rule.getUserId());
            t.setType(rule.getType());
            t.setAmount(rule.getAmount());
            t.setCategory(rule.getCategory());
            String text = rule.getDescription() == null ? "" : rule.getDescription().trim();
            t.setDescription(text.isEmpty() ? "Recurring" : text + " (recurring)");
            t.setTxnDate(due);
            t.setRuleId(rule.getId());
            transactions.save(t);
            rule.setOccurrences(rule.getOccurrences() + 1);
            created++;
        }
        rules.save(rule);
        return created;
    }
}
