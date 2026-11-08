package com.son.leafcare.service;

import java.time.LocalDateTime;
import java.util.List;

public final class ChatDtos {

    private ChatDtos() {}

    public record ConversationSummaryDto(
            Long id,
            String title,
            Long plantId,
            String plantName,
            Long scanHistoryId,
            String scanImageUrl,
            LocalDateTime updatedAt
    ) {}

    public record CitationDto(
            String chunkCode,
            String sectionTitle,
            String sourceTitle,
            String publisher,
            String publishedDate,
            String sourceUrl,
            String snippet,
            boolean withdrawn
    ) {}

    public record MessageDto(
            Long id,
            String role,
            String content,
            String imageUrl,
            String status,
            String statusLabel,
            List<String> followUpQuestions,
            List<CitationDto> citations,
            LocalDateTime createdAt
    ) {}

    public record ChatTurnResponseDto(
            Long conversationId,
            String conversationTitle,
            MessageDto userMessage,
            MessageDto assistantMessage
    ) {}
}

