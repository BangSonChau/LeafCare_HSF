package com.son.leafcare.disease;

import static org.assertj.core.api.Assertions.assertThat;

import com.son.leafcare.plant.Plant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class DiseaseRepositoryTest {

    @Autowired
    DiseaseRepository repo;

    @Autowired
    TestEntityManager em;

    @BeforeEach
    void seed() {
        Plant lan = new Plant();
        lan.setName("Lan");
        em.persist(lan);
        List<Disease> all = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            Disease d = new Disease();
            d.setName(String.format("Đốm lá %02d", i));
            d.setPlant(i == 1 ? lan : null);
            all.add(d);
        }
        repo.saveAll(all);
        em.flush();
        em.clear();
    }

    @Test
    void searchIsCaseInsensitiveAndPaged() {
        var page = repo.findByNameContainingIgnoreCase("ĐỐM", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getContent()).hasSize(10);
    }

    @Test
    void lastPageHasRemainder() {
        assertThat(repo.findByNameContainingIgnoreCase("đốm", PageRequest.of(2, 10)).getContent()).hasSize(5);
    }

    @Test
    void plantIsLoadedForListWithoutOpenSession() {
        var d = repo.findByNameContainingIgnoreCase("lá 01", PageRequest.of(0, 10)).getContent().get(0);
        em.clear();
        assertThat(d.getPlant().getName()).isEqualTo("Lan"); // would throw if the entity graph were missing
    }

    @Test
    void findsByModelLabelWithPlant() {
        Disease d = repo.findByNameContainingIgnoreCase("Đốm lá 01", PageRequest.of(0, 1)).getContent().get(0);
        d.setModelLabel("Tomato___Early_blight");
        repo.saveAndFlush(d);
        em.clear();

        Disease found = repo.findByModelLabel("Tomato___Early_blight").orElseThrow();
        assertThat(found.getId()).isEqualTo(d.getId());
        assertThat(found.getPlant().getName()).isEqualTo("Lan");
        assertThat(repo.findByModelLabel("Tomato___healthy")).isEmpty();
    }
}
