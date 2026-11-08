package com.son.leafcare.service;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Payment;
import com.son.leafcare.entity.PaymentStatus;
import com.son.leafcare.entity.Plan;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.repository.PaymentRepository;
import com.son.leafcare.service.momo.MomoClient;
import com.son.leafcare.service.momo.MomoClient.MomoResult;
import com.son.leafcare.service.momo.MomoException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Payment state changes only here, from a signed IPN or MoMo's query API; never from redirect URL params. */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repo;
    private final SubscriptionService subscriptions;
    private final MomoClient momo;

    public PaymentService(PaymentRepository repo, SubscriptionService subscriptions, MomoClient momo) {
        this.repo = repo;
        this.subscriptions = subscriptions;
        this.momo = momo;
    }

    /** Saves a PENDING order and returns MoMo's payUrl. If MoMo refuses, nothing is saved. */
    @Transactional
    public String startPremium(AppUser user) {
        Payment p = new Payment();
        p.setUser(user);
        p.setOrderId("LC" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 10000));
        p.setRequestId(p.getOrderId());
        p.setPlan(Plan.PREMIUM);
        p.setAmount(subscriptions.premiumPrice());
        repo.saveAndFlush(p);
        return momo.createPayUrl(p.getOrderId(), p.getAmount(), "LeafCare Premium " + subscriptions.premiumDays() + " ngay");
    }

    /** IPN from MoMo. False when the signature is wrong. */
    @Transactional
    public boolean handleIpn(Map<String, Object> params) {
        MomoResult r = momo.verifyIpn(params);
        if (r == null) {
            log.warn("IPN bị từ chối: sai chữ ký, orderId={}", params.get("orderId"));
            return false;
        }
        repo.findByOrderId(r.orderId()).ifPresentOrElse(p -> apply(p, r),
                () -> log.warn("IPN cho đơn không tồn tại: {}", r.orderId()));
        return true;
    }

    /** The user's own order; while PENDING, asks MoMo for its status first. Other users' orders are 404. */
    @Transactional
    public Payment refresh(String orderId, AppUser user) {
        Payment p = repo.findByOrderId(orderId)
                .filter(x -> x.getUser().getId().equals(user.getId()))
                .orElseThrow(NotFoundException::new);
        if (p.getStatus() == PaymentStatus.PENDING) {
            try {
                apply(p, momo.query(orderId));
            } catch (MomoException e) {
                log.warn("Không hỏi được MoMo cho đơn {}: {}", orderId, e.getMessage());
            }
        }
        return p;
    }

    private void apply(Payment p, MomoResult r) {
        if (p.getStatus() != PaymentStatus.PENDING || r.pending()) {
            return; // already final (duplicate IPN) or MoMo still processing
        }
        if (r.amount() != p.getAmount()) {
            log.warn("Số tiền MoMo ({}) khác đơn {} ({}), bỏ qua", r.amount(), p.getOrderId(), p.getAmount());
            return;
        }
        p.setResultCode(r.resultCode());
        p.setMomoTransId(r.transId());
        String msg = r.message();
        p.setMessage(msg != null && msg.length() > 255 ? msg.substring(0, 255) : msg);
        if (r.success()) {
            p.setStatus(PaymentStatus.SUCCESS);
            p.setPaidAt(LocalDateTime.now());
            subscriptions.activatePremium(p.getUser());
        } else {
            p.setStatus(PaymentStatus.FAILED);
        }
    }
}
