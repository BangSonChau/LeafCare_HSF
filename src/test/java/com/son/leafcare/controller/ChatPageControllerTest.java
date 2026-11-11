package com.son.leafcare.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.service.AppUserService;
import com.son.leafcare.service.RagAnswer;
import com.son.leafcare.service.RagChatService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatPageController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "u@x.com")
class ChatPageControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RagChatService chat;
    @MockitoBean AppUserService users;

    @Test
    void formShowsWithoutAnswer() throws Exception {
        mvc.perform(get("/chat")).andExpect(status().isOk()).andExpect(view().name("chat/index"))
                .andExpect(content().string(containsString("Hỏi đáp bệnh lá")))
                .andExpect(content().string(not(containsString("Trả lời"))));
    }

    @Test
    void answerAndSourcesAreShownAndEscaped() throws Exception {
        when(chat.ask("Đốm nâu?")).thenReturn(new RagAnswer("Cắt lá bệnh [1] <b>x</b>",
                List.of(new RagAnswer.Source(1, "ca-chua.txt", "Bệnh đốm vòng...", 0.8))));

        mvc.perform(post("/chat").with(csrf()).param("question", "  Đốm nâu? "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cắt lá bệnh [1] &lt;b&gt;x&lt;/b&gt;")))
                .andExpect(content().string(containsString("ca-chua.txt")))
                .andExpect(content().string(containsString("Bệnh đốm vòng...")));
    }

    @Test
    void emptyAnswerListHidesSourcesBlock() throws Exception {
        when(chat.ask("q")).thenReturn(new RagAnswer("Tài liệu hiện có chưa đề cập vấn đề này.", List.of()));

        mvc.perform(post("/chat").with(csrf()).param("question", "q"))
                .andExpect(content().string(containsString("chưa đề cập")))
                .andExpect(content().string(not(containsString("Nguồn"))));
    }

    @Test
    void blankOrTooLongQuestionShowsErrorWithoutCallingLlm() throws Exception {
        for (String q : new String[] {"   ", "a".repeat(501)}) {
            mvc.perform(post("/chat").with(csrf()).param("question", q))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("1 đến 500 ký tự")));
        }
        verifyNoInteractions(chat);
    }

    @Test
    void llmFailureShowsFriendlyErrorAndKeepsQuestion() throws Exception {
        when(chat.ask(any())).thenThrow(new RuntimeException("quota"));

        mvc.perform(post("/chat").with(csrf()).param("question", "Cà chua?"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("thử lại sau")))
                .andExpect(content().string(containsString(">Cà chua?</textarea>")))
                .andExpect(content().string(not(containsString("quota"))));
    }

    @Test
    @WithMockUser
    void postWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/chat").param("question", "x")).andExpect(status().isForbidden());
        verifyNoInteractions(chat);
    }
}
