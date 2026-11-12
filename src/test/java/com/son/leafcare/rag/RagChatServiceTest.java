package com.son.leafcare.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

        RagAnswer a = new RagChatService(store, builder).ask("Cách trồng sầu riêng?");

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

        new RagChatService(store, builder).ask("q");

        org.mockito.ArgumentCaptor<SearchRequest> c = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(c.capture());
        assertThat(c.getValue().getTopK()).isEqualTo(4);
        assertThat(c.getValue().getSimilarityThreshold()).isEqualTo(0.3);
    }
}
