package com.son.leafcare.service;

import com.son.leafcare.repository.DiseaseRepository;
import com.son.leafcare.repository.PlantRepository;
import com.son.leafcare.repository.ScanHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final PlantRepository plants;
    private final DiseaseRepository diseases;
    private final ScanHistoryRepository scans;

    public DashboardService(PlantRepository plants, DiseaseRepository diseases, ScanHistoryRepository scans) {
        this.plants = plants;
        this.diseases = diseases;
        this.scans = scans;
    }

    @Transactional(readOnly = true)
    public DashboardStats stats() {
        return new DashboardStats(plants.count(), diseases.count(), scans.count(), scans.findTop5ByOrderByCreatedAtDesc());
    }
}
