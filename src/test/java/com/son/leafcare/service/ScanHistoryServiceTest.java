package com.son.leafcare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Role;
import com.son.leafcare.entity.ScanHistory;
import com.son.leafcare.entity.ScanStatus;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.exception.ScanLimitException;
import com.son.leafcare.repository.AppUserRepository;
import com.son.leafcare.repository.ScanHistoryRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@Import({ScanHistoryService.class, FileStorageService.class, SubscriptionService.class})
class ScanHistoryServiceTest {

    @TempDir
    static Path dir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("leafcare.upload-dir", () -> dir.toString());
    }

    @Autowired
    ScanHistoryService service;

    @Autowired
    ScanHistoryRepository repo;

    @Autowired
    AppUserRepository users;

    @Autowired
    SubscriptionService subscriptions;

    AppUser user;
    AppUser other;
    AppUser admin;

    @BeforeEach
    void setUp() {
        user = save("u@x.com", Role.USER);
        other = save("o@x.com", Role.USER);
        admin = save("a@x.com", Role.ADMIN);
    }

    AppUser save(String email, Role role) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setRole(role);
        return users.save(u);
    }

    long files() throws IOException {
        try (var s = Files.list(dir)) {
            return s.count();
        }
    }

    MockMultipartFile img() {
        return new MockMultipartFile("file", "leaf.png", "image/png", new byte[] {1, 2});
    }

    @Test
    void createSetsPendingAndTimestamp() {
        ScanHistory s = service.create(img(), "  note  ", user);
        assertThat(s.getStatus()).isEqualTo(ScanStatus.PENDING);
        assertThat(s.getCreatedAt()).isNotNull();
        assertThat(s.getNote()).isEqualTo("note");
        assertThat(dir.resolve(s.getImageFileName())).exists();
    }

    @Test
    void blankNoteBecomesNull() {
        assertThat(service.create(img(), "   ", user).getNote()).isNull();
    }

    @Test
    void deleteRemovesRowAndFile() {
        ScanHistory s = service.create(img(), null, user);
        service.delete(s.getId(), user);
        assertThat(repo.findById(s.getId())).isEmpty();
        assertThat(dir.resolve(s.getImageFileName())).doesNotExist();
    }

    @Test
    void deleteWithMissingFileStillRemovesRow() throws IOException {
        ScanHistory s = service.create(img(), null, user);
        Files.delete(dir.resolve(s.getImageFileName()));
        service.delete(s.getId(), user);
        assertThat(repo.findById(s.getId())).isEmpty();
    }

    @Test
    void unknownIdIs404() {
        assertThatThrownBy(() -> service.delete(999L, user)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(999L, user)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listClampsToLastPage() {
        for (int i = 0; i < 11; i++) {
            service.create(img(), "n" + i, admin);
        }
        var p = service.list(admin, 99);
        assertThat(p.getNumber()).isEqualTo(1);
        assertThat(p.getContent()).hasSize(1);
        assertThat(service.list(admin, -3).getNumber()).isZero();
    }

    @Test
    void listOnEmptyIsPageZero() {
        assertThat(service.list(user, 5).getNumber()).isZero();
    }

    @Test
    void createRecordsOwner() {
        assertThat(service.create(img(), null, user).getOwner()).isEqualTo(user);
    }

    @Test
    void freeUserGetsFiveScansThenSixthIsBlockedWithoutWritingAFile() throws IOException {
        for (int i = 0; i < 5; i++) {
            service.create(img(), null, user);
        }
        assertThat(service.remainingToday(user)).isZero();
        long before = files();

        assertThatThrownBy(() -> service.create(img(), null, user)).isInstanceOf(ScanLimitException.class);

        assertThat(files()).isEqualTo(before);
        assertThat(repo.countByOwner(user)).isEqualTo(5);
    }

    @Test
    void remainingCountsDown() {
        assertThat(service.remainingToday(user)).isEqualTo(5);
        service.create(img(), null, user);
        assertThat(service.remainingToday(user)).isEqualTo(4);
    }

    @Test
    void yesterdaysScansDoNotCount() {
        for (int i = 0; i < 5; i++) {
            ScanHistory s = service.create(img(), null, user);
            s.setCreatedAt(LocalDateTime.now().minusDays(1));
            repo.saveAndFlush(s);
        }
        assertThat(service.remainingToday(user)).isEqualTo(5);
        service.create(img(), null, user);
    }

    @Test
    void otherUsersScansDoNotUseMyLimit() {
        for (int i = 0; i < 5; i++) {
            service.create(img(), null, other);
        }
        assertThat(service.remainingToday(user)).isEqualTo(5);
    }

    @Test
    void premiumAndAdminAreUnlimited() {
        subscriptions.activatePremium(user);
        for (int i = 0; i < 6; i++) {
            service.create(img(), null, user);
            service.create(img(), null, admin);
        }
        assertThat(service.remainingToday(user)).isNull();
        assertThat(service.remainingToday(admin)).isNull();
    }

    @Test
    void userSeesOnlyOwnScansAdminSeesAllIncludingOwnerless() {
        ScanHistory mine = service.create(img(), null, user);
        ScanHistory theirs = service.create(img(), null, other);
        ScanHistory old = new ScanHistory();
        old.setImageFileName("old.png");
        old = repo.saveAndFlush(old);

        assertThat(service.list(user, 0).getContent()).containsExactly(mine);
        assertThat(service.list(admin, 0).getContent()).hasSize(3);
        assertThat(service.get(mine.getId(), user)).isEqualTo(mine);
        Long theirsId = theirs.getId();
        Long oldId = old.getId();
        assertThatThrownBy(() -> service.get(theirsId, user)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(oldId, user)).isInstanceOf(NotFoundException.class);
        assertThat(service.get(oldId, admin).getId()).isEqualTo(oldId);
    }

    @Test
    void userCannotDeleteSomeoneElsesScan() {
        ScanHistory theirs = service.create(img(), null, other);
        assertThatThrownBy(() -> service.delete(theirs.getId(), user)).isInstanceOf(NotFoundException.class);
        assertThat(repo.findById(theirs.getId())).isPresent();
        assertThat(dir.resolve(theirs.getImageFileName())).exists();
        service.delete(theirs.getId(), admin);
        assertThat(repo.findById(theirs.getId())).isEmpty();
    }
}
