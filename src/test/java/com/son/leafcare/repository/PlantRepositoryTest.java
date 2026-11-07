package com.son.leafcare.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.son.leafcare.entity.Plant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class PlantRepositoryTest {

    @Autowired
    PlantRepository repo;

    @BeforeEach
    void seed() {
        List<Plant> all = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            Plant p = new Plant();
            p.setName(String.format("Cây %02d", i));
            all.add(p);
        }
        Plant lan = new Plant();
        lan.setName("Lan 100%");
        all.add(lan);
        repo.saveAll(all);
    }

    @Test
    void searchIsCaseInsensitiveAndPaged() {
        var page = repo.findByNameContainingIgnoreCase("cây", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getContent()).hasSize(10);
    }

    @Test
    void lastPageHasRemainder() {
        assertThat(repo.findByNameContainingIgnoreCase("cây", PageRequest.of(2, 10)).getContent()).hasSize(5);
    }

    @Test
    void percentIsLiteral() {
        var page = repo.findByNameContainingIgnoreCase("%", PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Plant::getName).containsExactly("Lan 100%");
    }

    @Test
    void underscoreIsLiteral() {
        assertThat(repo.findByNameContainingIgnoreCase("_", PageRequest.of(0, 10)).getTotalElements()).isZero();
    }

    @Test
    void emptyQueryMatchesAll() {
        assertThat(repo.findByNameContainingIgnoreCase("", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(26);
    }

    @Test
    void existsByNameIgnoreCase() {
        assertThat(repo.existsByNameIgnoreCase("LAN 100%")).isTrue();
        assertThat(repo.existsByNameIgnoreCase("Không có")).isFalse();
    }

    @Test
    void existsByNameIgnoreCaseAndIdNotToleratesCaseTwins() {
        Plant a = new Plant();
        a.setName("Lan");
        Plant b = new Plant();
        b.setName("LAN");
        repo.saveAll(List.of(a, b));
        assertThat(repo.existsByNameIgnoreCaseAndIdNot("lan", a.getId())).isTrue();
        assertThat(repo.existsByNameIgnoreCaseAndIdNot("Cây 01", 0L)).isTrue();
        assertThat(repo.existsByNameIgnoreCaseAndIdNot("Cây 01", repo.findAll().stream()
                .filter(p -> p.getName().equals("Cây 01")).findFirst().get().getId())).isFalse();
    }
}
