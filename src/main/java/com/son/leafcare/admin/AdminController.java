package com.son.leafcare.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** ADMIN-only pages; SecurityConfig blocks /admin/** for everyone else. */
@Controller
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("stats", admin.stats());
        return "admin/index";
    }

    @GetMapping("/admin/users")
    public String users(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, Model model) {
        String term = q == null ? "" : q.trim();
        model.addAttribute("page", admin.users(term, page));
        model.addAttribute("q", term);
        return "admin/users";
    }
}
