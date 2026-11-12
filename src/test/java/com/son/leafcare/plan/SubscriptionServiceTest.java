package com.son.leafcare.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.son.leafcare.user.AppUser;
import com.son.leafcare.user.AppUserRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(SubscriptionService.class)
class SubscriptionServiceTest {

    @Autowired
    SubscriptionService service;

    @Autowired
    SubscriptionRepository subs;

    @Autowired
    AppUserRepository users;

    AppUser user;

    @BeforeEach
    void user() {
        AppUser u = new AppUser();
        u.setEmail("u@x.com");
        user = users.save(u);
    }

    void existing(LocalDateTime expiresAt) {
        Subscription s = new Subscription();
        s.setUser(user);
        s.setPlan(Plan.PREMIUM);
        s.setStartedAt(LocalDateTime.now().minusDays(40));
        s.setExpiresAt(expiresAt);
        subs.save(s);
    }

    @Test
    void newUserIsFree() {
        assertThat(service.isPremium(user)).isFalse();
        assertThat(service.premiumUntil(user)).isNull();
    }

    @Test
    void activateGivesThirtyDays() {
        service.activatePremium(user);
        assertThat(service.isPremium(user)).isTrue();
        assertThat(service.premiumUntil(user)).isCloseTo(LocalDateTime.now().plusDays(30), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void renewWhileActiveStacksOnEndDate() {
        LocalDateTime end = LocalDateTime.now().plusDays(10);
        existing(end);
        Subscription s = service.activatePremium(user);
        assertThat(s.getExpiresAt()).isEqualTo(end.plusDays(30));
        assertThat(subs.count()).isEqualTo(1);
    }

    @Test
    void expiredIsFreeAndRenewStartsFromNow() {
        existing(LocalDateTime.now().minusDays(1));
        assertThat(service.isPremium(user)).isFalse();
        assertThat(service.premiumUntil(user)).isNull();

        Subscription s = service.activatePremium(user);
        assertThat(s.getExpiresAt()).isCloseTo(LocalDateTime.now().plusDays(30), within(1, ChronoUnit.MINUTES));
        assertThat(s.getStartedAt()).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void priceAndDaysComeFromProperties() {
        assertThat(service.premiumPrice()).isEqualTo(49000);
        assertThat(service.premiumDays()).isEqualTo(30);
    }
}
