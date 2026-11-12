package com.son.leafcare.scan;

import com.son.leafcare.common.NotFoundException;
import com.son.leafcare.plan.SubscriptionService;
import com.son.leafcare.user.AppUser;
import com.son.leafcare.user.Role;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Scans belong to their owner; ADMIN sees all. Free accounts get FREE_PER_DAY scans per Vietnam day. */
@Service
public class ScanHistoryService {

    static final int PAGE_SIZE = 10;
    public static final int FREE_PER_DAY = 5;
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ScanHistoryRepository repo;
    private final FileStorageService files;
    private final SubscriptionService subscriptions;

    public ScanHistoryService(ScanHistoryRepository repo, FileStorageService files, SubscriptionService subscriptions) {
        this.repo = repo;
        this.files = files;
        this.subscriptions = subscriptions;
    }

    /** Scans left today, or null when the user has no limit (Premium or ADMIN). */
    @Transactional(readOnly = true)
    public Integer remainingToday(AppUser user) {
        if (user.getRole() == Role.ADMIN || subscriptions.isPremium(user)) {
            return null;
        }
        long used = repo.countByOwnerAndCreatedAtGreaterThanEqual(user, startOfVietnamDay());
        return (int) Math.max(FREE_PER_DAY - used, 0);
    }

    /** Checks the Free limit before storing anything, so a blocked scan leaves no file behind. */
    @Transactional
    public ScanHistory create(MultipartFile f, String note, AppUser user) {
        // shortcut: two uploads at the same instant can both pass this check (6th scan), fine for a demo app
        Integer left = remainingToday(user);
        if (left != null && left == 0) {
            throw new ScanLimitException();
        }
        String name = files.store(f);
        ScanHistory s = new ScanHistory();
        s.setImageFileName(name);
        s.setOwner(user);
        String trimmed = note == null ? "" : note.trim();
        s.setNote(trimmed.isEmpty() ? null : trimmed);
        return repo.saveAndFlush(s); // image and row share this transaction, so a failure rolls back both
    }

    @Transactional(readOnly = true)
    public Page<ScanHistory> list(AppUser user, int page) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        Page<ScanHistory> result = find(user, PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort));
        if (result.getNumber() > 0 && result.getNumber() >= result.getTotalPages()) {
            return find(user, PageRequest.of(Math.max(result.getTotalPages() - 1, 0), PAGE_SIZE, sort));
        }
        return result;
    }

    /** The scan, if this user may see it; someone else's scan is 404 like a missing one. */
    @Transactional(readOnly = true)
    public ScanHistory get(Long id, AppUser user) {
        return repo.findById(id)
                .filter(s -> user.getRole() == Role.ADMIN
                        || (s.getOwner() != null && Objects.equals(s.getOwner().getId(), user.getId())))
                .orElseThrow(NotFoundException::new);
    }

    @Transactional
    public void delete(Long id, AppUser user) {
        ScanHistory s = get(id, user);
        repo.delete(s);
        repo.flush();
        files.delete(s.getImageFileName());
    }

    private Page<ScanHistory> find(AppUser user, Pageable p) {
        return user.getRole() == Role.ADMIN ? repo.findAll(p) : repo.findByOwner(user, p);
    }

    /** 00:00 today in Vietnam, in the server's local time (createdAt is stored as server local time). */
    public static LocalDateTime startOfVietnamDay() {
        return ZonedDateTime.now(VN).toLocalDate().atStartOfDay(VN)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
