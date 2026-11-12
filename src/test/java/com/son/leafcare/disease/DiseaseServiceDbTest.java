package com.son.leafcare.disease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.son.leafcare.common.InUseException;
import com.son.leafcare.common.NotFoundException;
import com.son.leafcare.plant.Plant;
import com.son.leafcare.plant.PlantRepository;
import com.son.leafcare.plant.PlantService;
import com.son.leafcare.scan.ScanHistory;
import com.son.leafcare.scan.ScanHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({DiseaseService.class, PlantService.class})
class DiseaseServiceDbTest {

    @Autowired
    DiseaseService service;

    @Autowired
    PlantService plantService;

    @Autowired
    DiseaseRepository repo;

    @Autowired
    PlantRepository plants;

    @Autowired
    TestEntityManager em;

    @Autowired
    ScanHistoryRepository scans;

    Plant plant(String name) {
        Plant p = new Plant();
        p.setName(name);
        return plants.save(p);
    }

    Disease disease(String name, Plant bound) {
        Disease d = new Disease();
        d.setName(name);
        d.setPlant(bound);
        return d;
    }

    @Test
    void emptyPlantSelectIsStoredAsNull() {
        Plant blank = new Plant(); // what binding produces for plant.id=""
        Disease saved = service.save(disease("  Đốm lá ", blank));
        em.flush();
        em.clear();
        Disease reloaded = repo.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPlant()).isNull();
        assertThat(reloaded.getName()).isEqualTo("Đốm lá");
    }

    @Test
    void selectedPlantIsLinked() {
        Plant lan = plant("Lan");
        Plant ref = new Plant();
        ref.setId(lan.getId());
        Disease saved = service.save(disease("Thối rễ", ref));
        em.flush();
        em.clear();
        assertThat(repo.findById(saved.getId()).orElseThrow().getPlant().getId()).isEqualTo(lan.getId());
    }

    @Test
    void unknownPlantIdIsNotFound() {
        Plant ghost = new Plant();
        ghost.setId(999_999L);
        assertThatThrownBy(() -> service.save(disease("X", ghost))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void duplicateNamesAreAllowed() {
        service.save(disease("Đốm lá", null));
        service.save(disease("Đốm lá", null));
        assertThat(repo.count()).isEqualTo(2);
    }

    @Test
    void deleteRemovesDisease() {
        Disease d = service.save(disease("Đốm lá", null));
        service.delete(d.getId());
        assertThat(repo.existsById(d.getId())).isFalse();
    }

    @Test
    void deleteUnknownIsNotFound() {
        assertThatThrownBy(() -> service.delete(404L)).isInstanceOf(NotFoundException.class);
    }

    // No surrounding test transaction: PlantService.delete must roll back on its own, as in production.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletingPlantReferencedByDiseaseThrowsInUseAndKeepsRow() {
        Plant lan = plant("Lan");
        Disease d = service.save(disease("Thối rễ", lan));
        try {
            assertThatThrownBy(() -> plantService.delete(lan.getId())).isInstanceOf(InUseException.class);
            assertThat(plants.existsById(lan.getId())).isTrue();
            assertThat(repo.existsById(d.getId())).isTrue();
        } finally {
            repo.deleteAll();
            plants.deleteAll();
        }
    }

    @Test
    void editingKeepsTheModelLabel() {
        Disease d = disease("Đốm vòng", null);
        d.setModelLabel("Tomato___Early_blight");
        Long id = repo.saveAndFlush(d).getId();
        em.clear();

        Disease fromForm = disease("Bệnh đốm vòng", null); // the form has no model label field
        fromForm.setId(id);
        service.save(fromForm);
        em.flush();
        em.clear();

        Disease reloaded = repo.findById(id).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Bệnh đốm vòng");
        assertThat(reloaded.getModelLabel()).isEqualTo("Tomato___Early_blight");
    }

    @Test
    void newDiseaseCannotSetAModelLabelFromTheForm() {
        Disease fromForm = disease("Bệnh lạ", null);
        fromForm.setModelLabel("Tomato___Early_blight");
        assertThat(service.save(fromForm).getModelLabel()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletingDiseaseUsedByAScanThrowsInUseAndKeepsRow() {
        Disease d = service.save(disease("Đốm lá", null));
        ScanHistory s = new ScanHistory();
        s.setImageFileName("a.png");
        s.setDisease(d);
        scans.saveAndFlush(s);
        try {
            assertThatThrownBy(() -> service.delete(d.getId())).isInstanceOf(InUseException.class);
            assertThat(repo.existsById(d.getId())).isTrue();
        } finally {
            scans.deleteAll();
            repo.deleteAll();
        }
    }
}
