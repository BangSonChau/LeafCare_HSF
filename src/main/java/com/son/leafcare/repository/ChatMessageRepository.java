package com.son.leafcare.repository;

import com.son.leafcare.entity.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    Optional<ChatMessage> findFirstByConversationIdAndClientRequestId(
            Long conversationId, String clientRequestId);
}

