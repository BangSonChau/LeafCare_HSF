package com.son.leafcare.repository;

import com.son.leafcare.entity.Payment;
import jakarta.persistence.LockModeType;
import com.son.leafcare.entity.AppUser;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Row lock so an IPN and the result-page check cannot both activate Premium for one order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByOrderId(String orderId);

    Page<Payment> findByUser(AppUser user, Pageable pageable);
}
