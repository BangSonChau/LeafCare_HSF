package com.son.leafcare.payment;

import com.son.leafcare.common.NotFoundException;
import com.son.leafcare.plan.Plan;
import com.son.leafcare.plan.SubscriptionService;
import com.son.leafcare.user.AppUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Payment state changes only here, from SePay's webhook (API key checked); never from the browser. */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    static final int PAGE_SIZE = 10;
    private static final Pattern ORDER_ID = Pattern.compile("LC\\d{17}");

    private final PaymentRepository repo;
    private final SubscriptionService subscriptions;
    private final String apiKey;

    public PaymentService(PaymentRepository repo, SubscriptionService subscriptions,
                          @Value("${sepay.api-key}") String apiKey) {
        this.repo = repo;
        this.subscriptions = subscriptions;
        this.apiKey = apiKey;
    }

    /** Saves a PENDING order; the buyer then transfers money with the orderId as transfer content. */
    @Transactional
    public Payment startPremium(AppUser user) {
        Payment p = new Payment();
        p.setUser(user);
        p.setOrderId("LC" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 10000));
        p.setRequestId(p.getOrderId());
        p.setPlan(Plan.PREMIUM);
        p.setAmount(subscriptions.premiumPrice());
        return repo.saveAndFlush(p);
    }

    /** Is this the "Authorization: Apikey ..." header SePay was configured with? */
    public boolean validKey(String authorization) {
        String expected = "Apikey " + apiKey;
        return authorization != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A bank transaction from SePay (key already checked). Pays the order named in the transfer content when
     * the money is incoming and at least the order amount. Safe to receive twice: only PENDING orders change.
     */
    @Transactional
    public void handleWebhook(Map<String, Object> tx) {
        if (!"in".equals(tx.get("transferType"))) {
            return;
        }
        String orderId = findOrderId(tx.get("code"), tx.get("content"));
        if (orderId == null) {
            log.warn("SePay: không thấy mã đơn trong nội dung '{}'", tx.get("content"));
            return;
        }
        Payment p = repo.findByOrderId(orderId).orElse(null);
        if (p == null || p.getStatus() != PaymentStatus.PENDING) {
            log.warn("SePay: đơn {} không tồn tại hoặc đã xử lý, bỏ qua giao dịch {}", orderId, tx.get("id"));
            return;
        }
        long amount = ((Number) tx.getOrDefault("transferAmount", 0)).longValue();
        if (amount < p.getAmount()) {
            log.warn("SePay: đơn {} cần {} nhưng chỉ nhận {}, giữ PENDING", orderId, p.getAmount(), amount);
            return;
        }
        p.setTransId(tx.get("id") instanceof Number n ? n.longValue() : null);
        p.setMessage("Đã nhận chuyển khoản qua " + tx.getOrDefault("gateway", "ngân hàng"));
        p.setStatus(PaymentStatus.SUCCESS);
        p.setPaidAt(LocalDateTime.now());
        subscriptions.activatePremium(p.getUser());
    }

    /** The user's own order; other users' orders are 404. */
    @Transactional // findByOrderId locks the row (FOR UPDATE), which a read-only transaction refuses on PostgreSQL
    public Payment getForUser(String orderId, AppUser user) {
        return repo.findByOrderId(orderId)
                .filter(x -> x.getUser().getId().equals(user.getId()))
                .orElseThrow(NotFoundException::new);
    }

    /** The user's own orders, newest first, 10 per page; a page past the end shows the last page. */
    @Transactional(readOnly = true)
    public Page<Payment> history(AppUser user, int page) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Page<Payment> result = repo.findByUser(user, PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort));
        if (result.getNumber() > 0 && result.getNumber() >= result.getTotalPages()) {
            return repo.findByUser(user, PageRequest.of(Math.max(result.getTotalPages() - 1, 0), PAGE_SIZE, sort));
        }
        return result;
    }

    /** SePay's parsed code when its pattern is configured, else our own pattern in the raw content. */
    static String findOrderId(Object code, Object content) {
        for (Object o : new Object[] {code, content}) {
            if (o != null) {
                Matcher m = ORDER_ID.matcher(o.toString().toUpperCase());
                if (m.find()) {
                    return m.group();
                }
            }
        }
        return null;
    }
}
