package com.son.leafcare.repository;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Subscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUser(AppUser user);
}
