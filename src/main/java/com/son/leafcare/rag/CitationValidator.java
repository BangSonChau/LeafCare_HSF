package com.son.leafcare.rag;

import com.son.leafcare.rag.KnowledgeIndex.IndexedChunk;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CitationValidator {

    /**
     * Filters citation IDs returned by the LLM so that ONLY chunk codes present in the
     * retrieved candidate list are accepted. Maps valid chunk codes back to their full
     * backend metadata (source title, publisher, URL, snippet).
     */
    public List<ValidatedCitation> validateAndMap(
            List<String> modelCitationIds,
            List<IndexedChunk> retrievedChunks) {

        if (modelCitationIds == null || modelCitationIds.isEmpty()
                || retrievedChunks == null || retrievedChunks.isEmpty()) {
            return List.of();
        }

        Map<String, IndexedChunk> allowedByCode = new LinkedHashMap<>();
        for (IndexedChunk c : retrievedChunks) {
            allowedByCode.put(c.chunkCode().trim().toUpperCase(), c);
        }

        Map<String, ValidatedCitation> validated = new LinkedHashMap<>();
        int order = 1;
        for (String rawId : modelCitationIds) {
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            String cleanKey = rawId.trim()
                    .replace("[", "")
                    .replace("]", "")
                    .toUpperCase();
            IndexedChunk matched = allowedByCode.get(cleanKey);
            if (matched != null && !validated.containsKey(matched.chunkCode())) {
                validated.put(matched.chunkCode(), new ValidatedCitation(
                        matched,
                        order++,
                        buildSnippet(matched.content())
                ));
            }
        }
        return new ArrayList<>(validated.values());
    }

    private String buildSnippet(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= 240) {
            return trimmed;
        }
        return trimmed.substring(0, 237) + "...";
    }

    public record ValidatedCitation(
            IndexedChunk chunk,
            int displayOrder,
            String snippet
    ) {}
}

