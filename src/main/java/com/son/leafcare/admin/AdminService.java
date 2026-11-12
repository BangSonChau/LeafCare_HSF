package com.son.leafcare.admin;

import com.son.leafcare.payment.PaymentRepository;
import com.son.leafcare.plan.Plan;
import com.son.leafcare.plan.Subscription;
import com.son.leafcare.plan.SubscriptionRepository;
import com.son.leafcare.scan.ScanHistoryRepository;
import com.son.leafcare.scan.ScanHistoryService;
import com.son.leafcare.user.AppUser;
import com.son.leafcare.user.AppUserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only numbers and lists for the ADMIN pages. */
@Service
public class AdminService {

    static final int CHART_DAYS = 14;
    static final int USERS_PER_PAGE = 20;
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AppUserRepository users;
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final ScanHistoryRepository scans;

    public AdminService(AppUserRepository users, SubscriptionRepository subscriptions,
                        PaymentRepository payments, ScanHistoryRepository scans) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.scans = scans;
    }

    @Transactional(readOnly = true)
    public AdminStats stats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = ScanHistoryService.startOfVietnamDay();
        LocalDateTime monthStart = toServer(LocalDate.now(VN).withDayOfMonth(1));
        LocalDateTime chartStart = today.minusDays(CHART_DAYS - 1);

        return new AdminStats(
                users.count(),
                users.countByCreatedAtGreaterThanEqual(now.minusDays(7)),
                subscriptions.countByPlanAndExpiresAtAfter(Plan.PREMIUM, now),
                payments.sumPaid(),
                payments.sumPaidSince(monthStart),
                scans.countByCreatedAtGreaterThanEqual(today),
                scans.countByCreatedAtGreaterThanEqual(today.minusDays(6)),
                perDay(scans.findCreatedAtSince(chartStart)),
                payments.findTop5ByOrderByCreatedAtDescIdDesc(),
                topScanners());
    }

    /** Users whose email or name contains q (any case), newest first; a page past the end shows the last page. */
    @Transactional(readOnly = true)
    public Page<UserRow> users(String q, int page) {
        String term = q == null ? "" : q.trim();
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Page<AppUser> result = find(term, PageRequest.of(Math.max(page, 0), USERS_PER_PAGE, sort));
        if (result.getNumber() > 0 && result.getNumber() >= result.getTotalPages()) {
            result = find(term, PageRequest.of(Math.max(result.getTotalPages() - 1, 0), USERS_PER_PAGE, sort));
        }
        List<AppUser> list = result.getContent();
        LocalDateTime now = LocalDateTime.now();
        Map<Long, LocalDateTime> premium = subscriptions.findByUserIn(list).stream()
                .filter(s -> s.isPremiumAt(now))
                .collect(Collectors.toMap(s -> s.getUser().getId(), Subscription::getExpiresAt));
        Map<Long, Long> counts = list.isEmpty() ? Map.of() : scans.countByOwners(list).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return result.map(u -> new UserRow(u, premium.get(u.getId()), counts.getOrDefault(u.getId(), 0L)));
    }

    private Page<AppUser> find(String term, Pageable p) {
        return term.isEmpty() ? users.findAll(p)
                : users.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(term, term, p);
    }

    /** Buckets scan times (server clock) into the last CHART_DAYS Vietnam days, oldest first. */
    private List<DayCount> perDay(List<LocalDateTime> times) {
        Map<LocalDate, Long> byDay = times.stream().collect(Collectors.groupingBy(
                t -> t.atZone(ZoneId.systemDefault()).withZoneSameInstant(VN).toLocalDate(), Collectors.counting()));
        LocalDate last = LocalDate.now(VN);
        List<DayCount> days = new ArrayList<>();
        for (int i = CHART_DAYS - 1; i >= 0; i--) {
            LocalDate d = last.minusDays(i);
            days.add(new DayCount(d, byDay.getOrDefault(d, 0L)));
        }
        return days;
    }

    private List<TopScanner> topScanners() {
        List<Object[]> rows = scans.topOwnerIds(PageRequest.of(0, 5));
        Map<Long, AppUser> byId = users.findAllById(rows.stream().map(r -> (Long) r[0]).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return rows.stream().map(r -> new TopScanner(byId.get((Long) r[0]), (Long) r[1])).toList();
    }

    private static LocalDateTime toServer(LocalDate vnDay) {
        return vnDay.atStartOfDay(VN).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
