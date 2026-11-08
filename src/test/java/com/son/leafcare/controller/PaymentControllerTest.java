package com.son.leafcare.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Payment;
import com.son.leafcare.entity.PaymentStatus;
import com.son.leafcare.entity.Plan;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.service.AppUserService;
import com.son.leafcare.service.PaymentService;
import com.son.leafcare.service.SubscriptionService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "u@x.com")
class PaymentControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService users;

    @MockitoBean
    SubscriptionService subscriptions;

    @MockitoBean
    PaymentService payments;

    AppUser me = new AppUser();

    @BeforeEach
    void stubs() {
        when(users.current(any())).thenReturn(me);
        when(subscriptions.premiumPrice()).thenReturn(49000L);
        when(subscriptions.premiumDays()).thenReturn(30);
    }

    Payment payment(PaymentStatus status) {
        Payment p = new Payment();
        p.setOrderId("LC1");
        p.setPlan(Plan.PREMIUM);
        p.setAmount(49000);
        p.setStatus(status);
        p.setMessage("Giao dịch bị từ chối");
        ReflectionTestUtils.setField(p, "createdAt", LocalDateTime.of(2026, 11, 8, 12, 0));
        return p;
    }

    @Test
    void checkoutShowsSummaryAndQrButton() throws Exception {
        mvc.perform(get("/payments/checkout")).andExpect(status().isOk())
                .andExpect(content().string(containsString("49.000 ₫")))
                .andExpect(content().string(containsString("Tạo mã QR chuyển khoản")))
                .andExpect(content().string(containsString("action=\"/payments/premium\"")));
    }

    @Test
    void anonymousCannotCheckout() throws Exception {
        mvc.perform(get("/payments/checkout").with(anonymous())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void startCreatesOrderAndOpensItsQrPage() throws Exception {
        when(payments.startPremium(me)).thenReturn(payment(PaymentStatus.PENDING));
        mvc.perform(post("/payments/premium").with(csrf())).andExpect(redirectedUrl("/payments/LC1"));
    }

    @Test
    void webhookIsPublicWithoutCsrfAndAnswersSuccessJson() throws Exception {
        when(payments.validKey("Apikey k")).thenReturn(true);
        mvc.perform(post("/payments/sepay/webhook").with(anonymous()).header("Authorization", "Apikey k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"LC1\"}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"success\":true}"));
        verify(payments).handleWebhook(anyMap());
    }

    @Test
    void webhookWithWrongKeyIs401AndChangesNothing() throws Exception {
        mvc.perform(post("/payments/sepay/webhook").with(anonymous()).header("Authorization", "Apikey bad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"LC1\"}"))
                .andExpect(status().isUnauthorized());
        verify(payments, never()).handleWebhook(anyMap());
    }

    @Test
    void resultSuccess() throws Exception {
        when(payments.getForUser("LC1", me)).thenReturn(payment(PaymentStatus.SUCCESS));
        when(subscriptions.premiumUntil(me)).thenReturn(LocalDateTime.of(2026, 12, 8, 12, 0));
        mvc.perform(get("/payments/LC1")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Thanh toán thành công")))
                .andExpect(content().string(containsString("08/12/2026 12:00")));
    }

    @Test
    void resultFailedShowsMessageAndRetry() throws Exception {
        when(payments.getForUser("LC1", me)).thenReturn(payment(PaymentStatus.FAILED));
        mvc.perform(get("/payments/LC1"))
                .andExpect(content().string(containsString("Thanh toán không thành công")))
                .andExpect(content().string(containsString("Giao dịch bị từ chối")))
                .andExpect(content().string(containsString("Thanh toán lại")));
    }

    @Test
    void resultPendingOffersRecheck() throws Exception {
        when(payments.getForUser("LC1", me)).thenReturn(payment(PaymentStatus.PENDING));
        mvc.perform(get("/payments/LC1"))
                .andExpect(content().string(containsString("Quét mã để thanh toán")))
                .andExpect(content().string(containsString(
                        "https://vietqr.app/img?acc=0123456789&amp;bank=MBBank&amp;amount=49000&amp;des=LC1")))
                .andExpect(content().string(containsString("NGUYEN VAN A")))
                .andExpect(content().string(containsString("http-equiv=\"refresh\"")))
                .andExpect(content().string(containsString("kiểm tra lại")));
    }

    @Test
    void otherUsersOrderIs404() throws Exception {
        when(payments.getForUser(eq("LC9"), any())).thenThrow(new NotFoundException());
        mvc.perform(get("/payments/LC9")).andExpect(status().isNotFound());
    }

    @Test
    void historyEmptyState() throws Exception {
        when(payments.history(me, 0)).thenReturn(Page.empty(PageRequest.of(0, 10)));
        mvc.perform(get("/payments")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có giao dịch nào")))
                .andExpect(content().string(containsString("href=\"/plans\"")));
    }

    @Test
    void historyListsOrdersWithStatusAndLink() throws Exception {
        when(payments.history(me, 1)).thenReturn(new PageImpl<>(
                List.of(payment(PaymentStatus.SUCCESS)), PageRequest.of(1, 10), 11));
        mvc.perform(get("/payments").param("page", "1")).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/payments/LC1\"")))
                .andExpect(content().string(containsString("Thành công")))
                .andExpect(content().string(containsString("49.000 ₫")))
                .andExpect(content().string(containsString("08/11/2026 12:00")))
                .andExpect(content().string(containsString("aria-label=\"Phân trang\"")));
    }

    @Test
    void anonymousCannotSeeHistory() throws Exception {
        mvc.perform(get("/payments").with(anonymous())).andExpect(redirectedUrl("/login"));
    }
}
