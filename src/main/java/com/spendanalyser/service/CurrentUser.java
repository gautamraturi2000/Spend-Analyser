package com.spendanalyser.service;

import com.spendanalyser.model.AppUser;
import com.spendanalyser.repo.UserRepository;
import java.security.Principal;
import org.springframework.stereotype.Component;

/** Finds the logged-in user so every query can be limited to that user's own data. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public AppUser get(Principal principal) {
        return users.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user no longer exists"));
    }

    public Long id(Principal principal) {
        return get(principal).getId();
    }
}
