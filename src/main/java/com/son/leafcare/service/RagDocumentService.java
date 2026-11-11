package com.son.leafcare.service;

import java.io.IOException;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Đọc file -> chia nhỏ (chunk) -> embedding -> lưu vào pgvector. */
@Service
public class RagDocumentService {

    private final VectorStore vectorStore;
    // ~400 token mỗi đoạn: đủ ngữ cảnh, không quá dài để tìm kiếm mất chính xác
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(400).build();

    public RagDocumentService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** Trả về số đoạn đã lưu (0 nếu file không có chữ, ví dụ PDF scan). */
    public int ingest(Resource file, String fileName) throws IOException {
        if (file.contentLength() == 0) { // Tika throws on empty input
            return 0;
        }
        List<Document> chunks = splitter.apply(new TikaDocumentReader(file).get());
        chunks.forEach(c -> c.getMetadata().put("source", fileName));
        if (!chunks.isEmpty()) {
            vectorStore.add(chunks);
        }
        return chunks.size();
    }
}
