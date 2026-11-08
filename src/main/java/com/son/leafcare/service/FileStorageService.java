package com.son.leafcare.service;

import com.son.leafcare.entity.ImageFile;
import com.son.leafcare.exception.InvalidImageException;
import com.son.leafcare.repository.ImageFileRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Validates uploaded photos and keeps them in the image_file table (the server disk is not kept on Render Free). */
@Service
public class FileStorageService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ImageFileRepository images;

    public FileStorageService(ImageFileRepository images) {
        this.images = images;
    }

    /** Returns the new UUID name; the original file name is only used for its extension. */
    @Transactional
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
            images.save(new ImageFile(name, type, f.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return name;
    }

    @Transactional(readOnly = true)
    public Optional<ImageFile> find(String name) {
        return images.findById(name);
    }

    @Transactional
    public void delete(String name) {
        images.deleteById(name); // missing name is a no-op in Spring Data 3+
    }
}
