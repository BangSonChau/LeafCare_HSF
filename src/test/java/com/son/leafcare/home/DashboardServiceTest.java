package com.son.leafcare.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.son.leafcare.disease.DiseaseRepository;
import com.son.leafcare.plant.PlantRepository;
import com.son.leafcare.scan.ScanHistory;
import com.son.leafcare.scan.ScanHistoryRepository;
import com.son.leafcare.user.AppUser;
import com.son.leafcare.user.Role;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    @Test
    void statsCombinesCountsAndRecentScans() {
        PlantRepository p = mock(PlantRepository.class);
        DiseaseRepository d = mock(DiseaseRepository.class);
        ScanHistoryRepository s = mock(ScanHistoryRepository.class);
        List<ScanHistory> recent = List.of(new ScanHistory());
        when(p.count()).thenReturn(3L);
        when(d.count()).thenReturn(2L);
        when(s.count()).thenReturn(7L);
        when(s.findTop5ByOrderByCreatedAtDesc()).thenReturn(recent);

        AppUser admin = new AppUser();
        admin.setRole(Role.ADMIN);

        DashboardStats st = new DashboardService(p, d, s).stats(admin);

        assertThat(st).isEqualTo(new DashboardStats(3, 2, 7, recent));
    }

    @Test
    void userSeesOwnScanNumbers() {
        PlantRepository p = mock(PlantRepository.class);
        DiseaseRepository d = mock(DiseaseRepository.class);
        ScanHistoryRepository s = mock(ScanHistoryRepository.class);
        AppUser user = new AppUser();
        List<ScanHistory> mine = List.of(new ScanHistory());
        when(p.count()).thenReturn(3L);
        when(d.count()).thenReturn(2L);
        when(s.count()).thenReturn(70L);
        when(s.countByOwner(user)).thenReturn(4L);
        when(s.findTop5ByOwnerOrderByCreatedAtDesc(user)).thenReturn(mine);

        assertThat(new DashboardService(p, d, s).stats(user)).isEqualTo(new DashboardStats(3, 2, 4, mine));
    }
}
