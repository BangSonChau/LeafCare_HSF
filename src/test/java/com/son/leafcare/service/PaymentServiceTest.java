package com.son.leafcare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Payment;
import com.son.leafcare.entity.PaymentStatus;
import com.son.leafcare.entity.Plan;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.repository.AppUserRepository;
import com.son.leafcare.repository.PaymentRepository;
import com.son.leafcare.service.momo.MomoClient;
import com.son.leafcare.service.momo.MomoClient.MomoResult;
import com.son.leafcare.service.momo.MomoException;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({PaymentService.class, SubscriptionService.class})
class PaymentServiceTest {

    @Autowired
    PaymentService service;

    @Autowired
    SubscriptionService subscriptions;

    @Autowired
    PaymentRepository payments;

    @Autowired
    AppUserRepository users;

    @MockitoBean
    MomoClient momo;

    AppUser user;
    Map<String, Object> ipnBody = Map.of("orderId", "LC1");

    @BeforeEach
    void setUp() {
        user = user("u@x.com");
    }

    AppUser user(String email) {
        AppUser u = new AppUser();
        u.setEmail(email);
        return users.save(u);
    }

    Payment pending(String orderId) {
        Payment p = new Payment();
        p.setUser(user);
        p.setOrderId(orderId);
        p.setRequestId(orderId);
        p.setPlan(Plan.PREMIUM);
        p.setAmount(49000);
        return payments.saveAndFlush(p);
    }

    void ipnSays(int resultCode, long amount) {
        when(momo.verifyIpn(ipnBody)).thenReturn(new MomoResult("LC1", amount, resultCode, 99L, "msg"));
    }

    @Test
    void startSavesPendingOrderAtPremiumPrice() {
        when(momo.createPayUrl(anyString(), eq(49000L), anyString())).thenReturn("https://pay.test/x");

        assertThat(service.startPremium(user)).isEqualTo("https://pay.test/x");

        Payment p = payments.findAll().get(0);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(p.getAmount()).isEqualTo(49000);
        assertThat(p.getOrderId()).startsWith("LC");
    }

    @Test
    void ipnWithBadSignatureChangesNothing() {
        pending("LC1");
        when(momo.verifyIpn(ipnBody)).thenReturn(null);

        assertThat(service.handleIpn(ipnBody)).isFalse();
        assertThat(payments.findByOrderId("LC1").orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void successfulIpnActivatesPremium() {
        pending("LC1");
        ipnSays(0, 49000);

        assertThat(service.handleIpn(ipnBody)).isTrue();

        Payment p = payments.findByOrderId("LC1").orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(p.getMomoTransId()).isEqualTo(99L);
        assertThat(p.getPaidAt()).isNotNull();
        assertThat(subscriptions.isPremium(user)).isTrue();
    }

    @Test
    void duplicateIpnExtendsOnlyOnce() {
        pending("LC1");
        ipnSays(0, 49000);
        service.handleIpn(ipnBody);
        LocalDateTime until = subscriptions.premiumUntil(user);

        service.handleIpn(ipnBody);

        assertThat(subscriptions.premiumUntil(user)).isEqualTo(until);
    }

    @Test
    void wrongAmountIsIgnored() {
        pending("LC1");
        ipnSays(0, 1000);

        service.handleIpn(ipnBody);

        assertThat(payments.findByOrderId("LC1").orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(subscriptions.isPremium(user)).isFalse();
    }

    @Test
    void failedResultMarksFailedWithoutPremium() {
        pending("LC1");
        ipnSays(1006, 49000);

        service.handleIpn(ipnBody);

        Payment p = payments.findByOrderId("LC1").orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.getResultCode()).isEqualTo(1006);
        assertThat(subscriptions.isPremium(user)).isFalse();
    }

    @Test
    void stillProcessingStaysPending() {
        pending("LC1");
        ipnSays(7000, 49000);

        service.handleIpn(ipnBody);

        assertThat(payments.findByOrderId("LC1").orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void ipnForUnknownOrderIsAcknowledgedAndIgnored() {
        ipnSays(0, 49000);
        assertThat(service.handleIpn(ipnBody)).isTrue();
        assertThat(subscriptions.isPremium(user)).isFalse();
    }

    @Test
    void refreshAsksMomoWhilePending() {
        pending("LC1");
        when(momo.query("LC1")).thenReturn(new MomoResult("LC1", 49000, 0, 5L, "ok"));

        assertThat(service.refresh("LC1", user).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(subscriptions.isPremium(user)).isTrue();
    }

    @Test
    void refreshOfFinalPaymentDoesNotCallMomo() {
        Payment p = pending("LC1");
        p.setStatus(PaymentStatus.FAILED);
        payments.saveAndFlush(p);

        service.refresh("LC1", user);

        verify(momo, never()).query(any());
    }

    @Test
    void refreshKeepsPendingWhenMomoIsDown() {
        pending("LC1");
        when(momo.query("LC1")).thenThrow(new MomoException("down"));

        assertThat(service.refresh("LC1", user).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void otherUsersOrderIsNotFound() {
        pending("LC1");
        AppUser other = user("b@x.com");
        assertThatThrownBy(() -> service.refresh("LC1", other)).isInstanceOf(NotFoundException.class);
        verify(momo, never()).query(any());
    }

    @Test
    void momoRefusalPropagates() {
        when(momo.createPayUrl(anyString(), anyLong(), anyString())).thenThrow(new MomoException("x"));
        assertThatThrownBy(() -> service.startPremium(user)).isInstanceOf(MomoException.class);
    }
}
