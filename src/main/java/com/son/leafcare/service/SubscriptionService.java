package com.son.leafcare.service;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Plan;
import com.son.leafcare.entity.Subscription;
import com.son.leafcare.repository.SubscriptionRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository repo;
    private final long premiumPrice;
    private final int premiumDays;

    public SubscriptionService(SubscriptionRepository repo,
                               @Value("${leafcare.premium.price:49000}") long premiumPrice,
                               @Value("${leafcare.premium.days:30}") int premiumDays) {
        this.repo = repo;
        this.premiumPrice = premiumPrice;
        this.premiumDays = premiumDays;
    }

    public long premiumPrice() {
        return premiumPrice;
    }

    public int premiumDays() {
        return premiumDays;
    }

    @Transactional(readOnly = true)
    public boolean isPremium(AppUser user) {
        return repo.findByUser(user).map(s -> s.isPremiumAt(LocalDateTime.now())).orElse(false);
    }

    /** End of the current Premium period, or null when the user is Free. */
    @Transactional(readOnly = true)
    public LocalDateTime premiumUntil(AppUser user) {
        return repo.findByUser(user).filter(s -> s.isPremiumAt(LocalDateTime.now()))
                .map(Subscription::getExpiresAt).orElse(null);
    }

    /** Adds premiumDays; while still Premium the days stack on the current end date. */
    @Transactional
    public Subscription activatePremium(AppUser user) {
        LocalDateTime now = LocalDateTime.now();
        Subscription s = repo.findByUser(user).orElseGet(() -> {
            Subscription n = new Subscription();
            n.setUser(user);
            return n;
        });
        boolean active = s.isPremiumAt(now);
        if (!active) {
            s.setStartedAt(now);
        }
        s.setPlan(Plan.PREMIUM);
        s.setExpiresAt((active ? s.getExpiresAt() : now).plusDays(premiumDays));
        return repo.save(s);
    }
}
