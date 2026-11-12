package com.son.leafcare.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.son.leafcare.common.NotFoundException;
import com.son.leafcare.plan.Plan;
import com.son.leafcare.plan.SubscriptionService;
import com.son.leafcare.user.AppUser;
import com.son.leafcare.user.AppUserRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void historyShowsOnlyOwnOrdersNewestFirst() {
        Payment older = pending("LC00000000000000001");
        Payment newer = pending("LC00000000000000002");
        ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.of(2026, 11, 1, 8, 0));
        ReflectionTestUtils.setField(newer, "createdAt", LocalDateTime.of(2026, 11, 2, 8, 0));
        payments.saveAllAndFlush(List.of(older, newer));
        AppUser other = user("b@x.com");
        Payment theirs = pending("LC00000000000000003");
        theirs.setUser(other);
        payments.saveAndFlush(theirs);

        assertThat(service.history(user, 0).getContent()).extracting(Payment::getOrderId)
                .containsExactly("LC00000000000000002", "LC00000000000000001");
        assertThat(service.history(other, 0).getContent()).extracting(Payment::getOrderId)
                .containsExactly("LC00000000000000003");
    }

    @Test
    void historyPagesTenAndClampsTooLargePage() {
        for (int i = 0; i < 12; i++) {
            pending("LC%017d".formatted(i));
        }

        assertThat(service.history(user, 0).getContent()).hasSize(10);
        assertThat(service.history(user, 0).getTotalPages()).isEqualTo(2);
        assertThat(service.history(user, 99).getNumber()).isEqualTo(1);
        assertThat(service.history(user, -3).getNumber()).isZero();
    }

    @Test
    void emptyHistory() {
        assertThat(service.history(user, 5).getContent()).isEmpty();
    }
}
