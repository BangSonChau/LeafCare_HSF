package com.son.leafcare.home;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.scan.ScanHistory;
import com.son.leafcare.user.AppUserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class HomeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService users;

    @MockitoBean
    DashboardService dashboard;

    @Test
    void homeShowsStatCardsAndEmptyRecentState() throws Exception {
        when(dashboard.stats(any())).thenReturn(new DashboardStats(3, 2, 7, List.of()));
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("stats"))
                .andExpect(content().string(containsString("href=\"/plants\"")))
                .andExpect(content().string(containsString("href=\"/diseases\"")))
                .andExpect(content().string(containsString("href=\"/scans\"")))
                .andExpect(content().string(containsString(">3<")))
                .andExpect(content().string(containsString(">2<")))
                .andExpect(content().string(containsString(">7<")))
                .andExpect(content().string(containsString("Chưa có lượt quét nào")))
                .andExpect(content().string(containsString("Quét lá")))
                .andExpect(content().string(containsString("href=\"/admin\"")))
                .andExpect(content().string(containsString("Trang quản trị")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void userSeesNoAdminLinks() throws Exception {
        when(dashboard.stats(any())).thenReturn(new DashboardStats(3, 2, 7, List.of()));
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/admin\""))))
                .andExpect(content().string(not(containsString("Trang quản trị"))));
    }

    @Test
    void homeListsRecentScansLinkingToDetail() throws Exception {
        ScanHistory s = new ScanHistory();
        ReflectionTestUtils.setField(s, "id", 42L);
        s.setImageFileName("a.jpg");
        s.setNote("Lá vàng");
        ReflectionTestUtils.setField(s, "createdAt", java.time.LocalDateTime.of(2026, 11, 7, 9, 30));
        when(dashboard.stats(any())).thenReturn(new DashboardStats(1, 1, 1, List.of(s)));
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("href=\"/scans/42\"")))
                .andExpect(content().string(containsString("Lá vàng")));
    }
}
