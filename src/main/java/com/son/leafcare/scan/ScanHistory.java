package com.son.leafcare.scan;

import com.son.leafcare.disease.Disease;
import com.son.leafcare.plant.Plant;
import com.son.leafcare.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ScanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String imageFileName;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    @Column(length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus status = ScanStatus.PENDING;

    private Double confidence;

    /** Raw label from the scan model (set once analysed), kept even when no disease row matches it. */
    @Column(length = 100)
    private String modelLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    private Plant plant;

    @ManyToOne(fetch = FetchType.LAZY)
    private Disease disease;

    /** Who scanned it; null for scans made before login existed (only ADMIN sees those). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser owner;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
