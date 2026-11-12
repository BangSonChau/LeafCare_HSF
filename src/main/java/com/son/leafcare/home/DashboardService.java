package com.son.leafcare.home;

import com.son.leafcare.disease.DiseaseRepository;
import com.son.leafcare.plant.PlantRepository;
import com.son.leafcare.scan.ScanHistoryRepository;
import com.son.leafcare.user.AppUser;
import com.son.leafcare.user.Role;
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
