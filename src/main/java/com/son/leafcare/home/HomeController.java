package com.son.leafcare.home;

import com.son.leafcare.user.AppUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final DashboardService dashboard;
    private final AppUserService users;

    public HomeController(DashboardService dashboard, AppUserService users) {
        this.dashboard = dashboard;
        this.users = users;
    }

    @GetMapping("/")
    public String home(Authentication auth, Model model) {
        model.addAttribute("stats", dashboard.stats(users.current(auth)));
        return "index";
    }
}
