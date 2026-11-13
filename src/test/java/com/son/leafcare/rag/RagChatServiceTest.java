package com.son.leafcare.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class RagChatServiceTest {

    @Test
    void promptNumbersEachChunkAndEndsWithQuestion() {
        String prompt = RagChatService.buildUserPrompt("Bệnh gì?",
                List.of(new Document("Đốm vòng"), new Document("Mốc sương")));

        assertThat(prompt).contains("[1] Đốm vòng").contains("[2] Mốc sương").endsWith("CÂU HỎI: Bệnh gì?");
    }

    @Test
    void sourcesKeepFileNameAndShortenLongText() {
        Document longDoc = Document.builder().text("a".repeat(300))
                .metadata(Map.of("source", "benh-la.md")).score(0.8).build();

        List<RagAnswer.Source> sources = RagChatService.toSources(List.of(longDoc));

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).number()).isEqualTo(1);
        assertThat(sources.get(0).file()).isEqualTo("benh-la.md");
        assertThat(sources.get(0).excerpt()).hasSize(203).endsWith("...");
    }

    @Test
    void sourceWithoutMetadataFallsBackToQuestionMark() {
        assertThat(RagChatService.toSources(List.of(new Document("x"))).get(0).file()).isEqualTo("?");
    }

    @Test
    void askWithNoMatchingChunksSkipsTheLlm() {
        VectorStore store = mock(VectorStore.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class, org.mockito.Mockito.RETURNS_SELF);
        ChatClient client = mock(ChatClient.class);
        when(builder.build()).thenReturn(client);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        RagAnswer a = new RagChatService(store, builder, Duration.ofSeconds(5), "").ask("Cách trồng sầu riêng?");

        assertThat(a.answer()).isEqualTo(RagChatService.NO_INFO);
        assertThat(a.sources()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void askSearchesWithThresholdAndTopK() {
        VectorStore store = mock(VectorStore.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class, org.mockito.Mockito.RETURNS_SELF);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        new RagChatService(store, builder, Duration.ofSeconds(5), "").ask("q");

        org.mockito.ArgumentCaptor<SearchRequest> c = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(c.capture());
        assertThat(c.getValue().getTopK()).isEqualTo(4);
        assertThat(c.getValue().getSimilarityThreshold()).isEqualTo(0.3);
    }

    // --- chịu lỗi khi Gemini quá tải ---

    VectorStore storeWithOneChunk() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                Document.builder().text("Đốm vòng: cắt lá bệnh").metadata(Map.of("source", "ca-chua.txt")).build()));
        return store;
    }

    ChatClient.Builder builderOf(ChatClient client) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, org.mockito.Mockito.RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        return builder;
    }

    @Test
    void mainModelAnswerIsReturnedWithSources() {
        ChatClient client = mock(ChatClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(client.prompt().user(org.mockito.ArgumentMatchers.anyString()).call().content()).thenReturn("Cắt lá [1]");

        RagAnswer a = new RagChatService(storeWithOneChunk(), builderOf(client), Duration.ofSeconds(5), "").ask("q");

        assertThat(a.answer()).isEqualTo("Cắt lá [1]");
        assertThat(a.sources()).hasSize(1);
    }

    @Test
    void mainModelFailureFallsBackToTheSecondModel() {
        ChatClient client = mock(ChatClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(client.prompt().user(org.mockito.ArgumentMatchers.anyString()).call().content())
                .thenThrow(new RuntimeException("503 This model is currently experiencing high demand"));
        when(client.prompt().options(any()).user(org.mockito.ArgumentMatchers.anyString()).call().content())
                .thenReturn("Trả lời dự phòng [1]");

        RagAnswer a = new RagChatService(storeWithOneChunk(), builderOf(client), Duration.ofSeconds(5), "lite")
                .ask("q");

        assertThat(a.answer()).isEqualTo("Trả lời dự phòng [1]");
    }

    @Test
    void allModelsFailingStillReturnsTheHandbookExcerpts() {
        ChatClient client = mock(ChatClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(client.prompt().user(org.mockito.ArgumentMatchers.anyString()).call().content())
                .thenThrow(new RuntimeException("503"));

        RagAnswer a = new RagChatService(storeWithOneChunk(), builderOf(client), Duration.ofSeconds(5), "").ask("q");

        assertThat(a.answer()).isNull();
        assertThat(a.sources()).extracting(RagAnswer.Source::file).containsExactly("ca-chua.txt");
    }

    @Test
    void slowModelIsCutOffAtTheTimeout() {
        ChatClient client = mock(ChatClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(client.prompt().user(org.mockito.ArgumentMatchers.anyString()).call().content()).thenAnswer(inv -> {
            Thread.sleep(10_000);
            return "quá muộn";
        });

        long start = System.nanoTime();
        RagAnswer a = new RagChatService(storeWithOneChunk(), builderOf(client), Duration.ofMillis(300), "").ask("q");

        assertThat(a.answer()).isNull();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void repeatedFailuresOpenTheBreakerSoTheMainModelIsSkipped() {
        ChatClient client = mock(ChatClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(client.prompt().user(org.mockito.ArgumentMatchers.anyString()).call().content()).thenAnswer(inv -> {
            calls.incrementAndGet();
            throw new RuntimeException("503");
        });
        RagChatService service = new RagChatService(storeWithOneChunk(), builderOf(client), Duration.ofSeconds(5), "");

        for (int i = 0; i < 5; i++) {
            service.ask("q");
        }

        assertThat(service.breaker.getState())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void shortReasonKeepsOneLineFromTheRootCause() {
        String html = "<!DOCTYPE html>" + "x".repeat(5000);
        String reason = RagChatService.shortReason(new RuntimeException("wrap", new IllegalStateException(html)));

        assertThat(reason).startsWith("IllegalStateException: <!DOCTYPE html>").hasSizeLessThan(260);
    }

    @Test
    void toHtmlEscapesThenBoldsMarkdown() {
        assertThat(RagChatService.toHtml("**Chú ý:** <script>x</script> a**b"))
                .isEqualTo("<strong>Chú ý:</strong> &lt;script&gt;x&lt;/script&gt; a**b");
        assertThat(RagChatService.toHtml(null)).isNull();
    }
}
