package com.son.leafcare.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.service.AppUserService;
import com.son.leafcare.service.RagAnswer;
import com.son.leafcare.service.RagChatService;
import com.son.leafcare.service.RagDocumentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RagController.class)
@Import(SecurityConfig.class)
class RagControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RagChatService chat;
    @MockitoBean RagDocumentService documents;
    @MockitoBean AppUserService users;

    private static final MockMultipartFile FILE =
            new MockMultipartFile("file", "benh.md", "text/markdown", "abc".getBytes());

    @Test
    void userCanAsk() throws Exception {
        when(chat.ask("Bệnh gì?")).thenReturn(new RagAnswer("Đốm lá [1]", List.of()));

        mvc.perform(post("/api/rag/chat").with(csrf()).with(user("u@x.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  Bệnh gì? \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Đốm lá [1]"));
    }

    @Test
    void blankOrTooLongQuestionIsRejected() throws Exception {
        for (String q : new String[] {"   ", "a".repeat(501)}) {
            mvc.perform(post("/api/rag/chat").with(csrf()).with(user("u@x.com").roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"" + q + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(chat);
    }

    @Test
    void anonymousCannotAsk() throws Exception {
        mvc.perform(post("/api/rag/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(chat);
    }

    @Test
    void adminCanUpload() throws Exception {
        when(documents.ingest(any(), eq("benh.md"))).thenReturn(3);

        mvc.perform(multipart("/api/rag/documents").file(FILE).with(csrf()).with(user("a@x.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunks").value(3));
    }

    @Test
    void plainUserCannotUpload() throws Exception {
        mvc.perform(multipart("/api/rag/documents").file(FILE).with(csrf()).with(user("u@x.com").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documents);
    }
}
