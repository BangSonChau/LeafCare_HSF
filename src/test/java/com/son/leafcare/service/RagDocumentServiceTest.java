package com.son.leafcare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;

class RagDocumentServiceTest {

    private final VectorStore store = mock(VectorStore.class);
    private final RagDocumentService service = new RagDocumentService(store);

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
        int n = service.ingest(new ByteArrayResource(new byte[0]), "rong.txt");

        assertThat(n).isZero();
        verifyNoInteractions(store);
    }
}
