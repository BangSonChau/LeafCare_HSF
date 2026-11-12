package com.son.leafcare.plant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

@DataJpaTest
@Import(PlantService.class)
class PlantServiceDbTest {

    @Autowired
    PlantService service;

    @Autowired
    PlantRepository repo;

    void seed(int n) {
        List<Plant> all = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Plant p = new Plant();
            p.setName(String.format("Cây %02d", i));
            all.add(p);
        }
        repo.saveAll(all);
    }

    @Test
    void pagePastTheEndClampsToLastPageWithItsRows() {
        seed(25);
        Page<Plant> page = service.search("", 99);
        assertThat(page.getNumber()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Plant::getName)
                .containsExactly("Cây 21", "Cây 22", "Cây 23", "Cây 24", "Cây 25");
    }

    @Test
    void findAllIsSortedByName() {
        seed(3);
        assertThat(service.findAll()).extracting(Plant::getName).containsExactly("Cây 01", "Cây 02", "Cây 03");
    }

    @Test
    void negativePageReturnsFirstPage() {
        seed(25);
        Page<Plant> page = service.search("  ", -4);
        assertThat(page.getNumber()).isZero();
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getContent().get(0).getName()).isEqualTo("Cây 01");
    }

    @Test
    void emptyResultWithLargePageReturnsEmptyFirstPage() {
        seed(3);
        Page<Plant> page = service.search("không có", 5);
        assertThat(page.getTotalPages()).isZero();
        assertThat(page.getNumber()).isZero();
        assertThat(page.getContent()).isEmpty();
    }
}
