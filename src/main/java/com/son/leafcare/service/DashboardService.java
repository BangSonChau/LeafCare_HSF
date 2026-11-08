package com.son.leafcare.service;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Role;
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

    /** Plant and disease counts are shared; scan numbers are the user's own (ADMIN: everyone's). */
    @Transactional(readOnly = true)
    public DashboardStats stats(AppUser user) {
        if (user.getRole() == Role.ADMIN) {
            return new DashboardStats(plants.count(), diseases.count(), scans.count(), scans.findTop5ByOrderByCreatedAtDesc());
        }
        return new DashboardStats(plants.count(), diseases.count(), scans.countByOwner(user),
                scans.findTop5ByOwnerOrderByCreatedAtDesc(user));
    }
}
