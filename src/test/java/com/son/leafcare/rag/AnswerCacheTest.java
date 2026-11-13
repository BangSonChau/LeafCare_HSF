package com.son.leafcare.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

class AnswerCacheTest {

    VectorStore store = mock(VectorStore.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    AnswerCache cache = new AnswerCache(store, jdbc, 0.92);

    @Test
    void hitReturnsTheStoredAnswerAndSources() {
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("câu cũ", Map.of(
                "answer", "Cắt lá bệnh [1]",
                "sources", List.of(Map.of("number", 1, "file", "ca-chua.txt", "excerpt", "Đốm vòng"))))));

        RagAnswer a = cache.find("cà chua bị đốm vòng trị sao?").orElseThrow();

        assertThat(a.answer()).isEqualTo("Cắt lá bệnh [1]");
        assertThat(a.sources()).extracting(RagAnswer.Source::file).containsExactly("ca-chua.txt");
        ArgumentCaptor<SearchRequest> c = ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(c.capture());
        assertThat(c.getValue().getSimilarityThreshold()).isEqualTo(0.92);
        assertThat(c.getValue().getTopK()).isEqualTo(1);
    }

    @Test
    void missOrBrokenStoreMeansNoCache() {
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        assertThat(cache.find("q")).isEmpty();

        when(store.similaritySearch(any(SearchRequest.class))).thenThrow(new RuntimeException("db down"));
        assertThat(cache.find("q")).isEmpty();
    }

    @Test
    void putStoresQuestionWithAnswerAndSources() {
        cache.put("Cách xử lý đốm vòng?", "Cắt lá [1]", List.of(new RagAnswer.Source(1, "ca-chua.txt", "Đốm", 0.8)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> c = ArgumentCaptor.forClass(List.class);
        verify(store).add(c.capture());
        Document d = c.getValue().get(0);
        assertThat(d.getText()).isEqualTo("Cách xử lý đốm vòng?");
        assertThat(d.getMetadata()).containsEntry("answer", "Cắt lá [1]");
        assertThat(AnswerCache.toAnswer(d).sources()).extracting(RagAnswer.Source::excerpt).containsExactly("Đốm");
    }

    @Test
    void emptyOrNoInfoAnswersAreNotCached() {
        cache.put("q", null, List.of());
        cache.put("q", "  ", List.of());
        cache.put("q", RagChatService.NO_INFO, List.of());
        verifyNoInteractions(store);
    }

    @Test
    void clearDeletesEverything() {
        cache.clear();
        verify(jdbc).update("DELETE FROM rag_answer_cache");
    }

    @Test
    void disabledCacheDoesNothing() {
        AnswerCache off = new AnswerCache(null, null, 0.92);
        assertThat(off.find("q")).isEmpty();
        off.put("q", "a", List.of());
        off.clear();
    }

    @Test
    void failingSaveOrClearIsSwallowed() {
        org.mockito.Mockito.doThrow(new RuntimeException("x")).when(store).add(any());
        when(jdbc.update(anyString())).thenThrow(new RuntimeException("x"));
        cache.put("q", "a", List.of());
        cache.clear();
    }
}
