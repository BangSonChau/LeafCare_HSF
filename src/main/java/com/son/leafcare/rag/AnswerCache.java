package com.son.leafcare.rag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Semantic cache: câu hỏi đã trả lời được lưu kèm vector (bảng {@value #TABLE}, Spring AI tự tạo như vector_store).
 * Câu hỏi mới đủ giống nghĩa (cosine >= ngưỡng) thì dùng lại câu trả lời, không gọi Gemini.
 * Cache lỗi (DB, embedding) thì coi như không có, không làm hỏng việc hỏi đáp.
 */
@Component
public class AnswerCache {

    private static final Logger log = LoggerFactory.getLogger(AnswerCache.class);
    static final String TABLE = "rag_answer_cache";

    private final VectorStore store; // null = tắt (test H2 không có pgvector/embedding)
    private final JdbcTemplate jdbc;
    private final double threshold;

    @Autowired
    public AnswerCache(ObjectProvider<EmbeddingModel> embedding, ObjectProvider<JdbcTemplate> jdbc,
                       @Value("${spring.ai.vectorstore.type:pgvector}") String storeType,
                       @Value("${leafcare.rag.cache-threshold:0.92}") double threshold) {
        EmbeddingModel model = embedding.getIfAvailable();
        this.jdbc = jdbc.getIfAvailable();
        this.threshold = threshold;
        if (model == null || this.jdbc == null || "none".equals(storeType)) {
            this.store = null;
            return;
        }
        PgVectorStore pg = PgVectorStore.builder(this.jdbc, model).vectorTableName(TABLE).dimensions(768)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW).initializeSchema(true).build();
        pg.afterPropertiesSet();
        this.store = pg;
    }

    AnswerCache(VectorStore store, JdbcTemplate jdbc, double threshold) {
        this.store = store;
        this.jdbc = jdbc;
        this.threshold = threshold;
    }

    public Optional<RagAnswer> find(String question) {
        if (store == null) {
            return Optional.empty();
        }
        try {
            List<Document> hits = store.similaritySearch(SearchRequest.builder()
                    .query(question).topK(1).similarityThreshold(threshold).build());
            return hits.stream().findFirst().map(AnswerCache::toAnswer);
        } catch (RuntimeException e) {
            log.warn("RAG cache lookup failed: {}", RagChatService.shortReason(e));
            return Optional.empty();
        }
    }

    public void put(String question, String answer, List<RagAnswer.Source> sources) {
        if (store == null || answer == null || answer.isBlank() || RagChatService.NO_INFO.equals(answer.trim())) {
            return;
        }
        try {
            Map<String, Object> meta = new HashMap<>();
            meta.put("answer", answer);
            meta.put("sources", sources.stream().map(s -> Map.of("number", s.number(), "file", s.file(),
                    "excerpt", s.excerpt())).toList());
            store.add(List.of(new Document(question, meta)));
        } catch (RuntimeException e) {
            log.warn("RAG cache save failed: {}", RagChatService.shortReason(e));
        }
    }

    /** Cẩm nang thay đổi thì câu trả lời cũ có thể sai: xoá hết. */
    public void clear() {
        if (store == null) {
            return;
        }
        try {
            jdbc.update("DELETE FROM " + TABLE);
        } catch (RuntimeException e) {
            log.warn("RAG cache clear failed: {}", RagChatService.shortReason(e));
        }
    }

    @SuppressWarnings("unchecked")
    static RagAnswer toAnswer(Document d) {
        Object raw = d.getMetadata().getOrDefault("sources", List.of());
        List<RagAnswer.Source> sources = ((List<Map<String, Object>>) raw).stream()
                .map(m -> new RagAnswer.Source(((Number) m.get("number")).intValue(), String.valueOf(m.get("file")),
                        String.valueOf(m.get("excerpt")), null))
                .toList();
        return new RagAnswer(String.valueOf(d.getMetadata().get("answer")), sources);
    }
}
