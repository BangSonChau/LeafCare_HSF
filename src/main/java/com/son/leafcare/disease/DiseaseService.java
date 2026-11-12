package com.son.leafcare.disease;

import com.son.leafcare.common.NotFoundException;
import com.son.leafcare.plant.PlantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiseaseService {

    static final int PAGE_SIZE = 10;

    private final DiseaseRepository repo;
    private final PlantRepository plants;

    public DiseaseService(DiseaseRepository repo, PlantRepository plants) {
        this.repo = repo;
        this.plants = plants;
    }

    @Transactional(readOnly = true)
    public Page<Disease> search(String q, int page) {
        String query = q == null ? "" : q.trim();
        Sort sort = Sort.by("name");
        Page<Disease> result = repo.findByNameContainingIgnoreCase(query, PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort));
        if (result.getNumber() > 0 && result.getNumber() >= result.getTotalPages()) {
            return repo.findByNameContainingIgnoreCase(query,
                    PageRequest.of(Math.max(result.getTotalPages() - 1, 0), PAGE_SIZE, sort));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Disease get(Long id) {
        return repo.findById(id).orElseThrow(NotFoundException::new);
    }

    @Transactional
    public Disease save(Disease d) {
        d.setName(d.getName().trim());
        // An empty select binds to a Plant with null id; unknown id -> 404 rather than a FK error.
        if (d.getPlant() != null) {
            d.setPlant(d.getPlant().getId() == null ? null
                    : plants.findById(d.getPlant().getId()).orElseThrow(NotFoundException::new));
        }
        return repo.saveAndFlush(d);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException();
        }
        repo.deleteById(id);
    }
}
