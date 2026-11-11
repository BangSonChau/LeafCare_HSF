package com.son.leafcare.service;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/** Tìm đoạn liên quan -> đưa cho LLM -> trả lời kèm nguồn. */
@Service
public class RagChatService {

    static final String NO_INFO = "Tài liệu hiện có chưa đề cập vấn đề này.";

    static final String SYSTEM_PROMPT = """
            Bạn là trợ lý LeafCare về bệnh lá cây. Chỉ trả lời dựa trên TÀI LIỆU được cung cấp.
            Sau mỗi ý, ghi số nguồn trong ngoặc vuông, ví dụ [1] hoặc [1][3].
            Nếu tài liệu không có thông tin, nói rõ: "%s"
            Trả lời bằng tiếng Việt, ngắn gọn.""".formatted(NO_INFO);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagChatService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    }

    public RagAnswer ask(String question) {
        // lấy 4 đoạn gần nghĩa nhất, bỏ đoạn quá xa (độ giống < 0.3)
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question).topK(4).similarityThreshold(0.3).build());
        if (docs.isEmpty()) {
            return new RagAnswer(NO_INFO, List.of());
        }
        String answer = chatClient.prompt().user(buildUserPrompt(question, docs)).call().content();
        return new RagAnswer(answer, toSources(docs));
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
