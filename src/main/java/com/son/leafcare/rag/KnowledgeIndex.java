package com.son.leafcare.rag;

import java.util.List;

public interface KnowledgeIndex {

    int getActiveIndexVersion();

    int getChunkCount();

    void reload();

    List<ScoredChunk> search(String queryText, float[] queryVector, String preferredPlantName, int topK);

    record IndexedChunk(
            Long chunkId,
            String chunkCode,
            String sectionTitle,
            String content,
            String plantTag,
            String scientificNameTag,
            String diseaseTag,
            String plantPart,
            String region,
            int sourceVersion,
            String sourceCode,
            String sourceTitle,
            String sourceUrl,
            String publisher,
            String publishedDate,
            int indexVersion,
            float[] vector
    ) {}

    record ScoredChunk(
            IndexedChunk chunk,
            double combinedScore,
            double cosineScore,
            double keywordScore
    ) {}
}

