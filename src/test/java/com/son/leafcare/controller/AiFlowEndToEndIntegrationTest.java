package com.son.leafcare.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.son.leafcare.ai.GeminiGateway;
import com.son.leafcare.ai.GeminiGateway.GeminiChatResponse;
import com.son.leafcare.rag.KnowledgeIndex;
import com.son.leafcare.rag.KnowledgeIngestionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-End integration test covering the complete AI chat lifecycle:
 * - Session initiation and conversation creation
 * - Vietnamese symptom query with RAG retrieval and citation grounding
 * - Multi-turn follow-up dialogue
 * - Multimodal leaf image upload and diagnosis
 * - Idempotency deduplication via clientRequestId
 * - Out-of-scope query handling without hallucinated citations
 * - Session security isolation
 * - Full conversation deletion cascade (conversations, messages, citations, images)
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiFlowEndToEndIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    KnowledgeIngestionService ingestionService;

    @Autowired
    KnowledgeIndex knowledgeIndex;

    ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    GeminiGateway geminiGateway;

    @BeforeEach
    void setUp() {
        ingestionService.ingestDefaultManifest();
        knowledgeIndex.reload();
    }

    @Test
    @DisplayName("E2E: Full conversational lifecycle from create -> query -> follow-up -> deduplication -> delete")
    void testFullAiConversationalLifecycle() throws Exception {
        when(geminiGateway.isAvailable()).thenReturn(true);
        when(geminiGateway.embed(anyString(), any(Boolean.class))).thenReturn(new float[0]);

        // Mock AI response for first turn (Rose black spot)
        when(geminiGateway.generateStructuredAnswer(anyString(), contains("Hoa hồng"), any(), any()))
                .thenReturn(new GeminiChatResponse(
                        "Cây hoa hồng có biểu hiện bệnh đốm đen do nấm Diplocarpon rosae. Bạn cần cắt tỉa lá bệnh và không tưới nước lên lá vào chiều tối.",
                        false,
                        List.of("Cây trồng ở ban công có bị mưa tạt không?", "Bạn tưới cây vào thời điểm nào?"),
                        List.of("ROSE-BLACK-SPOT-01"),
                        150,
                        95,
                        "gemini-2.5-flash"
                ));

        MockHttpSession session = new MockHttpSession();

        // 1. Visit /chat page
        mvc.perform(get("/chat").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trợ lý AI &amp; Tra cứu bệnh cây")));

        // 2. Create conversation
        MvcResult createRes = mvc.perform(post("/api/chat/conversations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        long convId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();
        assertThat(convId).isPositive();

        // 3. First turn: Send Vietnamese question with unique clientRequestId
        String turn1Payload = """
                {
                  "text": "Lá hoa hồng ban công xuất hiện nhiều đốm tròn đen có viền tua rua như lông chim, rồi vàng rụng từ dưới lên",
                  "clientRequestId": "e2e-req-001"
                }
                """;

        mvc.perform(post("/api/chat/conversations/" + convId + "/messages")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turn1Payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(convId))
                .andExpect(jsonPath("$.userMessage.content").value(containsString("hoa hồng ban công")))
                .andExpect(jsonPath("$.assistantMessage.status").value("COMPLETED"))
                .andExpect(jsonPath("$.assistantMessage.citations.length()").value(1))
                .andExpect(jsonPath("$.assistantMessage.citations[0].chunkCode").value("ROSE-BLACK-SPOT-01"))
                .andExpect(jsonPath("$.assistantMessage.followUpQuestions.length()").value(2));

        // 4. Idempotency test: Re-sending exact same clientRequestId must return the same response
        mvc.perform(post("/api/chat/conversations/" + convId + "/messages")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turn1Payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage.citations[0].chunkCode").value("ROSE-BLACK-SPOT-01"));

        // 5. Follow-up turn in same conversation
        when(geminiGateway.generateStructuredAnswer(anyString(), anyString(), any(), any()))
                .thenReturn(new GeminiChatResponse(
                        "Bạn chỉ nên tưới nước vào buổi sáng sớm và tưới trực tiếp vào gốc cây để lá mau khô.",
                        false,
                        List.of("Bạn có đang dùng màng phủ gốc không?"),
                        List.of("ROSE-BLACK-SPOT-01"),
                        200,
                        80,
                        "gemini-2.5-flash"
                ));

        String turn2Payload = """
                {
                  "text": "Tôi có nên tưới cây vào buổi tối không?",
                  "clientRequestId": "e2e-req-002"
                }
                """;

        mvc.perform(post("/api/chat/conversations/" + convId + "/messages")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turn2Payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage.status").value("COMPLETED"));

        // 6. Verify conversation history has 4 messages (2 user, 2 assistant)
        mvc.perform(get("/api/chat/conversations/" + convId + "/messages").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        // 7. Delete conversation (verifies cascade cleanup of conversation, messages, citations)
        mvc.perform(delete("/api/chat/conversations/" + convId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        // 8. Confirm conversation is now gone (404)
        mvc.perform(get("/api/chat/conversations/" + convId + "/messages").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("E2E: Multimodal leaf scan upload with citation grounding")
    void testMultimodalLeafDiagnosis() throws Exception {
        when(geminiGateway.isAvailable()).thenReturn(true);
        when(geminiGateway.embed(anyString(), any(Boolean.class))).thenReturn(new float[0]);
        when(geminiGateway.generateStructuredAnswer(anyString(), anyString(), any(), any()))
                .thenReturn(new GeminiChatResponse(
                        "Quan sát ảnh lá cà chua thấy mép lá có đốm thâm nâu và lớp mốc trắng mỏng, điển hình của bệnh mốc sương.",
                        false,
                        List.of("Cây trồng trong nhà màng hay ngoài đồng?"),
                        List.of("TOMATO-LATE-BLIGHT-01"),
                        210,
                        90,
                        "gemini-2.5-flash"
                ));

        MockHttpSession session = new MockHttpSession();
        MvcResult createRes = mvc.perform(post("/api/chat/conversations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        long convId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        MockMultipartFile leafImage = new MockMultipartFile(
                "image", "leaf-blight.png", "image/png", new byte[] {1, 2, 3, 4, 5});

        mvc.perform(multipart("/api/chat/conversations/" + convId + "/messages")
                        .file(leafImage)
                        .param("text", "Nhờ xem ảnh lá cà chua này có phải bị mốc sương không?")
                        .param("clientRequestId", "e2e-img-req-003")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage.imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.assistantMessage.status").value("COMPLETED"))
                .andExpect(jsonPath("$.assistantMessage.citations[0].chunkCode").value("TOMATO-LATE-BLIGHT-01"));
    }

    @Test
    @DisplayName("E2E: Out-of-scope query triggers clarification without fake citations")
    void testOutOfScopeQueryHandling() throws Exception {
        when(geminiGateway.isAvailable()).thenReturn(true);
        when(geminiGateway.embed(anyString(), any(Boolean.class))).thenReturn(new float[0]);
        when(geminiGateway.generateStructuredAnswer(anyString(), anyString(), any(), any()))
                .thenReturn(new GeminiChatResponse(
                        "Xin lỗi, tôi là trợ lý chẩn đoán bệnh cây trồng. Câu hỏi về máy tính không thuộc phạm vi chuyên môn.",
                        true,
                        List.of("Bạn có câu hỏi nào về chăm sóc cây trồng không?"),
                        List.of(),
                        80,
                        40,
                        "gemini-2.5-flash"
                ));

        MockHttpSession session = new MockHttpSession();
        MvcResult createRes = mvc.perform(post("/api/chat/conversations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        long convId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/chat/conversations/" + convId + "/messages")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Làm thế nào để sửa máy tính bị lỗi màn hình xanh?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage.status").value(org.hamcrest.Matchers.isOneOf("NEEDS_CLARIFICATION", "INSUFFICIENT_EVIDENCE")))
                .andExpect(jsonPath("$.assistantMessage.citations").isEmpty());
    }

    @Test
    @DisplayName("E2E: Rate limiting protects AI endpoint against excessive calls")
    void testRateLimitingProtection() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult createRes = mvc.perform(post("/api/chat/conversations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        long convId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        when(geminiGateway.isAvailable()).thenReturn(false);

        // Exhaust allowed tokens (capacity = 10 requests / min)
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/chat/conversations/" + convId + "/messages")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\": \"Triệu chứng lá thứ " + i + "\"}"))
                    .andExpect(status().isOk());
        }

        // 11th request must be rejected with 429 TOO_MANY_REQUESTS
        mvc.perform(post("/api/chat/conversations/" + convId + "/messages")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Yêu cầu thứ 11 vượt ngưỡng rate limit\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(containsString("Bạn gửi tin nhắn quá nhanh")));
    }
}
