package com.son.leafcare.plant;

import com.son.leafcare.common.DuplicateNameException;
import com.son.leafcare.common.InUseException;
import com.son.leafcare.common.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlantService {

    static final int PAGE_SIZE = 10;

    private final PlantRepository repo;

    public PlantService(PlantRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Page<Plant> search(String q, int page) {
        String query = q == null ? "" : q.trim();
        Sort sort = Sort.by("name");
        Page<Plant> result = repo.findByNameContainingIgnoreCase(query, PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort));
        if (result.getNumber() > 0 && result.getNumber() >= result.getTotalPages()) {
            return repo.findByNameContainingIgnoreCase(query,
                    PageRequest.of(Math.max(result.getTotalPages() - 1, 0), PAGE_SIZE, sort));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public java.util.List<Plant> findAll() {
        return repo.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public Plant get(Long id) {
        return repo.findById(id).orElseThrow(NotFoundException::new);
    }

    @Transactional
    public Plant save(Plant p) {
        p.setName(p.getName().trim());
        // Derived "id <> null" never matches, so new plants use the plain exists check.
        boolean taken = p.getId() == null
                ? repo.existsByNameIgnoreCase(p.getName())
                : repo.existsByNameIgnoreCaseAndIdNot(p.getName(), p.getId());
        if (taken) {
            throw new DuplicateNameException();
        }
        try {
            return repo.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) { // lost a race against the unique index
            throw new DuplicateNameException();
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException();
        }
        try {
            repo.deleteById(id);
            repo.flush();
        } catch (DataIntegrityViolationException e) {
            throw new InUseException();
        }
    }
}
