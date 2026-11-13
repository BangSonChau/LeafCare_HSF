package com.son.leafcare.rag;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.user.AppUserService;
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
                .andExpect(content().string(not(containsString("<div class=\"mt-4\" data-server-result"))));
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
    void boldMarkdownIsRenderedButOtherHtmlStaysEscaped() throws Exception {
        when(chat.ask("q")).thenReturn(new RagAnswer("**1. Cách nhận biết:** <i>x</i>", List.of()));

        mvc.perform(post("/chat").with(csrf()).param("question", "q"))
                .andExpect(content().string(containsString("<strong>1. Cách nhận biết:</strong> &lt;i&gt;x&lt;/i&gt;")));
    }

    @Test
    void busyModelStillShowsHandbookExcerpts() throws Exception {
        when(chat.ask("q")).thenReturn(new RagAnswer(null,
                List.of(new RagAnswer.Source(1, "ca-chua.txt", "Bệnh đốm vòng...", 0.8))));

        mvc.perform(post("/chat").with(csrf()).param("question", "q"))
                .andExpect(content().string(containsString("Máy trả lời đang quá tải")))
                .andExpect(content().string(containsString("Bệnh đốm vòng...")))
                .andExpect(content().string(not(containsString("<article class="))));
    }

    @Test
    void emptyAnswerListHidesSourcesBlock() throws Exception {
        when(chat.ask("q")).thenReturn(new RagAnswer("Tài liệu hiện có chưa đề cập vấn đề này.", List.of()));

        mvc.perform(post("/chat").with(csrf()).param("question", "q"))
                .andExpect(content().string(containsString("chưa đề cập")))
                .andExpect(content().string(not(containsString("<ol class="))));
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
                // 1 lần trong khung báo lỗi của layout + 1 lần trong JS (câu dự phòng khi mất mạng)
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s)^(?:(?!thử lại sau).)*(?:thử lại sau(?:(?!thử lại sau).)*){2}$")))
                .andExpect(content().string(containsString(">Cà chua?</textarea>")))
                .andExpect(content().string(not(containsString("quota"))));
    }

    @Test
    @WithMockUser
    void postWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/chat").param("question", "x")).andExpect(status().isForbidden());
        verifyNoInteractions(chat);
    }

    // --- trang mở sẵn câu hỏi + streaming ---

    @Test
    void questionFromQueryIsPrefilledAndAskedAutomatically() throws Exception {
        mvc.perform(get("/chat").param("q", " Cách xử lý đốm vòng? "))
                .andExpect(content().string(containsString(">Cách xử lý đốm vòng?</textarea>")))
                .andExpect(content().string(containsString("data-auto-ask=\"true\"")));
        verifyNoInteractions(chat);
    }

    @Test
    void overlongQueryIsIgnored() throws Exception {
        mvc.perform(get("/chat").param("q", "a".repeat(501)))
                .andExpect(content().string(containsString("data-auto-ask=\"false\"")))
                .andExpect(content().string(not(containsString("aaaa"))));
    }

    String streamed(String question) throws Exception {
        var result = mvc.perform(post("/chat/stream").with(csrf()).param("question", question))
                .andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(5000);
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void streamSendsSourcesThenTokensThenDone() throws Exception {
        when(chat.prepare("q")).thenReturn(new RagChatService.Prepared("prompt",
                List.of(new RagAnswer.Source(1, "ca-chua.txt", "Đốm vòng", 0.8))));
        when(chat.stream("prompt")).thenReturn(reactor.core.publisher.Flux.just("Cắt ", "lá [1]"));

        String body = streamed("q");

        assertOrder(body, "event:sources", "ca-chua.txt", "event:token", "Cắt ", "lá [1]", "event:done");
    }

    @Test
    void streamFailureAfterSourcesSendsBusy() throws Exception {
        when(chat.prepare("q")).thenReturn(new RagChatService.Prepared("prompt",
                List.of(new RagAnswer.Source(1, "ca-chua.txt", "Đốm vòng", 0.8))));
        when(chat.stream("prompt")).thenReturn(reactor.core.publisher.Flux.error(new RuntimeException("503")));

        assertOrder(streamed("q"), "event:sources", "event:busy");
    }

    @Test
    void streamWithNothingInTheHandbookSaysSo() throws Exception {
        when(chat.prepare("q")).thenReturn(new RagChatService.Prepared(null, List.of()));

        assertOrder(streamed("q"), "event:sources", "chưa đề cập", "event:done");
    }

    @Test
    void streamSearchFailureSendsFriendlyError() throws Exception {
        when(chat.prepare(any())).thenThrow(new RuntimeException("quota"));

        String body = streamed("q");
        assertOrder(body, "event:error", "thử lại sau");
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("quota");
    }

    @Test
    void streamRejectsBlankQuestionWithoutCallingTheService() throws Exception {
        String body = mvc.perform(post("/chat/stream").with(csrf()).param("question", "  "))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertOrder(body, "event:error", "1 đến 500 ký tự");
        verifyNoInteractions(chat);
    }

    @Test
    void streamWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/chat/stream").param("question", "x")).andExpect(status().isForbidden());
        verifyNoInteractions(chat);
    }

    static void assertOrder(String body, String... parts) {
        int at = 0;
        for (String p : parts) {
            int i = body.indexOf(p, at);
            org.assertj.core.api.Assertions.assertThat(i).as("'%s' after %d in:%n%s", p, at, body).isGreaterThanOrEqualTo(0);
            at = i + p.length();
        }
    }
}
