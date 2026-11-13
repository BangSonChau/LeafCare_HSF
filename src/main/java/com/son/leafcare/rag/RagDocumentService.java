package com.son.leafcare.rag;

import java.io.IOException;
import java.util.List;
import java.util.function.IntConsumer;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Đọc file -> chia nhỏ (chunk) -> embedding -> lưu vào pgvector. */
@Service
public class RagDocumentService {

    /** Số đoạn mỗi lần gọi embedding: đủ nhỏ để dưới giới hạn mỗi request của Gemini. */
    static final int BATCH = 20;
    static final int MAX_ATTEMPTS = 5;

    private final VectorStore vectorStore;
    private final long backoffMs;
    private final AnswerCache cache;
    // ~400 token mỗi đoạn: đủ ngữ cảnh, không quá dài để tìm kiếm mất chính xác
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(400).build();

    @Autowired
    public RagDocumentService(VectorStore vectorStore, AnswerCache cache) {
        this(vectorStore, 20_000, cache); // gói Gemini free giới hạn token mỗi phút: chờ rồi thử lại
    }

    RagDocumentService(VectorStore vectorStore, long backoffMs) {
        this(vectorStore, backoffMs, new AnswerCache(null, null, 1));
    }

    RagDocumentService(VectorStore vectorStore, long backoffMs, AnswerCache cache) {
        this.vectorStore = vectorStore;
        this.backoffMs = backoffMs;
        this.cache = cache;
    }

    /** Đọc file và chia đoạn, chưa lưu. Rỗng nếu file không có chữ (ví dụ PDF scan). */
    public List<Document> split(Resource file, String fileName) throws IOException {
        if (file.contentLength() == 0) { // Tika throws on empty input
            return List.of();
        }
        List<Document> chunks = splitter.apply(new TikaDocumentReader(file).get());
        chunks.forEach(c -> c.getMetadata().put("source", fileName));
        return chunks;
    }

    /**
     * Embedding + lưu theo lô; gặp lỗi (thường là quota/rate limit) thì chờ rồi thử lại tối đa {@value #MAX_ATTEMPTS} lần.
     * Lô nào đã lưu thì giữ nguyên nếu lô sau thất bại. {@code onBatch} nhận tổng số đoạn đã lưu.
     */
    public void store(List<Document> chunks, IntConsumer onBatch) {
        try {
            storeBatches(chunks, onBatch);
        } finally {
            cache.clear(); // cẩm nang đã đổi (kể cả nạp dở): câu trả lời đã lưu có thể cũ
        }
    }

    private void storeBatches(List<Document> chunks, IntConsumer onBatch) {
        for (int from = 0; from < chunks.size(); from += BATCH) {
            List<Document> batch = chunks.subList(from, Math.min(from + BATCH, chunks.size()));
            for (int attempt = 1; ; attempt++) {
                try {
                    vectorStore.add(batch);
                    break;
                } catch (RuntimeException e) {
                    if (attempt == MAX_ATTEMPTS) {
                        throw e;
                    }
                    sleep(backoffMs * attempt);
                }
            }
            onBatch.accept(from + batch.size());
        }
    }

    /** Đọc và lưu ngay trong request (dùng cho API, file nhỏ). Trả về số đoạn đã lưu. */
    public int ingest(Resource file, String fileName) throws IOException {
        List<Document> chunks = split(file, fileName);
        store(chunks, n -> { });
        return chunks.size();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị ngắt khi chờ thử lại", ie);
        }
    }
}
