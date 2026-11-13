package com.son.leafcare.rag;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Flux;

/**
 * Tìm đoạn liên quan -> đưa cho LLM -> trả lời kèm nguồn.
 * Gemini bản free hay quá tải (503), nên mỗi lần gọi có giới hạn thời gian, có cầu dao (lỗi liên tục thì
 * tạm ngừng gọi model chính 1 phút), có model dự phòng; hỏng hết thì vẫn trả các đoạn cẩm nang (answer = null).
 */
@Service
public class RagChatService {

    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);
    static final String NO_INFO = "Tài liệu hiện có chưa đề cập vấn đề này.";
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");

    static final String SYSTEM_PROMPT = """
            Bạn là trợ lý LeafCare về bệnh lá cây. Chỉ trả lời dựa trên TÀI LIỆU được cung cấp.
            Sau mỗi ý, ghi số nguồn trong ngoặc vuông, ví dụ [1] hoặc [1][3].
            Nếu tài liệu không có thông tin, nói rõ: "%s"
            Trả lời bằng tiếng Việt, ngắn gọn.""".formatted(NO_INFO);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final TimeLimiter timeLimiter;
    private final Duration timeout;
    private final String fallbackModel;
    final CircuitBreaker breaker;
    // 4 luồng: tối đa 4 câu hỏi chờ Gemini cùng lúc, để server 512 MB không cạn luồng xử lý web
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "gemini-call");
        t.setDaemon(true);
        return t;
    });

    public RagChatService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder,
                          @Value("${leafcare.rag.timeout:25s}") Duration timeout,
                          @Value("${leafcare.rag.fallback-model:}") String fallbackModel) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.timeLimiter = TimeLimiter.of(timeout);
        this.timeout = timeout;
        this.fallbackModel = fallbackModel == null || fallbackModel.isBlank() ? null : fallbackModel.trim();
        // 3 trong 5 lần gần nhất lỗi -> ngắt 60s, rồi cho thử 1 lần
        this.breaker = CircuitBreaker.of("gemini", CircuitBreakerConfig.custom()
                .slidingWindowSize(5).minimumNumberOfCalls(3).failureRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(60)).permittedNumberOfCallsInHalfOpenState(1)
                .build());
    }

    public RagAnswer ask(String question) {
        Prepared p = prepare(question);
        if (p.prompt() == null) {
            return new RagAnswer(NO_INFO, List.of());
        }
        String prompt = p.prompt();
        List<RagAnswer.Source> sources = p.sources();
        try {
            return new RagAnswer(breaker.executeCallable(() -> generate(null, prompt)), sources);
        } catch (Exception e) {
            log.warn("RAG chat: main model failed: {}", shortReason(e));
        }
        if (fallbackModel != null) {
            try {
                return new RagAnswer(generate(fallbackModel, prompt), sources);
            } catch (Exception e) {
                log.warn("RAG chat: fallback model {} failed: {}", fallbackModel, shortReason(e));
            }
        }
        return new RagAnswer(null, sources);
    }

    /** Câu hỏi đã tìm trong cẩm nang: prompt null khi không có đoạn nào liên quan. */
    public record Prepared(String prompt, List<RagAnswer.Source> sources) {}

    /** Tìm 4 đoạn cẩm nang gần nghĩa nhất, bỏ đoạn quá xa (độ giống < 0.3). Gọi embedding, chặn luồng. */
    public Prepared prepare(String question) {
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question).topK(4).similarityThreshold(0.3).build());
        return docs.isEmpty() ? new Prepared(null, List.of())
                : new Prepared(buildUserPrompt(question, docs), toSources(docs));
    }

    /**
     * Bước 2: chữ trả lời theo từng đoạn. Cùng cầu dao và giới hạn thời gian (giữa hai đoạn chữ) như ask();
     * model chính lỗi trước khi ra chữ nào thì chuyển sang model dự phòng.
     */
    public Flux<String> stream(String prompt) {
        AtomicBoolean started = new AtomicBoolean();
        Flux<String> main = Flux.defer(() -> {
            if (!breaker.tryAcquirePermission()) {
                return Flux.error(CallNotPermittedException.createCallNotPermittedException(breaker));
            }
            long t0 = System.nanoTime();
            return streamFrom(null, prompt)
                    .doOnComplete(() -> breaker.onSuccess(System.nanoTime() - t0, TimeUnit.NANOSECONDS))
                    .doOnError(e -> breaker.onError(System.nanoTime() - t0, TimeUnit.NANOSECONDS, e))
                    .doOnCancel(breaker::releasePermission);
        });
        return main.doOnNext(t -> started.set(true)).onErrorResume(e -> {
            log.warn("RAG chat: main model failed: {}", shortReason(e));
            if (started.get() || fallbackModel == null) {
                return Flux.error(e);
            }
            return streamFrom(fallbackModel, prompt).doOnError(f ->
                    log.warn("RAG chat: fallback model {} failed: {}", fallbackModel, shortReason(f)));
        });
    }

    private Flux<String> streamFrom(String model, String prompt) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
        if (model != null) {
            spec = spec.options(ChatOptions.builder().model(model));
        }
        return spec.user(prompt).stream().content().timeout(timeout);
    }

    /** model null = model mặc định trong cấu hình. Quá thời gian thì huỷ và ném TimeoutException. */
    private String generate(String model, String prompt) throws Exception {
        return timeLimiter.executeFutureSupplier(() -> pool.submit(() -> {
            ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
            if (model != null) {
                spec = spec.options(ChatOptions.builder().model(model));
            }
            return spec.user(prompt).call().content();
        }));
    }

    static String shortReason(Throwable e) {
        if (e instanceof CallNotPermittedException) {
            return "skipped, circuit breaker open (too many recent failures)";
        }
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getClass().getSimpleName() + ": " + root.getMessage();
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }

    /** Escape HTML rồi đổi **chữ** (Markdown Gemini hay trả) thành chữ đậm. */
    public static String toHtml(String answer) {
        return answer == null ? null : BOLD.matcher(HtmlUtils.htmlEscape(answer, "UTF-8")).replaceAll("<strong>$1</strong>");
    }

    static String buildUserPrompt(String question, List<Document> docs) {
        StringBuilder sb = new StringBuilder("TÀI LIỆU:\n");
        for (int i = 0; i < docs.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(docs.get(i).getText()).append("\n\n");
        }
        return sb.append("CÂU HỎI: ").append(question).toString();
    }

    static List<RagAnswer.Source> toSources(List<Document> docs) {
        return IntStream.range(0, docs.size()).mapToObj(i -> {
            Document d = docs.get(i);
            String text = d.getText();
            return new RagAnswer.Source(i + 1,
                    String.valueOf(d.getMetadata().getOrDefault("source", "?")),
                    text.length() > 200 ? text.substring(0, 200) + "..." : text,
                    d.getScore());
        }).toList();
    }
}
