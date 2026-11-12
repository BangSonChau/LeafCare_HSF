package com.son.leafcare.scan;

import com.son.leafcare.common.NotFoundException;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Serves uploaded photos from the DB at the same /uploads/{name} URLs the templates already use. */
@Controller
public class ImageController {

    private final FileStorageService storage;

    public ImageController(FileStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/uploads/{name}")
    public ResponseEntity<byte[]> image(@PathVariable String name) {
        return storage.find(name)
                .map(i -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(i.getContentType()))
                        // names are random UUIDs and never reused, so the browser may keep them
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate())
                        .body(i.getData()))
                .orElseThrow(NotFoundException::new);
    }
}
