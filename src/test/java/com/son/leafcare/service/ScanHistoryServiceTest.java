package com.son.leafcare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.son.leafcare.entity.ScanHistory;
import com.son.leafcare.entity.ScanStatus;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.repository.ScanHistoryRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@Import({ScanHistoryService.class, FileStorageService.class})
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

    MockMultipartFile img() {
        return new MockMultipartFile("file", "leaf.png", "image/png", new byte[] {1, 2});
    }

    @Test
    void createSetsPendingAndTimestamp() {
        ScanHistory s = service.create(img(), "  note  ");
        assertThat(s.getStatus()).isEqualTo(ScanStatus.PENDING);
        assertThat(s.getCreatedAt()).isNotNull();
        assertThat(s.getNote()).isEqualTo("note");
        assertThat(dir.resolve(s.getImageFileName())).exists();
    }

    @Test
    void blankNoteBecomesNull() {
        assertThat(service.create(img(), "   ").getNote()).isNull();
    }

    @Test
    void deleteRemovesRowAndFile() {
        ScanHistory s = service.create(img(), null);
        service.delete(s.getId());
        assertThat(repo.findById(s.getId())).isEmpty();
        assertThat(dir.resolve(s.getImageFileName())).doesNotExist();
    }

    @Test
    void deleteWithMissingFileStillRemovesRow() throws IOException {
        ScanHistory s = service.create(img(), null);
        Files.delete(dir.resolve(s.getImageFileName()));
        service.delete(s.getId());
        assertThat(repo.findById(s.getId())).isEmpty();
    }

    @Test
    void unknownIdIs404() {
        assertThatThrownBy(() -> service.delete(999L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listClampsToLastPage() {
        for (int i = 0; i < 11; i++) {
            service.create(img(), "n" + i);
        }
        var p = service.list(99);
        assertThat(p.getNumber()).isEqualTo(1);
        assertThat(p.getContent()).hasSize(1);
        assertThat(service.list(-3).getNumber()).isZero();
    }

    @Test
    void listOnEmptyIsPageZero() {
        assertThat(service.list(5).getNumber()).isZero();
    }
}
