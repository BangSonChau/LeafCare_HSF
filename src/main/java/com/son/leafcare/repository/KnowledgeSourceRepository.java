package com.son.leafcare.repository;

import com.son.leafcare.entity.KnowledgeSource;
import com.son.leafcare.entity.ReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, Long> {

    Optional<KnowledgeSource> findBySourceCode(String sourceCode);

    List<KnowledgeSource> findByReviewStatusOrderBySourceCodeAsc(ReviewStatus status);
}

