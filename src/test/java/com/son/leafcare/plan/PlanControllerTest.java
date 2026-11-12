package com.son.leafcare.plan;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.user.AppUser;
import com.son.leafcare.user.AppUserService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlanController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "u@x.com")
class PlanControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService users;

    @MockitoBean
    SubscriptionService subscriptions;

    @BeforeEach
    void stubs() {
        when(users.current(any())).thenReturn(new AppUser());
        when(subscriptions.premiumPrice()).thenReturn(49000L);
        when(subscriptions.premiumDays()).thenReturn(30);
    }

    @Test
    void freeUserSeesUpgradeButton() throws Exception {
        mvc.perform(get("/plans")).andExpect(status().isOk()).andExpect(view().name("plans/index"))
                .andExpect(content().string(containsString("49.000 ₫")))
                .andExpect(content().string(containsString("5 lượt quét lá mỗi ngày")))
                .andExpect(content().string(containsString("Nâng cấp Premium")))
                .andExpect(content().string(containsString("href=\"/payments\"")))
                .andExpect(content().string(containsString("href=\"/payments/checkout\"")))
                .andExpect(content().string(not(containsString("Hết hạn"))));
    }

    @Test
    void premiumUserSeesEndDateAndRenew() throws Exception {
        when(subscriptions.premiumUntil(any())).thenReturn(LocalDateTime.of(2026, 12, 8, 10, 0));
        mvc.perform(get("/plans"))
                .andExpect(content().string(containsString("08/12/2026 10:00")))
                .andExpect(content().string(containsString("Gia hạn thêm 30 ngày")))
                .andExpect(content().string(not(containsString("Nâng cấp Premium"))));
    }
}
