package com.son.leafcare.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
import com.son.leafcare.service.momo.MomoException;
import java.time.LocalDateTime;
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
    void checkoutShowsSummaryAndMomoButton() throws Exception {
        mvc.perform(get("/payments/checkout")).andExpect(status().isOk())
                .andExpect(content().string(containsString("49.000 ₫")))
                .andExpect(content().string(containsString("Thanh toán bằng MoMo")))
                .andExpect(content().string(containsString("action=\"/payments/momo\"")));
    }

    @Test
    void anonymousCannotCheckout() throws Exception {
        mvc.perform(get("/payments/checkout").with(anonymous())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void payRedirectsToMomo() throws Exception {
        when(payments.startPremium(me)).thenReturn("https://test-payment.momo.vn/pay/x");
        mvc.perform(post("/payments/momo").with(csrf())).andExpect(redirectedUrl("https://test-payment.momo.vn/pay/x"));
    }

    @Test
    void momoDownShowsError() throws Exception {
        when(payments.startPremium(me)).thenThrow(new MomoException("x"));
        mvc.perform(post("/payments/momo").with(csrf())).andExpect(redirectedUrl("/payments/checkout"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void returnGoesToResultPageIgnoringOtherParams() throws Exception {
        mvc.perform(get("/payments/momo/return").param("orderId", "LC1").param("resultCode", "0"))
                .andExpect(redirectedUrl("/payments/LC1"));
    }

    @Test
    void ipnIsPublicWithoutCsrfAndReturns204() throws Exception {
        when(payments.handleIpn(anyMap())).thenReturn(true);
        mvc.perform(post("/payments/momo/ipn").with(anonymous()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"LC1\"}")).andExpect(status().isNoContent());
    }

    @Test
    void ipnWithBadSignatureIs400() throws Exception {
        when(payments.handleIpn(anyMap())).thenReturn(false);
        mvc.perform(post("/payments/momo/ipn").with(anonymous()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"LC1\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void resultSuccess() throws Exception {
        when(payments.refresh("LC1", me)).thenReturn(payment(PaymentStatus.SUCCESS));
        when(subscriptions.premiumUntil(me)).thenReturn(LocalDateTime.of(2026, 12, 8, 12, 0));
        mvc.perform(get("/payments/LC1")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Thanh toán thành công")))
                .andExpect(content().string(containsString("08/12/2026 12:00")));
    }

    @Test
    void resultFailedShowsMomoMessageAndRetry() throws Exception {
        when(payments.refresh("LC1", me)).thenReturn(payment(PaymentStatus.FAILED));
        mvc.perform(get("/payments/LC1"))
                .andExpect(content().string(containsString("Thanh toán không thành công")))
                .andExpect(content().string(containsString("Giao dịch bị từ chối")))
                .andExpect(content().string(containsString("Thanh toán lại")));
    }

    @Test
    void resultPendingOffersRecheck() throws Exception {
        when(payments.refresh("LC1", me)).thenReturn(payment(PaymentStatus.PENDING));
        mvc.perform(get("/payments/LC1"))
                .andExpect(content().string(containsString("Đang chờ MoMo xác nhận")))
                .andExpect(content().string(containsString("Kiểm tra lại")));
    }

    @Test
    void otherUsersOrderIs404() throws Exception {
        when(payments.refresh(eq("LC9"), any())).thenThrow(new NotFoundException());
        mvc.perform(get("/payments/LC9")).andExpect(status().isNotFound());
    }
}
