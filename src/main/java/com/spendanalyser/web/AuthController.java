package com.spendanalyser.web;

import com.spendanalyser.model.AppUser;
import com.spendanalyser.repo.UserRepository;
import java.security.Principal;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.]{3,30}$");

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping("/login")
    public String login(Principal principal) {
        return principal != null ? "redirect:/dashboard" : "login";
    }

    @GetMapping("/register")
    public String registerForm(Principal principal) {
        return principal != null ? "redirect:/dashboard" : "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String fullName,
                           @RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String confirm,
                           Model model) {
        fullName = fullName.trim();
        username = username.trim();
        model.addAttribute("fullName", fullName);
        model.addAttribute("username", username);

        String error = null;
        if (fullName.isEmpty() || fullName.length() > 80) {
            error = "Please enter your name (up to 80 characters).";
        } else if (!USERNAME.matcher(username).matches()) {
            error = "Username must be 3-30 characters: letters, numbers, dot or underscore.";
        } else if (password.length() < 6) {
            error = "Password must be at least 6 characters.";
        } else if (!password.equals(confirm)) {
            error = "Passwords do not match.";
        } else if (users.existsByUsername(username)) {
            error = "That username is already taken.";
        }
        if (error != null) {
            model.addAttribute("error", error);
            return "register";
        }

        AppUser user = new AppUser();
        user.setFullName(fullName);
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        users.save(user);
        return "redirect:/login?registered";
    }
}
