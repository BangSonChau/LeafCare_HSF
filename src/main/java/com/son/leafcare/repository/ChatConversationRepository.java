package com.son.leafcare.repository;

import com.son.leafcare.entity.ChatConversation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    @EntityGraph(attributePaths = {"plant", "scanHistory"})
    List<ChatConversation> findBySessionOwnerAndExpiresAtAfterOrderByUpdatedAtDescIdDesc(
            String sessionOwner, LocalDateTime now);

    @EntityGraph(attributePaths = {"plant", "scanHistory"})
    Optional<ChatConversation> findByIdAndSessionOwner(Long id, String sessionOwner);
}

