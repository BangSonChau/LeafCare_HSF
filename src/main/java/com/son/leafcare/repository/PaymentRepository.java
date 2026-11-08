package com.son.leafcare.repository;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Payment;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Row lock so an IPN and the result-page check cannot both activate Premium for one order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByOrderId(String orderId);

    Page<Payment> findByUser(AppUser user, Pageable pageable);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = com.son.leafcare.entity.PaymentStatus.SUCCESS")
    long sumPaid();

    @Query("select coalesce(sum(p.amount), 0) from Payment p "
            + "where p.status = com.son.leafcare.entity.PaymentStatus.SUCCESS and p.paidAt >= :since")
    long sumPaidSince(LocalDateTime since);

    @EntityGraph(attributePaths = "user")
    List<Payment> findTop5ByOrderByCreatedAtDescIdDesc();
}
