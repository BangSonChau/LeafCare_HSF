package com.son.leafcare.rag;

import com.son.leafcare.ai.GeminiGateway;
import com.son.leafcare.rag.KnowledgeIndex.IndexedChunk;
import com.son.leafcare.rag.KnowledgeIndex.ScoredChunk;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);
    private static final int CANDIDATE_POOL_SIZE = 12;
    private static final int MAX_PROMPT_CHUNKS = 5;

    private final KnowledgeIndex knowledgeIndex;
    private final GeminiGateway geminiGateway;

    public RetrievalService(KnowledgeIndex knowledgeIndex, GeminiGateway geminiGateway) {
        this.knowledgeIndex = knowledgeIndex;
        this.geminiGateway = geminiGateway;
    }

    public RetrievalResult retrieve(String userQuery, String preferredPlantName, String conversationContext) {
        String normalizedQuery = VietnameseTextNormalizer.normalizeNFC(userQuery);
        String resolvedPlant = preferredPlantName != null && !preferredPlantName.isBlank()
                ? preferredPlantName
                : VietnameseTextNormalizer.detectPlantTag(normalizedQuery + " " + (conversationContext == null ? "" : conversationContext));

        String searchQuery = buildSearchQuery(normalizedQuery, resolvedPlant, conversationContext);

        float[] queryVector = new float[0];
        if (geminiGateway.isAvailable()) {
            try {
                queryVector = geminiGateway.embed(searchQuery, true);
            } catch (Exception e) {
                log.warn("Embedding query failed, falling back to keyword/BM25 retrieval: {}", e.getMessage());
            }
        }

        List<ScoredChunk> candidates = knowledgeIndex.search(
                searchQuery,
                queryVector,
                resolvedPlant,
                CANDIDATE_POOL_SIZE);

        Map<String, ScoredChunk> deduplicated = new LinkedHashMap<>();
        for (ScoredChunk sc : candidates) {
            String code = sc.chunk().chunkCode();
            if (!deduplicated.containsKey(code) && deduplicated.size() < MAX_PROMPT_CHUNKS) {
                deduplicated.put(code, sc);
            }
        }

        List<ScoredChunk> topChunks = new ArrayList<>(deduplicated.values());
        boolean hasStrongEvidence = !topChunks.isEmpty() && topChunks.get(0).combinedScore() >= 0.32;

        return new RetrievalResult(
                searchQuery,
                resolvedPlant,
                knowledgeIndex.getActiveIndexVersion(),
                topChunks,
                hasStrongEvidence
        );
    }

    private String buildSearchQuery(String userQuery, String resolvedPlant, String conversationContext) {
        StringBuilder sb = new StringBuilder();
        if (resolvedPlant != null && !resolvedPlant.isBlank()) {
            sb.append("Cây trồng: ").append(resolvedPlant).append(". ");
        }
        sb.append(userQuery);
        if (conversationContext != null && !conversationContext.isBlank() && userQuery.length() < 80) {
            sb.append(" ").append(conversationContext);
        }
        return sb.toString().trim();
    }

    public record RetrievalResult(
            String effectiveQuery,
            String resolvedPlantName,
            int indexVersion,
            List<ScoredChunk> chunks,
            boolean hasStrongEvidence
    ) {
        public List<IndexedChunk> indexedChunks() {
            return chunks.stream().map(ScoredChunk::chunk).toList();
        }
    }
}
