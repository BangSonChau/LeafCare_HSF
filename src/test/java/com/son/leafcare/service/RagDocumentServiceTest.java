package com.son.leafcare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;

class RagDocumentServiceTest {

    private final VectorStore store = mock(VectorStore.class);
    private final RagDocumentService service = new RagDocumentService(store, 0);

    private static List<Document> docs(int n) {
        List<Document> d = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            d.add(new Document("đoạn " + i));
        }
        return d;
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestStoresChunksTaggedWithFileName() throws Exception {
        var file = new ByteArrayResource("Bệnh đốm lá do nấm gây ra.".getBytes(StandardCharsets.UTF_8));

        int n = service.ingest(file, "benh-la.md");

        ArgumentCaptor<List<Document>> c = ArgumentCaptor.forClass(List.class);
        verify(store).add(c.capture());
        assertThat(n).isEqualTo(c.getValue().size()).isPositive();
        assertThat(c.getValue()).allMatch(d -> "benh-la.md".equals(d.getMetadata().get("source")));
    }

    @Test
    void emptyFileStoresNothing() throws Exception {
        assertThat(service.split(new ByteArrayResource(new byte[0]), "rong.txt")).isEmpty();
        assertThat(service.ingest(new ByteArrayResource(new byte[0]), "rong.txt")).isZero();
        verifyNoInteractions(store);
    }

    @Test
    @SuppressWarnings("unchecked")
    void storeSendsBatchesOfTwentyAndReportsProgress() {
        List<Integer> progress = new ArrayList<>();

        service.store(docs(45), progress::add);

        ArgumentCaptor<List<Document>> c = ArgumentCaptor.forClass(List.class);
        verify(store, times(3)).add(c.capture());
        assertThat(c.getAllValues()).extracting(List::size).containsExactly(20, 20, 5);
        assertThat(progress).containsExactly(20, 40, 45);
    }

    @Test
    void storeRetriesAFailedBatchThenSucceeds() {
        doThrow(new RuntimeException("429")).doThrow(new RuntimeException("429")).doNothing().when(store).add(anyList());
        List<Integer> progress = new ArrayList<>();

        service.store(docs(5), progress::add);

        verify(store, times(3)).add(anyList());
        assertThat(progress).containsExactly(5);
    }

    @Test
    void storeGivesUpAfterMaxAttemptsAndKeepsEarlierBatches() {
        // first batch succeeds, second always fails
        doNothing().doThrow(new RuntimeException("quota")).when(store).add(anyList());
        List<Integer> progress = new ArrayList<>();

        assertThatThrownBy(() -> service.store(docs(30), progress::add)).hasMessage("quota");

        verify(store, times(1 + RagDocumentService.MAX_ATTEMPTS)).add(anyList());
        assertThat(progress).containsExactly(20);
    }
}
