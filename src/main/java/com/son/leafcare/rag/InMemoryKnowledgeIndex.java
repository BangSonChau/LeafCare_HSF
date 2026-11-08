package com.son.leafcare.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.son.leafcare.entity.KnowledgeChunk;
import com.son.leafcare.entity.KnowledgeSource;
import com.son.leafcare.entity.ReviewStatus;
import com.son.leafcare.repository.KnowledgeChunkRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InMemoryKnowledgeIndex implements KnowledgeIndex {

    private static final Logger log = LoggerFactory.getLogger(InMemoryKnowledgeIndex.class);

    private final KnowledgeChunkRepository chunkRepo;
    private final ObjectMapper objectMapper;
    private final AtomicReference<IndexSnapshot> activeSnapshot =
            new AtomicReference<>(new IndexSnapshot(1, List.of()));

    public InMemoryKnowledgeIndex(KnowledgeChunkRepository chunkRepo) {
        this.chunkRepo = chunkRepo;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public int getActiveIndexVersion() {
        return activeSnapshot.get().indexVersion();
    }

    @Override
    public int getChunkCount() {
        return activeSnapshot.get().entries().size();
    }

    @Override
    public synchronized void reload() {
        List<KnowledgeChunk> approvedChunks = chunkRepo.findByReviewStatusOrderByIdAsc(ReviewStatus.APPROVED);
        int maxVersion = 1;
        for (KnowledgeChunk c : approvedChunks) {
            if (c.getIndexVersion() != null && c.getIndexVersion() > maxVersion) {
                maxVersion = c.getIndexVersion();
            }
        }
        List<PrecomputedEntry> entries = new ArrayList<>();
        for (KnowledgeChunk c : approvedChunks) {
            KnowledgeSource src = c.getSource();
            if (src == null || src.getReviewStatus() != ReviewStatus.APPROVED) {
                continue;
            }
            int chunkIdxVer = c.getIndexVersion() == null ? 1 : c.getIndexVersion();
            if (chunkIdxVer != maxVersion) {
                continue;
            }
            float[] vec = parseVector(c.getVectorJson(), c.getDimension());
            IndexedChunk indexed = new IndexedChunk(
                    c.getId(),
                    c.getChunkCode(),
                    c.getSectionTitle(),
                    c.getContent(),
                    c.getPlantTag(),
                    c.getScientificNameTag(),
                    c.getDiseaseTag(),
                    c.getPlantPart(),
                    c.getRegion(),
                    c.getSourceVersion() == null ? 1 : c.getSourceVersion(),
                    src.getSourceCode(),
                    src.getTitle(),
                    src.getUrl(),
                    src.getPublisher(),
                    src.getPublishedDate(),
                    chunkIdxVer,
                    vec
            );
            String fullAscii = VietnameseTextNormalizer.stripAccents(
                    (c.getPlantTag() == null ? "" : c.getPlantTag()) + " "
                    + (c.getScientificNameTag() == null ? "" : c.getScientificNameTag()) + " "
                    + (c.getDiseaseTag() == null ? "" : c.getDiseaseTag()) + " "
                    + (c.getSectionTitle() == null ? "" : c.getSectionTitle()) + " "
                    + c.getContent()
            );
            String titleAndTagAscii = VietnameseTextNormalizer.stripAccents(
                    (c.getPlantTag() == null ? "" : c.getPlantTag()) + " "
                    + (c.getDiseaseTag() == null ? "" : c.getDiseaseTag()) + " "
                    + (c.getSectionTitle() == null ? "" : c.getSectionTitle())
            );
            String plantAscii = VietnameseTextNormalizer.stripAccents(c.getPlantTag());
            entries.add(new PrecomputedEntry(indexed, fullAscii, titleAndTagAscii, plantAscii));
        }
        activeSnapshot.set(new IndexSnapshot(maxVersion, List.copyOf(entries)));
        log.info("KnowledgeIndex reloaded atomically: indexVersion={}, activeChunks={}", maxVersion, entries.size());
    }

    @Override
    public List<ScoredChunk> search(String queryText, float[] queryVector, String preferredPlantName, int topK) {
        IndexSnapshot snapshot = activeSnapshot.get();
        if (snapshot.entries().isEmpty()) {
            return List.of();
        }
        List<String> queryTokens = VietnameseTextNormalizer.tokenizeKeywords(queryText);
        String queryAscii = VietnameseTextNormalizer.stripAccents(queryText);
        String targetPlant = preferredPlantName != null && !preferredPlantName.isBlank()
                ? preferredPlantName
                : VietnameseTextNormalizer.detectPlantTag(queryText);
        String targetPlantAscii = VietnameseTextNormalizer.stripAccents(targetPlant);

        boolean hasQueryVector = queryVector != null && queryVector.length > 0;
        List<ScoredChunk> scored = new ArrayList<>();

        for (PrecomputedEntry entry : snapshot.entries()) {
            double cosine = 0.0;
            float[] docVec = entry.chunk().vector();
            if (hasQueryVector && docVec != null && docVec.length == queryVector.length) {
                cosine = Math.max(0.0, cosineSimilarity(queryVector, docVec));
            }

            double kwScore = computeKeywordScore(queryTokens, queryAscii, entry);
            double plantBonus = 0.0;
            if (!targetPlantAscii.isEmpty() && !entry.plantAscii().isEmpty()) {
                if (entry.plantAscii().equals(targetPlantAscii) || entry.plantAscii().contains(targetPlantAscii)) {
                    plantBonus = 0.22;
                } else {
                    // Slight penalty when user explicitly selected a different plant
                    plantBonus = -0.10;
                }
            }

            double combined;
            if (hasQueryVector && docVec != null && docVec.length == queryVector.length) {
                combined = (0.62 * cosine) + (0.38 * kwScore) + plantBonus;
            } else {
                combined = kwScore + plantBonus;
            }

            if (combined > 0.08 || cosine > 0.35 || kwScore > 0.12) {
                scored.add(new ScoredChunk(entry.chunk(), combined, cosine, kwScore));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredChunk::combinedScore).reversed());
        int limit = Math.max(1, Math.min(topK, scored.size()));
        return scored.subList(0, limit);
    }

    private double computeKeywordScore(List<String> queryTokens, String queryAscii, PrecomputedEntry entry) {
        if (queryTokens.isEmpty()) {
            return 0.0;
        }
        int matchedTokens = 0;
        int titleMatches = 0;
        Set<String> docWords = new java.util.HashSet<>(List.of(entry.fullAscii().split(" ")));
        Set<String> titleWords = new java.util.HashSet<>(List.of(entry.titleAndTagAscii().split(" ")));

        for (String token : queryTokens) {
            if (docWords.contains(token)) {
                matchedTokens++;
            }
            if (titleWords.contains(token)) {
                titleMatches++;
            }
        }

        // Check bigrams in query against document text
        int bigramMatches = 0;
        for (int i = 0; i < queryTokens.size() - 1; i++) {
            String bigram = queryTokens.get(i) + " " + queryTokens.get(i + 1);
            if (entry.fullAscii().contains(bigram)) {
                bigramMatches++;
            }
            if (entry.titleAndTagAscii().contains(bigram)) {
                bigramMatches++;
            }
        }

        double tokenRatio = (double) matchedTokens / queryTokens.size();
        double titleBonus = Math.min(0.35, titleMatches * 0.10);
        double bigramBonus = Math.min(0.30, bigramMatches * 0.12);
        return Math.min(1.0, (tokenRatio * 0.65) + titleBonus + bigramBonus);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private float[] parseVector(String json, Integer expectedDim) {
        if (json == null || json.isBlank()) {
            return new float[0];
        }
        try {
            List<Float> list = objectMapper.readValue(json, new TypeReference<List<Float>>() {});
            if (list == null || list.isEmpty()) {
                return new float[0];
            }
            if (expectedDim != null && expectedDim > 0 && list.size() != expectedDim) {
                log.warn("Skipping vector with mismatched dimension: expected={}, actual={}", expectedDim, list.size());
                return new float[0];
            }
            float[] arr = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Float val = list.get(i);
                if (val == null || Float.isNaN(val) || Float.isInfinite(val)) {
                    return new float[0];
                }
                arr[i] = val;
            }
            return arr;
        } catch (Exception e) {
            log.warn("Invalid vector JSON in knowledge_chunk: {}", e.getMessage());
            return new float[0];
        }
    }

    private record PrecomputedEntry(
            IndexedChunk chunk,
            String fullAscii,
            String titleAndTagAscii,
            String plantAscii
    ) {}

    private record IndexSnapshot(
            int indexVersion,
            List<PrecomputedEntry> entries
    ) {}
}
