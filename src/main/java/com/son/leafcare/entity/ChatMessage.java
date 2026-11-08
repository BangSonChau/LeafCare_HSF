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
@Table(name = "chat_message")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRole role;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(length = 255)
    private String imageFileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ChatMessageStatus status = ChatMessageStatus.COMPLETED;

    @Column(length = 100)
    private String clientRequestId;

    @Column(length = 100)
    private String modelName;

    @Column(length = 50)
    private String promptVersion;

    private Integer indexVersion;

    private Integer promptTokens;

    private Integer completionTokens;

    private Long latencyMs;

    @Column(length = 2000)
    private String followUpQuestionsJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

