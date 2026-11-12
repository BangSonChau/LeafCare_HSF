package com.son.leafcare.service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Nạp tài liệu lớn ở nền để request không bị timeout (Render cắt request quá lâu, và Gemini giới hạn token mỗi phút).
 * shortcut: trạng thái chỉ nằm trong RAM và chạy lần lượt từng file; khởi động lại server thì mất danh sách,
 * nâng lên bảng DB khi cần xem lịch sử nạp.
 */
@Service
public class RagIngestJobs {

    public enum Status {
        READING("Đang đọc file"), STORING("Đang nạp"), DONE("Xong"), FAILED("Lỗi");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** Một lần nạp. Các trường được luồng nền ghi và luồng web đọc nên đều volatile. */
    public static class Job {
        private final String fileName;
        private volatile Status status = Status.READING;
        private volatile int total;
        private volatile int done;
        private volatile String message = "";
        private final Instant startedAt = Instant.now();
        private volatile Instant finishedAt;

        public Job(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() { return fileName; }
        public Status getStatus() { return status; }
        public int getTotal() { return total; }
        public int getDone() { return done; }
        public String getMessage() { return message; }
        public boolean isRunning() { return status == Status.READING || status == Status.STORING; }

        /** Thời gian đã chạy (dừng đếm khi xong hoặc lỗi), để admin biết nên chờ tiếp hay đi xem log. */
        public long getElapsedSeconds() {
            Instant end = finishedAt != null ? finishedAt : Instant.now();
            return Duration.between(startedAt, end).toSeconds();
        }

        private void finish(Status s) {
            finishedAt = Instant.now();
            status = s;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(RagIngestJobs.class);
    static final int KEEP = 10;

    private final RagDocumentService documents;
    private final Executor executor;
    private final ExecutorService ownExecutor; // null khi test truyền executor riêng
    private final List<Job> jobs = new CopyOnWriteArrayList<>(); // mới nhất ở đầu

    @Autowired
    public RagIngestJobs(RagDocumentService documents) {
        this.documents = documents;
        this.ownExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rag-ingest");
            t.setDaemon(true);
            return t;
        });
        this.executor = ownExecutor;
    }

    RagIngestJobs(RagDocumentService documents, Executor executor) {
        this.documents = documents;
        this.executor = executor;
        this.ownExecutor = null;
    }

    /** Nhận file (đã nằm trong bộ nhớ) và nạp ở nền. */
    public Job start(Resource file, String fileName) {
        Job job = new Job(fileName);
        jobs.add(0, job);
        while (jobs.size() > KEEP) {
            jobs.remove(jobs.size() - 1);
        }
        executor.execute(() -> run(job, file));
        return job;
    }

    /** Các lần nạp gần đây, mới nhất trước. */
    public List<Job> recent() {
        return List.copyOf(jobs);
    }

    private void run(Job job, Resource file) {
        try {
            var chunks = documents.split(file, job.fileName);
            if (chunks.isEmpty()) {
                job.message = "Không đọc được chữ trong file. PDF scan hoặc ảnh thì chưa nạp được.";
                job.finish(Status.FAILED);
                return;
            }
            job.total = chunks.size();
            job.status = Status.STORING;
            documents.store(chunks, n -> job.done = n);
            job.finish(Status.DONE);
        } catch (Throwable e) { // cả OutOfMemoryError: nếu không bắt, luồng nền chết im lặng và job kẹt ở "Đang đọc file" mãi
            log.warn("RAG ingest failed for {}", job.fileName, e);
            job.message = e instanceof OutOfMemoryError
                    ? "Hết bộ nhớ khi đọc file (server gói Free chỉ có 512 MB). Thử tách file nhỏ hơn rồi nạp từng phần."
                    : job.done > 0
                    ? "Dừng giữa chừng: đã lưu " + job.done + "/" + job.total + " đoạn. Thử lại sau sẽ lưu trùng các đoạn này."
                    : "Không nạp được. Kiểm tra định dạng file hoặc thử lại sau.";
            job.finish(Status.FAILED);
        }
    }

    @PreDestroy
    void shutdown() {
        if (ownExecutor != null) {
            ownExecutor.shutdownNow();
        }
    }
}
