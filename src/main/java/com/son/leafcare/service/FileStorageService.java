package com.son.leafcare.service;

import com.son.leafcare.exception.InvalidImageException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path dir;

    public FileStorageService(@Value("${leafcare.upload-dir}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    public String store(MultipartFile f) {
        if (f == null || f.isEmpty()) {
            throw new InvalidImageException("Vui lòng chọn ảnh lá");
        }
        if (f.getSize() > MAX_BYTES) {
            throw new InvalidImageException("Ảnh tối đa 5 MB");
        }
        String original = f.getOriginalFilename() == null ? "" : f.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        String type = f.getContentType() == null ? "" : f.getContentType().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(ext) || !CONTENT_TYPES.contains(type)) {
            throw new InvalidImageException("Chỉ nhận ảnh JPG, PNG hoặc WebP");
        }
        String name = UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(name).normalize();
            if (!target.startsWith(dir)) {
                throw new InvalidImageException("Tên ảnh không hợp lệ");
            }
            try (InputStream in = f.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return name;
    }

    public void delete(String fileName) {
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // shortcut: an undeletable file leaves an orphan (row already gone); add a cleanup job if it matters.
        }
    }
}
