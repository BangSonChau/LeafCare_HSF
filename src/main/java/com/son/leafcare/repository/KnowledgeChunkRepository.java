package com.son.leafcare.repository;

import com.son.leafcare.entity.KnowledgeChunk;
import com.son.leafcare.entity.ReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    @EntityGraph(attributePaths = "source")
    Optional<KnowledgeChunk> findByChunkCode(String chunkCode);

    @EntityGraph(attributePaths = "source")
    List<KnowledgeChunk> findByReviewStatusOrderByIdAsc(ReviewStatus status);

    @EntityGraph(attributePaths = "source")
    List<KnowledgeChunk> findByChunkCodeIn(List<String> chunkCodes);
}

