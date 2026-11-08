package com.son.leafcare.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Payment;
import com.son.leafcare.entity.PaymentStatus;
import com.son.leafcare.entity.Plan;
import com.son.leafcare.entity.Role;
import com.son.leafcare.entity.ScanHistory;
import com.son.leafcare.repository.AppUserRepository;
import com.son.leafcare.repository.PaymentRepository;
import com.son.leafcare.repository.ScanHistoryRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({AdminService.class, SubscriptionService.class})
class AdminServiceTest {

    @Autowired
    AdminService service;

    @Autowired
    AppUserRepository users;

    @Autowired
    PaymentRepository payments;

    @Autowired
    ScanHistoryRepository scans;

    @Autowired
    SubscriptionService subscriptions;

    AppUser a;
    AppUser b;

    @BeforeEach
    void setUp() {
        a = user("a@x.com", "An");
        b = user("b@x.com", "Bình");
    }

    AppUser user(String email, String name) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setName(name);
        u.setRole(Role.USER);
        return users.saveAndFlush(u);
    }

    void scan(AppUser owner, LocalDateTime at) {
        ScanHistory s = new ScanHistory();
        s.setImageFileName("x.png");
        s.setOwner(owner);
        s = scans.saveAndFlush(s);
        s.setCreatedAt(at); // @PrePersist sets now; move it afterwards
        scans.saveAndFlush(s);
    }

    void pay(AppUser u, long amount, PaymentStatus status, LocalDateTime paidAt) {
        Payment p = new Payment();
        p.setUser(u);
        p.setOrderId("LC" + System.nanoTime());
        p.setRequestId(p.getOrderId());
        p.setPlan(Plan.PREMIUM);
        p.setAmount(amount);
        p.setStatus(status);
        p.setPaidAt(paidAt);
        payments.saveAndFlush(p);
    }

    @Test
    void emptyDatabaseGivesZeros() {
        users.deleteAll();
        AdminStats s = service.stats();
        assertThat(s.users()).isZero();
        assertThat(s.revenueTotal()).isZero();
        assertThat(s.scansToday()).isZero();
        assertThat(s.scansByDay()).hasSize(14).allSatisfy(d -> assertThat(d.count()).isZero());
        assertThat(s.latestPayments()).isEmpty();
        assertThat(s.topScanners()).isEmpty();
    }

    @Test
    void countsUsersAndNewOnes() {
        a.setCreatedAt(LocalDateTime.now().minusDays(30));
        users.saveAndFlush(a);
        AdminStats s = service.stats();
        assertThat(s.users()).isEqualTo(2);
        assertThat(s.newUsers7d()).isEqualTo(1);
    }

    @Test
    void countsOnlyActivePremium() {
        subscriptions.activatePremium(a);
        AdminStats s = service.stats();
        assertThat(s.premiumActive()).isEqualTo(1);
    }

    @Test
    void revenueCountsOnlySuccessAndSplitsThisMonth() {
        LocalDateTime now = LocalDateTime.now();
        pay(a, 49000, PaymentStatus.SUCCESS, now);
        pay(a, 49000, PaymentStatus.SUCCESS, now.minusMonths(2));
        pay(b, 99000, PaymentStatus.PENDING, null);
        pay(b, 99000, PaymentStatus.FAILED, null);
        AdminStats s = service.stats();
        assertThat(s.revenueTotal()).isEqualTo(98000);
        assertThat(s.revenueThisMonth()).isEqualTo(49000);
        assertThat(s.latestPayments()).hasSize(4);
        assertThat(s.latestPayments().get(0).getUser().getEmail()).isNotBlank();
    }

    @Test
    void latestPaymentsKeepsFiveNewest() {
        for (int i = 0; i < 7; i++) {
            pay(a, 1000, PaymentStatus.PENDING, null);
        }
        assertThat(service.stats().latestPayments()).hasSize(5);
    }

    @Test
    void scansTodaySevenDaysAndPerDay() {
        LocalDateTime today = ScanHistoryService.startOfVietnamDay().plusMinutes(1);
        scan(a, today);
        scan(a, today);
        scan(b, today.minusDays(3));
        scan(b, today.minusDays(20));
        AdminStats s = service.stats();
        assertThat(s.scansToday()).isEqualTo(2);
        assertThat(s.scans7d()).isEqualTo(3);
        LocalDate vnToday = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        assertThat(s.scansByDay().get(13).day()).isEqualTo(vnToday);
        assertThat(s.scansByDay().get(13).count()).isEqualTo(2);
        assertThat(s.scansByDay().get(10).count()).isEqualTo(1);
        assertThat(s.maxPerDay()).isEqualTo(2);
    }

    @Test
    void topScannersOrderedAndSkipOwnerless() {
        LocalDateTime now = LocalDateTime.now();
        scan(b, now);
        scan(b, now);
        scan(a, now);
        scan(null, now);
        AdminStats s = service.stats();
        assertThat(s.topScanners()).extracting(t -> t.user().getEmail()).containsExactly("b@x.com", "a@x.com");
        assertThat(s.topScanners()).extracting(TopScanner::count).containsExactly(2L, 1L);
    }

    @Test
    void usersSearchByEmailOrNameWithPlanAndScanCount() {
        subscriptions.activatePremium(b);
        scan(b, LocalDateTime.now());
        var page = service.users("bình", 0);
        assertThat(page.getContent()).hasSize(1);
        UserRow row = page.getContent().get(0);
        assertThat(row.user().getEmail()).isEqualTo("b@x.com");
        assertThat(row.premiumUntil()).isNotNull();
        assertThat(row.scans()).isEqualTo(1);

        assertThat(service.users("A@X", 0).getContent()).extracting(r -> r.user().getEmail()).containsExactly("a@x.com");
        UserRow free = service.users("a@x.com", 0).getContent().get(0);
        assertThat(free.premiumUntil()).isNull();
        assertThat(free.scans()).isZero();
    }

    @Test
    void usersBlankQueryListsAllNewestFirstAndClampsPage() {
        assertThat(service.users("  ", 0).getContent()).extracting(r -> r.user().getEmail())
                .containsExactly("b@x.com", "a@x.com");
        assertThat(service.users(null, 99).getNumber()).isZero();
        assertThat(service.users("không-có", 3).getContent()).isEmpty();
    }
}
