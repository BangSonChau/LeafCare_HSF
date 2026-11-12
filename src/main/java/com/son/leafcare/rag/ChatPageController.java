package com.son.leafcare.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Trang hỏi đáp về bệnh lá (server-rendered, form POST thường nên không cần JS hay CSRF thủ công). */
@Controller
public class ChatPageController {

    private static final Logger log = LoggerFactory.getLogger(ChatPageController.class);
    static final int MAX_QUESTION = 500;

    private final RagChatService chat;

    public ChatPageController(RagChatService chat) {
        this.chat = chat;
    }

    @GetMapping("/chat")
    public String form() {
        return "chat/index";
    }

    @PostMapping("/chat")
    public String ask(@RequestParam(defaultValue = "") String question, Model model) {
        String q = question.trim();
        model.addAttribute("question", q);
        if (q.isEmpty() || q.length() > MAX_QUESTION) {
            model.addAttribute("error", "Câu hỏi phải có 1 đến " + MAX_QUESTION + " ký tự.");
            return "chat/index";
        }
        try {
            model.addAttribute("result", chat.ask(q));
        } catch (RuntimeException e) { // Gemini hết quota, sai key, mất mạng...
            log.warn("RAG chat failed", e);
            model.addAttribute("error", "Chưa trả lời được lúc này, bạn thử lại sau ít phút.");
        }
        return "chat/index";
    }
}
