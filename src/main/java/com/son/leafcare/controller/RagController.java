package com.son.leafcare.controller;

import com.son.leafcare.service.RagAnswer;
import com.son.leafcare.service.RagChatService;
import com.son.leafcare.service.RagDocumentService;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** /api/rag/chat: mọi người đã đăng nhập; /api/rag/documents: chỉ ADMIN (chặn trong SecurityConfig). */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagDocumentService documents;
    private final RagChatService chat;

    public RagController(RagDocumentService documents, RagChatService chat) {
        this.documents = documents;
        this.chat = chat;
    }

    @PostMapping("/documents")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        return Map.of("file", String.valueOf(name), "chunks", documents.ingest(file.getResource(), name));
    }

    /** Body: {"question": "..."} */
    @PostMapping("/chat")
    public RagAnswer ask(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "").trim();
        if (question.isEmpty() || question.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi phải có 1 đến 500 ký tự");
        }
        return chat.ask(question);
    }
}
