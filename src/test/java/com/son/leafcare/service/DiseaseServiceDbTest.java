package com.son.leafcare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.son.leafcare.entity.Disease;
import com.son.leafcare.entity.Plant;
import com.son.leafcare.exception.InUseException;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.repository.DiseaseRepository;
import com.son.leafcare.repository.PlantRepository;
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
}
