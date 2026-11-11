package com.son.leafcare.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Payment;
import com.son.leafcare.entity.PaymentStatus;
import com.son.leafcare.entity.Plan;
import com.son.leafcare.service.AdminService;
import com.son.leafcare.service.AdminStats;
import com.son.leafcare.service.AppUserService;
import com.son.leafcare.service.DayCount;
import com.son.leafcare.service.TopScanner;
import com.son.leafcare.service.UserRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "a@x.com", roles = {"USER", "ADMIN"})
class AdminControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService users;

    @MockitoBean
    AdminService admin;

    static AppUser user(String email, String name) {
        AppUser u = new AppUser();
        u.setId((long) email.hashCode());
        u.setEmail(email);
        u.setName(name);
        u.setCreatedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        return u;
    }

    static List<DayCount> days(long... counts) {
        List<DayCount> d = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            d.add(new DayCount(LocalDate.of(2026, 10, 26).plusDays(i), i < counts.length ? counts[i] : 0));
        }
        return d;
    }

    @Test
    void dashboardShowsNumbersTablesAndChart() throws Exception {
        AppUser shin = user("shin@x.com", "Shin");
        Payment p = new Payment();
        p.setUser(shin);
        p.setOrderId("LC12345678901234567");
        p.setPlan(Plan.PREMIUM);
        p.setAmount(49000);
        p.setStatus(PaymentStatus.SUCCESS);
        p.setCreatedAt(LocalDateTime.of(2026, 11, 8, 14, 5));
        when(admin.stats()).thenReturn(new AdminStats(12, 3, 2, 1_234_000, 98_000, 4, 20,
                days(1, 0, 5), List.of(p), List.of(new TopScanner(shin, 9))));
        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(view().name("admin/index"))
                .andExpect(content().string(containsString("1.234.000\u00a0₫")))
                .andExpect(content().string(containsString("98.000 ₫")))
                .andExpect(content().string(containsString("LC12345678901234567")))
                .andExpect(content().string(containsString("shin@x.com")))
                .andExpect(content().string(containsString("Lượt quét 14 ngày")))
                .andExpect(content().string(containsString("href=\"/admin/users\"")))
                // shared admin shell: sidebar with all three sections and a search box that targets the user list
                .andExpect(content().string(containsString("href=\"/admin/documents\"")))
                .andExpect(content().string(containsString("action=\"/admin/users\"")))
                .andExpect(content().string(containsString("Tổng quan")));
    }

    @Test
    void dashboardEmptyStateHasNoTables() throws Exception {
        when(admin.stats()).thenReturn(new AdminStats(0, 0, 0, 0, 0, 0, 0, days(), List.of(), List.of()));
        mvc.perform(get("/admin")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có giao dịch nào")))
                .andExpect(content().string(containsString("Chưa có lượt quét nào")));
    }

    @Test
    void usersListShowsPlanAndScans() throws Exception {
        UserRow premium = new UserRow(user("b@x.com", "Bình"), LocalDateTime.of(2026, 12, 8, 10, 0), 7);
        UserRow free = new UserRow(user("c@x.com", null), null, 0);
        when(admin.users(eq("b"), eq(0))).thenReturn(new PageImpl<>(List.of(premium, free), PageRequest.of(0, 20), 2));
        mvc.perform(get("/admin/users").param("q", " b ")).andExpect(status().isOk()).andExpect(view().name("admin/users"))
                .andExpect(content().string(containsString("Bình")))
                .andExpect(content().string(containsString("08/12/2026")))
                .andExpect(content().string(containsString("c@x.com")))
                .andExpect(content().string(containsString("value=\"b\"")));
    }

    @Test
    void usersEmptySearch() throws Exception {
        when(admin.users(any(), eq(0))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mvc.perform(get("/admin/users").param("q", "zzz")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Không tìm thấy người dùng nào")))
                .andExpect(content().string(not(containsString("<table"))));
    }

    @Test
    @WithMockUser(username = "u@x.com", roles = "USER")
    void userIsForbidden() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users")).andExpect(status().isForbidden());
    }
}
