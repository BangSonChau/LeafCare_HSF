package com.son.leafcare.controller;

import com.son.leafcare.service.AppUserService;
import com.son.leafcare.service.SubscriptionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PlanController {

    private final AppUserService users;
    private final SubscriptionService subscriptions;

    public PlanController(AppUserService users, SubscriptionService subscriptions) {
        this.users = users;
        this.subscriptions = subscriptions;
    }

    @GetMapping("/plans")
    public String plans(Authentication auth, Model model) {
        model.addAttribute("premiumUntil", subscriptions.premiumUntil(users.current(auth)));
        model.addAttribute("price", subscriptions.premiumPrice());
        model.addAttribute("days", subscriptions.premiumDays());
        return "plans/index";
    }
}
