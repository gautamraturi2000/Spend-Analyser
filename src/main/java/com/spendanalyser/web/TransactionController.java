package com.spendanalyser.web;

import com.spendanalyser.model.Categories;
import com.spendanalyser.model.Transaction;
import com.spendanalyser.model.TxnType;
import com.spendanalyser.repo.TransactionRepository;
import com.spendanalyser.service.Analytics;
import com.spendanalyser.service.CurrentUser;
import com.spendanalyser.service.DateUtil;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TransactionController {

    private static final LocalDate FAR_PAST = LocalDate.of(1900, 1, 1);
    private static final LocalDate FAR_FUTURE = LocalDate.of(2999, 12, 31);

    private final CurrentUser currentUser;
    private final TransactionRepository transactions;

    public TransactionController(CurrentUser currentUser, TransactionRepository transactions) {
        this.currentUser = currentUser;
        this.transactions = transactions;
    }

    /**
     * Month handling: no "month" parameter means the current month, an empty one means all time.
     */
    private List<Transaction> load(Long uid, String month, String type, String category, String q) {
        LocalDate from = FAR_PAST;
        LocalDate to = FAR_FUTURE;
        if (month == null || !month.trim().isEmpty()) {
            YearMonth ym = DateUtil.parseMonth(month);
            from = DateUtil.startOf(ym);
            to = DateUtil.endOf(ym);
        }
        List<Transaction> all = transactions.findByUserIdAndTxnDateBetweenOrderByTxnDateDescIdDesc(uid, from, to);
        List<Transaction> out = new ArrayList<>();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        for (Transaction t : all) {
            if (type != null && !type.isEmpty() && !t.getType().name().equals(type)) {
                continue;
            }
            if (category != null && !category.isEmpty() && !t.getCategory().equals(category)) {
                continue;
            }
            if (!needle.isEmpty()) {
                String hay = (t.getCategory() + " " + (t.getDescription() == null ? "" : t.getDescription()))
                        .toLowerCase(Locale.ROOT);
                if (!hay.contains(needle)) {
                    continue;
                }
            }
            out.add(t);
        }
        return out;
    }

    @GetMapping("/transactions")
    public String list(@RequestParam(required = false) String month,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       Principal principal, Model model) {
        Long uid = currentUser.id(principal);
        List<Transaction> list = load(uid, month, type, category, q);

        String monthValue = month == null ? YearMonth.now().toString() : month;
        model.addAttribute("active", "transactions");
        model.addAttribute("list", list);
        model.addAttribute("month", monthValue);
        model.addAttribute("type", type == null ? "" : type);
        model.addAttribute("category", category == null ? "" : category);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("allCategories", Categories.all());
        model.addAttribute("income", Analytics.sum(list, TxnType.INCOME));
        model.addAttribute("expense", Analytics.sum(list, TxnType.EXPENSE));
        return "transactions";
    }

    // ---------- add / edit ----------

    private void formModel(Model model, TransactionForm form, Long editId) {
        model.addAttribute("active", "transactions");
        model.addAttribute("form", form);
        model.addAttribute("editId", editId);
        model.addAttribute("types", TxnType.values());
        model.addAttribute("expenseCats", Categories.EXPENSE);
        model.addAttribute("incomeCats", Categories.INCOME);
    }

    @GetMapping("/transactions/new")
    public String newForm(@RequestParam(required = false) String type, Model model) {
        TransactionForm form = new TransactionForm();
        if ("INCOME".equals(type)) {
            form.setType(TxnType.INCOME);
        }
        formModel(model, form, null);
        return "transaction-form";
    }

    @GetMapping("/transactions/{id}/edit")
    public String editForm(@PathVariable Long id, Principal principal, Model model) {
        Transaction t = transactions.findByIdAndUserId(id, currentUser.id(principal)).orElse(null);
        if (t == null) {
            return "redirect:/transactions";
        }
        TransactionForm form = new TransactionForm();
        form.setType(t.getType());
        form.setAmount(t.getAmount());
        form.setCategory(t.getCategory());
        form.setDescription(t.getDescription());
        form.setDate(t.getTxnDate());
        formModel(model, form, id);
        return "transaction-form";
    }

    private String validate(TransactionForm f, BindingResult result) {
        if (result.hasErrors()) {
            return "Please check the amount and the date.";
        }
        if (f.getType() == null) {
            return "Choose income or expense.";
        }
        if (f.getAmount() == null || f.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "Amount must be greater than zero.";
        }
        if (f.getAmount().compareTo(new BigDecimal("999999999")) > 0) {
            return "That amount is too large.";
        }
        if (f.getDate() == null) {
            return "Please pick a date.";
        }
        if (f.getCategory() == null || !Categories.forType(f.getType()).contains(f.getCategory())) {
            return "Please choose a category.";
        }
        return null;
    }

    private void copy(TransactionForm f, Transaction t) {
        t.setType(f.getType());
        t.setAmount(f.getAmount().setScale(2, RoundingMode.HALF_UP));
        t.setCategory(f.getCategory());
        String d = f.getDescription() == null ? "" : f.getDescription().trim();
        t.setDescription(d.length() > 200 ? d.substring(0, 200) : d);
        t.setTxnDate(f.getDate());
    }

    @PostMapping("/transactions")
    public String create(@ModelAttribute("form") TransactionForm form, BindingResult result,
                         Principal principal, Model model) {
        String error = validate(form, result);
        if (error != null) {
            formModel(model, form, null);
            model.addAttribute("error", error);
            return "transaction-form";
        }
        Transaction t = new Transaction();
        t.setUserId(currentUser.id(principal));
        copy(form, t);
        transactions.save(t);
        return "redirect:/transactions?month=" + YearMonth.from(form.getDate());
    }

    @PostMapping("/transactions/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") TransactionForm form,
                         BindingResult result, Principal principal, Model model) {
        Transaction t = transactions.findByIdAndUserId(id, currentUser.id(principal)).orElse(null);
        if (t == null) {
            return "redirect:/transactions";
        }
        String error = validate(form, result);
        if (error != null) {
            formModel(model, form, id);
            model.addAttribute("error", error);
            return "transaction-form";
        }
        copy(form, t);
        transactions.save(t);
        return "redirect:/transactions?month=" + YearMonth.from(form.getDate());
    }

    @PostMapping("/transactions/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal) {
        transactions.findByIdAndUserId(id, currentUser.id(principal)).ifPresent(transactions::delete);
        return "redirect:/transactions";
    }

    // ---------- CSV export ----------

    @GetMapping("/transactions/export")
    public void export(@RequestParam(required = false) String month,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       Principal principal, HttpServletResponse response) throws IOException {
        List<Transaction> list = load(currentUser.id(principal), month, type, category, q);
        String name = (month == null || month.isEmpty()) ? "all" : month;
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"transactions-" + name + ".csv\"");
        StringBuilder sb = new StringBuilder("Date,Type,Category,Description,Amount\n");
        for (Transaction t : list) {
            sb.append(t.getTxnDate()).append(',')
              .append(t.getType().getLabel()).append(',')
              .append(csv(t.getCategory())).append(',')
              .append(csv(t.getDescription())).append(',')
              .append(t.getAmount().toPlainString()).append('\n');
        }
        response.getWriter().write(sb.toString());
    }

    static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        // Stop spreadsheet formulas from running when the file is opened.
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
