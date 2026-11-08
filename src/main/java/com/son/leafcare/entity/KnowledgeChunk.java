package com.son.leafcare.entity;

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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "knowledge_chunk")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String chunkCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private KnowledgeSource source;

    @Column(nullable = false)
    private Integer sourceVersion = 1;

    @Column(length = 500)
    private String sectionTitle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 255)
    private String plantTag;

    @Column(length = 255)
    private String scientificNameTag;

    @Column(length = 255)
    private String diseaseTag;

    @Column(length = 255)
    private String plantPart;

    @Column(length = 100)
    private String region;

    @Column(nullable = false, length = 128)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReviewStatus reviewStatus = ReviewStatus.APPROVED;

    @Column(length = 100)
    private String embeddingModel;

    private Integer dimension;

    @Column(columnDefinition = "TEXT")
    private String vectorJson;

    @Column(nullable = false)
    private Integer indexVersion = 1;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

