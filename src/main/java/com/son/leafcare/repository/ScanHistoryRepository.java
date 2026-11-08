package com.son.leafcare.repository;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.ScanHistory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanHistoryRepository extends JpaRepository<ScanHistory, Long> {

    List<ScanHistory> findTop5ByOrderByCreatedAtDesc();

    /** ADMIN's list shows who scanned each row; load owners in the same query. */
    @Override
    @EntityGraph(attributePaths = "owner")
    Page<ScanHistory> findAll(Pageable pageable);

    List<ScanHistory> findTop5ByOwnerOrderByCreatedAtDesc(AppUser owner);

    Page<ScanHistory> findByOwner(AppUser owner, Pageable pageable);

    long countByOwner(AppUser owner);

    long countByOwnerAndCreatedAtGreaterThanEqual(AppUser owner, LocalDateTime since);
}
