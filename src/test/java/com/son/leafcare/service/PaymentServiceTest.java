package com.son.leafcare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Payment;
import com.son.leafcare.entity.PaymentStatus;
import com.son.leafcare.entity.Plan;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.repository.AppUserRepository;
import com.son.leafcare.repository.PaymentRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

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

    AppUser user;

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

    static final String ORDER = "LC17941158639378946";

    /** A SePay webhook body: money in, transfer content as a bank app would write it. */
    Map<String, Object> tx(String content, long amount) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", 92704);
        m.put("gateway", "MBBank");
        m.put("transferType", "in");
        m.put("code", null);
        m.put("content", content);
        m.put("transferAmount", amount);
        return m;
    }

    @Test
    void startSavesPendingOrderAtPremiumPrice() {
        Payment p = service.startPremium(user);

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(p.getAmount()).isEqualTo(49000);
        assertThat(p.getOrderId()).matches("LC\\d{17}");
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    void transferWithOrderInContentActivatesPremium() {
        pending(ORDER);

        service.handleWebhook(tx("MBVCB.123 " + ORDER + " chuyen tien", 49000));

        Payment p = payments.findByOrderId(ORDER).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(p.getTransId()).isEqualTo(92704L);
        assertThat(p.getPaidAt()).isNotNull();
        assertThat(subscriptions.isPremium(user)).isTrue();
    }

    @Test
    void sepayParsedCodeIsUsedFirst() {
        pending(ORDER);
        Map<String, Object> t = tx("noi dung bi ngan hang cat", 49000);
        t.put("code", ORDER);

        service.handleWebhook(t);

        assertThat(subscriptions.isPremium(user)).isTrue();
    }

    @Test
    void lowercaseContentStillMatches() {
        pending(ORDER);
        service.handleWebhook(tx(ORDER.toLowerCase(), 49000));
        assertThat(subscriptions.isPremium(user)).isTrue();
    }

    @Test
    void duplicateWebhookExtendsOnlyOnce() {
        pending(ORDER);
        service.handleWebhook(tx(ORDER, 49000));
        LocalDateTime until = subscriptions.premiumUntil(user);

        service.handleWebhook(tx(ORDER, 49000));

        assertThat(subscriptions.premiumUntil(user)).isEqualTo(until);
    }

    @Test
    void underpaymentKeepsPending() {
        pending(ORDER);

        service.handleWebhook(tx(ORDER, 1000));

        assertThat(payments.findByOrderId(ORDER).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(subscriptions.isPremium(user)).isFalse();
    }

    @Test
    void overpaymentStillPays() {
        pending(ORDER);
        service.handleWebhook(tx(ORDER, 50000));
        assertThat(subscriptions.isPremium(user)).isTrue();
    }

    @Test
    void outgoingMoneyIsIgnored() {
        pending(ORDER);
        Map<String, Object> t = tx(ORDER, 49000);
        t.put("transferType", "out");

        service.handleWebhook(t);

        assertThat(subscriptions.isPremium(user)).isFalse();
    }

    @Test
    void contentWithoutOrderOrUnknownOrderIsIgnored() {
        pending(ORDER);
        service.handleWebhook(tx("tien an trua", 49000));
        service.handleWebhook(tx("LC99999999999999999", 49000));
        assertThat(subscriptions.isPremium(user)).isFalse();
    }

    @Test
    void apiKeyMustMatchExactly() {
        assertThat(service.validKey("Apikey test-key")).isTrue();
        assertThat(service.validKey("Apikey wrong")).isFalse();
        assertThat(service.validKey("test-key")).isFalse();
        assertThat(service.validKey(null)).isFalse();
    }

    @Test
    void ownerCanReadOrder() {
        pending(ORDER);
        assertThat(service.getForUser(ORDER, user).getOrderId()).isEqualTo(ORDER);
    }

    @Test
    void otherUsersOrderIsNotFound() {
        pending(ORDER);
        AppUser other = user("b@x.com");
        assertThatThrownBy(() -> service.getForUser(ORDER, other)).isInstanceOf(NotFoundException.class);
    }
}
