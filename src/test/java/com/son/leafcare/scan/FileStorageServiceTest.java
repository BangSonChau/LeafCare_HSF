package com.son.leafcare.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

@DataJpaTest
@Import(FileStorageService.class)
class FileStorageServiceTest {

    @Autowired
    FileStorageService service;

    @Autowired
    ImageFileRepository images;

    byte[] bytes = {1, 2, 3};

    void assertRejected(MockMultipartFile f) {
        assertThatThrownBy(() -> service.store(f)).isInstanceOf(InvalidImageException.class);
        assertThat(images.count()).isZero();
    }

    @Test
    void storesValidImageWithUuidNameInDb() {
        String name = service.store(new MockMultipartFile("file", "leaf.png", "image/png", bytes));
        assertThat(name).matches("[0-9a-f-]{36}\\.png");
        ImageFile saved = service.find(name).orElseThrow();
        assertThat(saved.getContentType()).isEqualTo("image/png");
        assertThat(saved.getData()).containsExactly(bytes);
    }

    @Test
    void upperCaseExtensionAndTypeAreLowered() {
        String name = service.store(new MockMultipartFile("file", "x.JPG", "IMAGE/JPEG", bytes));
        assertThat(name).endsWith(".jpg");
        assertThat(service.find(name).orElseThrow().getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void storesFiveMegabytes() {
        String name = service.store(new MockMultipartFile("file", "big.webp", "image/webp", new byte[5 * 1024 * 1024]));
        assertThat(service.find(name).orElseThrow().getData()).hasSize(5 * 1024 * 1024);
    }

    @Test
    void rejectsGif() {
        assertRejected(new MockMultipartFile("file", "leaf.gif", "image/gif", bytes));
    }

    @Test
    void rejectsWrongContentType() {
        assertRejected(new MockMultipartFile("file", "leaf.png", "text/html", bytes));
    }

    @Test
    void rejectsPhpExtension() {
        assertRejected(new MockMultipartFile("file", "shell.php", "image/png", bytes));
    }

    @Test
    void rejectsNoExtension() {
        assertRejected(new MockMultipartFile("file", "leaf", "image/png", bytes));
    }

    @Test
    void rejectsEmpty() {
        assertRejected(new MockMultipartFile("file", "leaf.png", "image/png", new byte[0]));
    }

    @Test
    void rejectsOver5Mb() {
        assertRejected(new MockMultipartFile("file", "leaf.png", "image/png", new byte[5 * 1024 * 1024 + 1]));
    }

    @Test
    void originalNameIsNeverUsed() {
        String name = service.store(new MockMultipartFile("file", "../../shell.php.png", "image/png", bytes));
        assertThat(name).matches("[0-9a-f-]{36}\\.png");
    }

    @Test
    void deleteRemovesAndMissingIsSilent() {
        String name = service.store(new MockMultipartFile("file", "leaf.png", "image/png", bytes));
        service.delete(name);
        assertThat(service.find(name)).isEmpty();
        assertThatCode(() -> service.delete("missing.png")).doesNotThrowAnyException();
    }
}
