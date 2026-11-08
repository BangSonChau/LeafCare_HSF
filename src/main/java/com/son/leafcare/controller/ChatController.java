package com.son.leafcare.controller;

import com.son.leafcare.ai.GeminiGateway;
import com.son.leafcare.entity.Disease;
import com.son.leafcare.entity.ScanHistory;
import com.son.leafcare.exception.InvalidImageException;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.rag.KnowledgeIndex;
import com.son.leafcare.service.ChatDtos.ChatTurnResponseDto;
import com.son.leafcare.service.ChatDtos.ConversationSummaryDto;
import com.son.leafcare.service.ChatDtos.MessageDto;
import com.son.leafcare.service.ChatRateLimiter;
import com.son.leafcare.service.ChatService;
import com.son.leafcare.service.DiseaseService;
import com.son.leafcare.service.PlantService;
import com.son.leafcare.service.ScanHistoryService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ChatController {

    public static final String SESSION_OWNER_KEY = "LEAFCARE_CHAT_OWNER";

    private final ChatService chatService;
    private final PlantService plantService;
    private final DiseaseService diseaseService;
    private final ScanHistoryService scanService;
    private final ChatRateLimiter rateLimiter;
    private final GeminiGateway geminiGateway;
    private final KnowledgeIndex knowledgeIndex;

    public ChatController(
            ChatService chatService,
            PlantService plantService,
            DiseaseService diseaseService,
            ScanHistoryService scanService,
            ChatRateLimiter rateLimiter,
            GeminiGateway geminiGateway,
            KnowledgeIndex knowledgeIndex) {
        this.chatService = chatService;
        this.plantService = plantService;
        this.diseaseService = diseaseService;
        this.scanService = scanService;
        this.rateLimiter = rateLimiter;
        this.geminiGateway = geminiGateway;
        this.knowledgeIndex = knowledgeIndex;
    }

    @GetMapping("/chat")
    public String chatPage(
            @RequestParam(required = false) Long conversationId,
            @RequestParam(required = false) Long plantId,
            @RequestParam(required = false) Long diseaseId,
            @RequestParam(required = false) Long scanId,
            HttpSession session,
            Model model) {

        String owner = resolveSessionOwner(session);
        List<ConversationSummaryDto> conversations = chatService.listConversations(owner);

        ConversationSummaryDto activeConv = null;
        String initialPromptSuggestion = "";

        if (conversationId != null) {
            for (ConversationSummaryDto c : conversations) {
                if (c.id().equals(conversationId)) {
                    activeConv = c;
                    break;
                }
            }
        }

        if (activeConv == null && (plantId != null || diseaseId != null || scanId != null)) {
            activeConv = chatService.createConversation(owner, plantId, diseaseId, scanId);
            conversations = chatService.listConversations(owner);
            initialPromptSuggestion = buildContextualPrompt(plantId, diseaseId, scanId);
        } else if (activeConv == null && !conversations.isEmpty()) {
            activeConv = conversations.get(0);
        } else if (activeConv == null) {
            activeConv = chatService.createConversation(owner, null, null, null);
            conversations = chatService.listConversations(owner);
        }

        List<MessageDto> messages = chatService.getMessages(activeConv.id(), owner);

        model.addAttribute("conversations", conversations);
        model.addAttribute("activeConversation", activeConv);
        model.addAttribute("messages", messages);
        model.addAttribute("plants", plantService.findAll());
        model.addAttribute("selectedPlantId", activeConv.plantId() != null ? activeConv.plantId() : plantId);
        model.addAttribute("initialPrompt", initialPromptSuggestion);
        model.addAttribute("aiAvailable", geminiGateway.isAvailable());
        model.addAttribute("activeChunksCount", knowledgeIndex.getChunkCount());
        model.addAttribute("activeIndexVersion", knowledgeIndex.getActiveIndexVersion());
        return "chat/index";
    }

    @PostMapping(value = "/api/chat/conversations", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ConversationSummaryDto> createConversation(
            @RequestBody(required = false) CreateConversationRequest req,
            HttpSession session) {
        String owner = resolveSessionOwner(session);
        Long plantId = req != null ? req.plantId() : null;
        Long diseaseId = req != null ? req.diseaseId() : null;
        Long scanId = req != null ? req.scanId() : null;
        ConversationSummaryDto created = chatService.createConversation(owner, plantId, diseaseId, scanId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping(value = "/api/chat/conversations/{id}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getConversationMessages(@PathVariable Long id, HttpSession session) {
        String owner = resolveSessionOwner(session);
        try {
            List<MessageDto> messages = chatService.getMessages(id, owner);
            return ResponseEntity.ok(messages);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Không tìm thấy hội thoại hoặc bạn không có quyền truy cập."));
        }
    }

    @PostMapping(
            value = "/api/chat/conversations/{id}/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> sendJsonMessage(
            @PathVariable Long id,
            @RequestBody SendMessageRequest req,
            HttpSession session) {
        String owner = resolveSessionOwner(session);
        if (!rateLimiter.tryAcquire(owner)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Bạn gửi tin nhắn quá nhanh (tối đa 10 yêu cầu/phút). Vui lòng đợi một chút rồi thử lại."));
        }
        try {
            ChatTurnResponseDto turn = chatService.sendMessage(
                    id,
                    owner,
                    req != null ? req.text() : "",
                    req != null ? req.plantId() : null,
                    req != null ? req.clientRequestId() : null,
                    null);
            return ResponseEntity.ok(turn);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Không tìm thấy hội thoại hoặc phiên đã hết hạn."));
        } catch (IllegalArgumentException | InvalidImageException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(
            value = "/api/chat/conversations/{id}/messages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> sendMultipartMessage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "") String text,
            @RequestParam(required = false) Long plantId,
            @RequestParam(required = false) String clientRequestId,
            @RequestParam(required = false) MultipartFile image,
            HttpSession session) {
        String owner = resolveSessionOwner(session);
        if (!rateLimiter.tryAcquire(owner)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Bạn gửi tin nhắn quá nhanh (tối đa 10 yêu cầu/phút). Vui lòng đợi một chút rồi thử lại."));
        }
        try {
            ChatTurnResponseDto turn = chatService.sendMessage(
                    id,
                    owner,
                    text,
                    plantId,
                    clientRequestId,
                    image);
            return ResponseEntity.ok(turn);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Không tìm thấy hội thoại hoặc phiên đã hết hạn."));
        } catch (IllegalArgumentException | InvalidImageException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping(value = "/api/chat/conversations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> deleteConversation(@PathVariable Long id, HttpSession session) {
        String owner = resolveSessionOwner(session);
        try {
            chatService.deleteConversation(id, owner);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Không tìm thấy hội thoại hoặc bạn không có quyền xoá."));
        }
    }

    private String resolveSessionOwner(HttpSession session) {
        Object attr = session.getAttribute(SESSION_OWNER_KEY);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        String generated = UUID.randomUUID().toString();
        session.setAttribute(SESSION_OWNER_KEY, generated);
        return generated;
    }

    private String buildContextualPrompt(Long plantId, Long diseaseId, Long scanId) {
        try {
            if (scanId != null) {
                ScanHistory scan = scanService.get(scanId);
                if (scan.getNote() != null && !scan.getNote().isBlank()) {
                    return "Nhờ phân tích ảnh lá trong lượt quét #" + scan.getId()
                            + " với ghi chú: \"" + scan.getNote() + "\". Cây đang gặp vấn đề gì và cần xử lý ra sao?";
                }
                return "Nhờ phân tích ảnh lá trong lượt quét #" + scan.getId() + " và hướng dẫn các bước chăm sóc phù hợp.";
            }
            if (diseaseId != null) {
                Disease d = diseaseService.get(diseaseId);
                String plantPart = d.getPlant() != null ? " trên cây " + d.getPlant().getName() : "";
                return "Cho tôi hỏi dấu hiệu phân biệt và cách xử lý an toàn đối với \"" + d.getName() + "\"" + plantPart + "?";
            }
            if (plantId != null) {
                var p = plantService.get(plantId);
                return "Cây " + p.getName() + " của tôi có biểu hiện bất thường trên lá, nhờ trợ lý hướng dẫn cách kiểm tra và chăm sóc.";
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public record CreateConversationRequest(Long plantId, Long diseaseId, Long scanId) {}

    public record SendMessageRequest(String text, Long plantId, String clientRequestId) {}
}

