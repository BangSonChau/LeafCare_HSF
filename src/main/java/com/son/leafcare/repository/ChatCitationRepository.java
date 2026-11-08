package com.son.leafcare.repository;

import com.son.leafcare.entity.ChatCitation;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatCitationRepository extends JpaRepository<ChatCitation, Long> {

    @EntityGraph(attributePaths = {"chunk", "chunk.source"})
    List<ChatCitation> findByMessageIdOrderByDisplayOrderAsc(Long messageId);

    @EntityGraph(attributePaths = {"chunk", "chunk.source"})
    List<ChatCitation> findByMessageIdInOrderByDisplayOrderAsc(List<Long> messageIds);
}

