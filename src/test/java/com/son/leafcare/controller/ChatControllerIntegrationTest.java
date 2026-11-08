package com.son.leafcare.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.son.leafcare.ai.GeminiGateway;
import com.son.leafcare.ai.GeminiGateway.GeminiChatResponse;
import com.son.leafcare.rag.KnowledgeIngestionService;
import com.son.leafcare.rag.KnowledgeIndex;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    KnowledgeIngestionService ingestionService;

    @Autowired
    KnowledgeIndex knowledgeIndex;

    @MockitoBean
    GeminiGateway geminiGateway;

    @BeforeEach
    void initKnowledge() {
        ingestionService.ingestDefaultManifest();
        knowledgeIndex.reload();
    }

    @Test
    void getChatPageRendersAndCreatesSessionConversation() throws Exception {
        when(geminiGateway.isAvailable()).thenReturn(true);
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/chat").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("chat/index"))
                .andExpect(content().string(containsString("Trợ lý AI &amp; Tra cứu bệnh cây")))
                .andExpect(content().string(containsString("Lưu ý sử dụng &amp; Quyền riêng tư")));
    }

    @Test
    void sendJsonMessageWithGeminiReturnsValidatedCitationsAndDeduplicatesRequestId() throws Exception {
        when(geminiGateway.isAvailable()).thenReturn(true);
        when(geminiGateway.embed(anyString(), any(Boolean.class))).thenReturn(new float[0]);
        when(geminiGateway.generateStructuredAnswer(anyString(), anyString(), any(), any()))
                .thenReturn(new GeminiChatResponse(
                        "Cây hoa hồng có dấu hiệu bệnh đốm đen do nấm Diplocarpon rosae. Bạn cần cắt bỏ lá bệnh và tưới vào gốc buổi sáng.",
                        false,
                        List.of("Cây trồng ở ban công có bị mưa tạt không?"),
                        List.of("ROSE-BLACK-SPOT-01", "HALLUCINATED-CHUNK-999"),
                        120,
                        85,
                        "gemini-2.5-flash"
                ));

        MockHttpSession session = new MockHttpSession();
        MvcResult createRes = mvc.perform(post("/api/chat/conversations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        String body = createRes.getResponse().getContentAsString();
        long convId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).get("id").asLong();

        String payload = """
                {
                  "text": "Lá hoa hồng có đốm tròn màu đen viền tua rua rồi vàng rụng",
                  "clientRequestId": "req-unique-101"
                }
                """;

        mvc.perform(post("/api/chat/conversations/" + convId + "/messages")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage.status").value("COMPLETED"))
                .andExpect(jsonPath("$.assistantMessage.citations.length()").value(1))
                .andExpect(jsonPath("$.assistantMessage.citations[0].chunkCode").value("ROSE-BLACK-SPOT-01"));

        // Sending the same clientRequestId again must not create duplicate messages
        mvc.perform(post("/api/chat/conversations/" + convId + "/messages")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage.citations[0].chunkCode").value("ROSE-BLACK-SPOT-01"));

        mvc.perform(get("/api/chat/conversations/" + convId + "/messages").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void sendMultipartImageMessageStoresLeafPhotoAndPassesToGemini() throws Exception {
        when(geminiGateway.isAvailable()).thenReturn(true);
        when(geminiGateway.embed(anyString(), any(Boolean.class))).thenReturn(new float[0]);
        when(geminiGateway.generateStructuredAnswer(anyString(), anyString(), any(), any()))
                .thenReturn(new GeminiChatResponse(
                        "Quan sát ảnh lá cà chua thấy vết mốc sương ở mép lá.",
                        false,
                        List.of("Mặt dưới lá có lớp mốc trắng không?"),
                        List.of("TOMATO-LATE-BLIGHT-01"),
                        250,
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
        long convId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "tomato-leaf.png", "image/png", new byte[] {1, 2, 3, 4});

        mvc.perform(multipart("/api/chat/conversations/" + convId + "/messages")
                        .file(imageFile)
                        .param("text", "Nhờ xem giúp ảnh lá cà chua này bị bệnh gì")
                        .param("clientRequestId", "req-img-202")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage.imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.assistantMessage.citations[0].chunkCode").value("TOMATO-LATE-BLIGHT-01"));
    }

    @Test
    void sessionIsolationPreventsAnotherSessionFromReadingOrDeletingConversation() throws Exception {
        MockHttpSession ownerSession = new MockHttpSession();
        MockHttpSession otherSession = new MockHttpSession();

        MvcResult createRes = mvc.perform(post("/api/chat/conversations")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        long convId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        // Other session cannot read messages
        mvc.perform(get("/api/chat/conversations/" + convId + "/messages").session(otherSession))
                .andExpect(status().isNotFound());

        // Other session cannot delete conversation
        mvc.perform(delete("/api/chat/conversations/" + convId).session(otherSession))
                .andExpect(status().isNotFound());

        // Owner session can delete conversation
        mvc.perform(delete("/api/chat/conversations/" + convId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
    }

    @Test
    void outOfScopeQueryWithoutEvidenceReturnsClarificationWithoutHallucinatedCitations() throws Exception {
        when(geminiGateway.isAvailable()).thenReturn(false); // Offline RAG mode
        MockHttpSession session = new MockHttpSession();

        MvcResult createRes = mvc.perform(post("/api/chat/conversations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        long convId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/chat/conversations/" + convId + "/messages")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Cây xương rồng sa mạc Bắc Cực bị rụng gai tím"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantMessage.status").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.assistantMessage.citations").isEmpty());
    }
}

