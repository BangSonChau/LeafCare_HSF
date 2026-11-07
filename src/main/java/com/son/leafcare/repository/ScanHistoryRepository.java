package com.son.leafcare.repository;

import com.son.leafcare.entity.ScanHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanHistoryRepository extends JpaRepository<ScanHistory, Long> {

    List<ScanHistory> findTop5ByOrderByCreatedAtDesc();
}
