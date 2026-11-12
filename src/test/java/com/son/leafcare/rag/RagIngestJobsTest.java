package com.son.leafcare.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;

class RagIngestJobsTest {

    private final RagDocumentService documents = mock(RagDocumentService.class);
    private final RagIngestJobs jobs = new RagIngestJobs(documents, Runnable::run); // chạy ngay trong luồng test
    private final ByteArrayResource file = new ByteArrayResource(new byte[] {1});

    @Test
    void successfulJobEndsDoneWithFullProgress() throws Exception {
        when(documents.split(any(), eq("a.pdf"))).thenReturn(List.of(new Document("x"), new Document("y")));
        doAnswer(inv -> {
            ((IntConsumer) inv.getArgument(1)).accept(2);
            return null;
        }).when(documents).store(any(), any());

        var job = jobs.start(file, "a.pdf");

        assertThat(job.getStatus()).isEqualTo(RagIngestJobs.Status.DONE);
        assertThat(job.getTotal()).isEqualTo(2);
        assertThat(job.getDone()).isEqualTo(2);
        assertThat(job.isRunning()).isFalse();
    }

    @Test
    void fileWithoutTextFailsWithClearMessage() throws Exception {
        when(documents.split(any(), any())).thenReturn(List.of());

        var job = jobs.start(file, "scan.pdf");

        assertThat(job.getStatus()).isEqualTo(RagIngestJobs.Status.FAILED);
        assertThat(job.getMessage()).contains("Không đọc được chữ");
    }

    @Test
    void failureAfterSomeBatchesReportsPartialProgress() throws Exception {
        when(documents.split(any(), any())).thenReturn(List.of(new Document("x"), new Document("y"), new Document("z")));
        doAnswer(inv -> {
            ((IntConsumer) inv.getArgument(1)).accept(2);
            throw new RuntimeException("quota");
        }).when(documents).store(any(), any());

        var job = jobs.start(file, "a.pdf");

        assertThat(job.getStatus()).isEqualTo(RagIngestJobs.Status.FAILED);
        assertThat(job.getDone()).isEqualTo(2);
        assertThat(job.getMessage()).contains("2/3").doesNotContain("quota");
    }

    @Test
    void parseFailureFailsWithoutLeakingDetails() throws Exception {
        doThrow(new RuntimeException("tika boom")).when(documents).split(any(), any());

        var job = jobs.start(file, "x.bin");

        assertThat(job.getStatus()).isEqualTo(RagIngestJobs.Status.FAILED);
        assertThat(job.getMessage()).doesNotContain("boom");
    }

    @Test
    void outOfMemoryDuringReadFailsInsteadOfLeavingTheJobStuck() throws Exception {
        doThrow(new OutOfMemoryError("heap")).when(documents).split(any(), any());

        var job = jobs.start(file, "big.pdf");

        assertThat(job.getStatus()).isEqualTo(RagIngestJobs.Status.FAILED);
        assertThat(job.isRunning()).isFalse();
        assertThat(job.getMessage()).contains("Hết bộ nhớ");
    }

    @Test
    void elapsedTimeStopsCountingOnceFinished() throws Exception {
        when(documents.split(any(), any())).thenReturn(List.of());

        var job = jobs.start(file, "a.pdf");

        assertThat(job.getElapsedSeconds()).isZero();
        Thread.sleep(1100);
        assertThat(job.getElapsedSeconds()).isZero(); // đã dừng đếm từ lúc lỗi
    }

    @Test
    void recentIsNewestFirstAndKeepsOnlyTheLastTen() throws Exception {
        when(documents.split(any(), any())).thenReturn(List.of());

        for (int i = 0; i < 12; i++) {
            jobs.start(file, "f" + i);
        }

        assertThat(jobs.recent()).hasSize(RagIngestJobs.KEEP);
        assertThat(jobs.recent().get(0).getFileName()).isEqualTo("f11");
        assertThat(jobs.recent().get(9).getFileName()).isEqualTo("f2");
    }
}
