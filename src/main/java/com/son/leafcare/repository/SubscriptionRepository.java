package com.son.leafcare.repository;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Plan;
import com.son.leafcare.entity.Subscription;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUser(AppUser user);

    long countByPlanAndExpiresAtAfter(Plan plan, LocalDateTime time);

    List<Subscription> findByUserIn(Collection<AppUser> users);
}
