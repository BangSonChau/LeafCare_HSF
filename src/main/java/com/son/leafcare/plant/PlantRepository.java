package com.son.leafcare.plant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantRepository extends JpaRepository<Plant, Long> {

    // Containing escapes % and _ for us, so they match literally.
    Page<Plant> findByNameContainingIgnoreCase(String q, Pageable p);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Plants the scan model can recognise, for the scan form. */
    List<Plant> findByModelPrefixIsNotNullOrderByName();

    Optional<Plant> findByModelPrefix(String modelPrefix);
}
