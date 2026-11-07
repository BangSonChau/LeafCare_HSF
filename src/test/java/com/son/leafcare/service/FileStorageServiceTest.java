package com.son.leafcare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.son.leafcare.exception.InvalidImageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {

    @TempDir
    Path dir;

    byte[] bytes = {1, 2, 3};

    FileStorageService service() {
        return new FileStorageService(dir.toString());
    }

    long count() throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    void assertRejected(MockMultipartFile f) throws IOException {
        assertThatThrownBy(() -> service().store(f)).isInstanceOf(InvalidImageException.class);
        assertThat(count()).isZero();
    }

    @Test
    void storesValidImageWithUuidName() {
        String name = service().store(new MockMultipartFile("file", "leaf.png", "image/png", bytes));
        assertThat(name).matches("[0-9a-f-]{36}\\.png");
        assertThat(dir.resolve(name)).exists();
    }

    @Test
    void createsMissingDirectory() {
        Path nested = dir.resolve("a/b");
        String name = new FileStorageService(nested.toString())
                .store(new MockMultipartFile("file", "x.JPG", "image/jpeg", bytes));
        assertThat(name).endsWith(".jpg");
        assertThat(nested.resolve(name)).exists();
    }

    @Test
    void rejectsGif() throws IOException {
        assertRejected(new MockMultipartFile("file", "leaf.gif", "image/gif", bytes));
    }

    @Test
    void rejectsWrongContentType() throws IOException {
        assertRejected(new MockMultipartFile("file", "leaf.png", "text/html", bytes));
    }

    @Test
    void rejectsPhpExtension() throws IOException {
        assertRejected(new MockMultipartFile("file", "shell.php", "image/png", bytes));
    }

    @Test
    void rejectsNoExtension() throws IOException {
        assertRejected(new MockMultipartFile("file", "leaf", "image/png", bytes));
    }

    @Test
    void rejectsEmpty() throws IOException {
        assertRejected(new MockMultipartFile("file", "leaf.png", "image/png", new byte[0]));
    }

    @Test
    void rejectsOver5Mb() throws IOException {
        assertRejected(new MockMultipartFile("file", "leaf.png", "image/png", new byte[5 * 1024 * 1024 + 1]));
    }

    @Test
    void traversalNameStoresInsideDir() throws IOException {
        String name = service().store(new MockMultipartFile("file", "../../x.png", "image/png", bytes));
        assertThat(name).matches("[0-9a-f-]{36}\\.png");
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void doubleExtensionUsesLastDot() {
        String name = service().store(new MockMultipartFile("file", "shell.php.png", "image/png", bytes));
        assertThat(name).endsWith(".png").doesNotContain("shell");
    }

    @Test
    void deleteMissingIsSilent() {
        assertThatCode(() -> service().delete("missing.png")).doesNotThrowAnyException();
    }

    @Test
    void deleteIgnoresTraversal() throws IOException {
        Path outside = dir.getParent().resolve("outside-" + System.nanoTime() + ".png");
        Files.write(outside, bytes);
        try {
            service().delete("../" + outside.getFileName());
            assertThat(outside).exists();
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
