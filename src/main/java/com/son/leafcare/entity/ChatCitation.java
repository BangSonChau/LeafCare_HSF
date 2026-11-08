package com.son.leafcare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_citation")
@Getter
@Setter
@NoArgsConstructor
public class ChatCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id", nullable = false)
    private KnowledgeChunk chunk;

    @Column(nullable = false, length = 120)
    private String chunkCode;

    @Column(nullable = false)
    private Integer sourceVersion = 1;

    @Column(nullable = false)
    private Integer displayOrder = 1;

    @Column(length = 600)
    private String snippet;
}

