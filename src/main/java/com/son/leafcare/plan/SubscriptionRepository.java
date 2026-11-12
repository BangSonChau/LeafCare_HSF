package com.son.leafcare.plan;

import com.son.leafcare.user.AppUser;
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
