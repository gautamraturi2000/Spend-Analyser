package com.spendanalyser.web;

import com.spendanalyser.model.AppUser;
import com.spendanalyser.service.CurrentUser;
import java.security.Principal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Puts the user's display name into every page for the navigation bar. */
@ControllerAdvice
public class GlobalAdvice {

    private final CurrentUser currentUser;

    public GlobalAdvice(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @ModelAttribute("currentName")
    public String currentName(Principal principal) {
        if (principal == null) {
            return null;
        }
        AppUser user = currentUser.get(principal);
        String full = user.getFullName();
        return (full == null || full.trim().isEmpty()) ? user.getUsername() : full;
    }
}
