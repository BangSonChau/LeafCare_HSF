package com.son.leafcare.repository;

import com.son.leafcare.entity.Disease;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiseaseRepository extends JpaRepository<Disease, Long> {

    @EntityGraph(attributePaths = "plant")
    Page<Disease> findByNameContainingIgnoreCase(String q, Pageable p);
}
