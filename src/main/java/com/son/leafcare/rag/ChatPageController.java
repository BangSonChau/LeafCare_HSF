package com.son.leafcare.rag;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Trang hỏi đáp về bệnh lá. Có JS: câu trả lời chạy dần qua POST /chat/stream (SSE).
 * Không JS: form POST /chat thường, chờ đủ câu trả lời.
 */
@Controller
public class ChatPageController {

    private static final Logger log = LoggerFactory.getLogger(ChatPageController.class);
    static final int MAX_QUESTION = 500;
    static final String LENGTH_ERROR = "Câu hỏi phải có 1 đến " + MAX_QUESTION + " ký tự.";
    static final String FAILED = "Chưa trả lời được lúc này, bạn thử lại sau ít phút.";

    private final RagChatService chat;

    public ChatPageController(RagChatService chat) {
        this.chat = chat;
    }

    /** ?q=... (từ nút "Hỏi cẩm nang" ở kết quả quét): điền sẵn câu hỏi và tự hỏi khi trang mở. */
    @GetMapping("/chat")
    public String form(@RequestParam(required = false) String q, Model model) {
        if (q != null && !q.isBlank() && q.trim().length() <= MAX_QUESTION) {
            model.addAttribute("question", q.trim());
            model.addAttribute("autoAsk", true);
        }
        return "chat/index";
    }

    @PostMapping("/chat")
    public String ask(@RequestParam(defaultValue = "") String question, Model model) {
        String q = question.trim();
        model.addAttribute("question", q);
        if (q.isEmpty() || q.length() > MAX_QUESTION) {
            model.addAttribute("error", LENGTH_ERROR);
            return "chat/index";
        }
        try {
            RagAnswer result = chat.ask(q);
            model.addAttribute("result", result);
            model.addAttribute("answerHtml", RagChatService.toHtml(result.answer()));
        } catch (RuntimeException e) { // tìm trong cẩm nang lỗi: embedding Gemini hết quota, sai key, mất DB...
            log.warn("RAG chat failed", e);
            model.addAttribute("error", FAILED);
        }
        return "chat/index";
    }

    /**
     * Sự kiện SSE: sources (danh sách nguồn), token (một đoạn chữ), done; hoặc busy (Gemini quá tải, nguồn
     * đã gửi vẫn dùng được), error (lỗi khác, kèm câu báo).
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "") String question) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String q = question.trim();
        if (q.isEmpty() || q.length() > MAX_QUESTION) {
            send(emitter, "error", Map.of("message", LENGTH_ERROR));
            emitter.complete();
            return emitter;
        }
        java.util.Optional<RagAnswer> hit = chat.cached(q);
        if (hit.isPresent()) { // đã có câu trả lời cho câu hỏi giống nghĩa: trả ngay
            send(emitter, "sources", hit.get().sources());
            send(emitter, "token", Map.of("t", hit.get().answer()));
            send(emitter, "done", Map.of("cached", true));
            emitter.complete();
            return emitter;
        }
        // shortcut: tìm cẩm nang (chặn luồng) chạy trên common pool, đủ cho vài người dùng; nhiều hơn thì dùng executor riêng
        CompletableFuture.runAsync(() -> {
            RagChatService.Prepared p;
            try {
                p = chat.prepare(q);
            } catch (RuntimeException e) {
                log.warn("RAG chat failed", e);
                send(emitter, "error", Map.of("message", FAILED));
                emitter.complete();
                return;
            }
            send(emitter, "sources", p.sources());
            if (p.prompt() == null) {
                send(emitter, "token", Map.of("t", RagChatService.NO_INFO));
                send(emitter, "done", Map.of());
                emitter.complete();
                return;
            }
            StringBuilder full = new StringBuilder();
            reactor.core.Disposable sub = chat.stream(p.prompt()).subscribe(
                    t -> {
                        full.append(t);
                        send(emitter, "token", Map.of("t", t));
                    },
                    e -> {
                        send(emitter, "busy", Map.of());
                        emitter.complete();
                    },
                    () -> {
                        send(emitter, "done", Map.of());
                        chat.remember(q, new RagAnswer(full.toString(), p.sources()));
                        emitter.complete();
                    });
            // người dùng đóng trang hoặc quá 2 phút: dừng gọi Gemini
            emitter.onTimeout(sub::dispose);
            emitter.onError(e -> sub.dispose());
        });
        return emitter;
    }

    private static void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // người dùng đã đóng trang: bỏ qua
        }
    }
}
