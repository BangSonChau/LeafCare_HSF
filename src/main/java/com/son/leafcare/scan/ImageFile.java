package com.son.leafcare.scan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An uploaded photo, keyed by the UUID file name that ScanHistory.imageFileName points to. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ImageFile {

    @Id
    @Column(length = 64)
    private String name;

    @Column(nullable = false, length = 32)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    public ImageFile(String name, String contentType, byte[] data) {
        this.name = name;
        this.contentType = contentType;
        this.data = data;
    }
}
