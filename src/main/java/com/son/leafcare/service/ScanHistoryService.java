package com.son.leafcare.service;

import com.son.leafcare.entity.ScanHistory;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.repository.ScanHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ScanHistoryService {

    static final int PAGE_SIZE = 10;

    private final ScanHistoryRepository repo;
    private final FileStorageService files;

    public ScanHistoryService(ScanHistoryRepository repo, FileStorageService files) {
        this.repo = repo;
        this.files = files;
    }

    @Transactional
    public ScanHistory create(MultipartFile f, String note) {
        String name = files.store(f);
        ScanHistory s = new ScanHistory();
        s.setImageFileName(name);
        String trimmed = note == null ? "" : note.trim();
        s.setNote(trimmed.isEmpty() ? null : trimmed);
        try {
            return repo.saveAndFlush(s);
        } catch (RuntimeException e) {
            files.delete(name); // no orphan file when the row fails
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<ScanHistory> list(int page) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        Page<ScanHistory> result = repo.findAll(PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort));
        if (result.getNumber() > 0 && result.getNumber() >= result.getTotalPages()) {
            return repo.findAll(PageRequest.of(Math.max(result.getTotalPages() - 1, 0), PAGE_SIZE, sort));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ScanHistory get(Long id) {
        return repo.findById(id).orElseThrow(NotFoundException::new);
    }

    @Transactional
    public void delete(Long id) {
        ScanHistory s = get(id);
        repo.delete(s);
        repo.flush();
        files.delete(s.getImageFileName()); // row first, then file
    }
}
