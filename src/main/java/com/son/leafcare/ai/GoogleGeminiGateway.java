package com.son.leafcare.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.son.leafcare.config.GeminiProperties;

@Component
public class GoogleGeminiGateway implements GeminiGateway {

    private static final Logger log = LoggerFactory.getLogger(GoogleGeminiGateway.class);

    private final GeminiProperties props;
    private final ObjectMapper objectMapper;
    private final Schema responseSchema;

    public GoogleGeminiGateway(GeminiProperties props) {
        this.props = props;
        this.objectMapper = new ObjectMapper();
        this.responseSchema = buildResponseSchema();
    }

    private Schema buildResponseSchema() {
        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "answer", Schema.builder()
                                .type(Type.Known.STRING)
                                .description("Câu trả lời tiếng Việt có cấu trúc rõ ràng cho người trồng cây")
                                .build(),
                        "needsClarification", Schema.builder()
                                .type(Type.Known.BOOLEAN)
                                .description("True nếu cần hỏi thêm thông tin hoặc chưa đủ bằng chứng để kết luận")
                                .build(),
                        "followUpQuestions", Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .description("Danh sách tối đa 3 câu hỏi gợi ý tiếp theo bằng tiếng Việt")
                                .build(),
                        "citationIds", Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .description("Danh sách mã chunkCode của các đoạn tài liệu thực sự được dùng")
                                .build()
                ))
                .required(List.of("answer", "needsClarification", "followUpQuestions", "citationIds"))
                .build();
    }

    @Override
    public boolean isAvailable() {
        return props.isEnabled() && props.hasApiKey();
    }

    @Override
    public float[] embed(String text, boolean isQuery) {
        if (!isAvailable() || text == null || text.isBlank()) {
            return new float[0];
        }
        String taskType = isQuery ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT";
        EmbedContentConfig config = EmbedContentConfig.builder()
                .taskType(taskType)
                .outputDimensionality(props.getEmbeddingDimension())
                .httpOptions(HttpOptions.builder().timeout(props.getTimeoutMs()).build())
                .build();

        return executeWithRetry("embedContent", () -> {
            try (Client client = createClient()) {
                EmbedContentResponse resp = client.models.embedContent(
                        props.getEmbeddingModel(),
                        text,
                        config);
                Optional<List<ContentEmbedding>> embeddings = resp.embeddings();
                if (embeddings.isEmpty() || embeddings.get().isEmpty()) {
                    return new float[0];
                }
                Optional<List<Float>> valuesOpt = embeddings.get().get(0).values();
                if (valuesOpt.isEmpty() || valuesOpt.get().isEmpty()) {
                    return new float[0];
                }
                List<Float> list = valuesOpt.get();
                float[] vector = new float[list.size()];
                double normSq = 0.0;
                for (int i = 0; i < list.size(); i++) {
                    float v = list.get(i) == null ? 0f : list.get(i);
                    vector[i] = v;
                    normSq += (double) v * v;
                }
                if (normSq > 0.0) {
                    float norm = (float) Math.sqrt(normSq);
                    for (int i = 0; i < vector.length; i++) {
                        vector[i] /= norm;
                    }
                }
                return vector;
            }
        });
    }

    @Override
    public GeminiChatResponse generateStructuredAnswer(
            String systemPrompt,
            String userPrompt,
            byte[] imageBytes,
            String imageMimeType) {

        if (!isAvailable()) {
            throw new GeminiGatewayException(
                    GeminiGatewayException.ErrorCode.AUTH_ERROR,
                    "Gemini API is disabled or missing GEMINI_API_KEY");
        }

        List<Part> parts = new ArrayList<>();
        if (imageBytes != null && imageBytes.length > 0 && imageMimeType != null && !imageMimeType.isBlank()) {
            parts.add(Part.fromBytes(imageBytes, imageMimeType));
        }
        parts.add(Part.fromText(userPrompt));

        Content userContent = Content.builder()
                .role("user")
                .parts(parts)
                .build();

        Content systemInstruction = Content.fromParts(Part.fromText(systemPrompt));

        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(systemInstruction)
                .temperature(0.2f)
                .maxOutputTokens(props.getMaxOutputTokens())
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .httpOptions(HttpOptions.builder().timeout(props.getTimeoutMs()).build())
                .build();

        return executeWithRetry("generateContent", () -> {
            try (Client client = createClient()) {
                GenerateContentResponse response = client.models.generateContent(
                        props.getChatModel(),
                        userContent,
                        config);
                String rawText = response.text();
                int promptTokens = 0;
                int completionTokens = 0;
                Optional<GenerateContentResponseUsageMetadata> usageOpt = response.usageMetadata();
                if (usageOpt.isPresent()) {
                    GenerateContentResponseUsageMetadata u = usageOpt.get();
                    promptTokens = u.promptTokenCount().orElse(0);
                    completionTokens = u.candidatesTokenCount().orElse(0);
                }
                return parseStructuredJson(rawText, promptTokens, completionTokens, props.getChatModel());
            }
        });
    }

    @Override
    public void generateStreamAnswer(
            String systemPrompt,
            String userPrompt,
            byte[] imageBytes,
            String imageMimeType,
            Consumer<String> tokenConsumer) {

        if (!isAvailable()) {
            throw new GeminiGatewayException(
                    GeminiGatewayException.ErrorCode.AUTH_ERROR,
                    "Gemini API is disabled or missing GEMINI_API_KEY");
        }

        List<Part> parts = new ArrayList<>();
        if (imageBytes != null && imageBytes.length > 0 && imageMimeType != null && !imageMimeType.isBlank()) {
            parts.add(Part.fromBytes(imageBytes, imageMimeType));
        }
        parts.add(Part.fromText(userPrompt));

        Content userContent = Content.builder()
                .role("user")
                .parts(parts)
                .build();

        Content systemInstruction = Content.fromParts(Part.fromText(systemPrompt));

        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(systemInstruction)
                .temperature(0.3f)
                .maxOutputTokens(props.getMaxOutputTokens())
                .httpOptions(HttpOptions.builder().timeout(props.getTimeoutMs()).build())
                .build();

        try (Client client = createClient()) {
            try (com.google.genai.ResponseStream<GenerateContentResponse> stream = client.models.generateContentStream(
                    props.getChatModel(),
                    userContent,
                    config)) {
                for (GenerateContentResponse resp : stream) {
                    String piece = resp.text();
                    if (piece != null && !piece.isEmpty()) {
                        tokenConsumer.accept(piece);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Gemini streaming failed: {}", e.getMessage());
            throw classifyException("generateContentStream", e);
        }
    }

    GeminiChatResponse parseStructuredJson(
            String rawText, int promptTokens, int completionTokens, String modelName) {
        if (rawText == null || rawText.isBlank()) {
            throw new GeminiGatewayException(
                    GeminiGatewayException.ErrorCode.PARSE_ERROR,
                    "Empty response from Gemini model");
        }
        String cleaned = stripMarkdownCodeFence(rawText.trim());
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            String answer = root.path("answer").asText("").trim();
            if (answer.isEmpty()) {
                throw new GeminiGatewayException(
                        GeminiGatewayException.ErrorCode.PARSE_ERROR,
                        "Missing answer field in Gemini response");
            }
            boolean needsClarification = root.path("needsClarification").asBoolean(false);
            List<String> followUpQuestions = new ArrayList<>();
            JsonNode followNode = root.path("followUpQuestions");
            if (followNode.isArray()) {
                for (JsonNode item : followNode) {
                    String q = item.asText("").trim();
                    if (!q.isEmpty() && followUpQuestions.size() < 4) {
                        followUpQuestions.add(q);
                    }
                }
            }
            List<String> citationIds = new ArrayList<>();
            JsonNode citeNode = root.path("citationIds");
            if (citeNode.isArray()) {
                for (JsonNode item : citeNode) {
                    String id = item.asText("").trim();
                    if (!id.isEmpty()) {
                        citationIds.add(id);
                    }
                }
            }
            return new GeminiChatResponse(
                    answer,
                    needsClarification,
                    followUpQuestions,
                    citationIds,
                    promptTokens,
                    completionTokens,
                    modelName);
        } catch (GeminiGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse structured JSON from Gemini. Raw text was: [{}]", rawText, e);
            throw new GeminiGatewayException(
                    GeminiGatewayException.ErrorCode.PARSE_ERROR,
                    "Failed to parse structured JSON from Gemini", e);
        }
    }

    private String stripMarkdownCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence >= 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
        }
        trimmed = trimmed.trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed.trim();
    }

    private Client createClient() {
        return Client.builder()
                .apiKey(props.getApiKey().trim())
                .httpOptions(HttpOptions.builder().timeout(props.getTimeoutMs()).build())
                .build();
    }

    private <T> T executeWithRetry(String operation, RetryableSupplier<T> supplier) {
        int maxAttempts = Math.max(1, props.getMaxRetries() + 1);
        long startTime = System.currentTimeMillis();
        long budgetMs = props.getTimeoutMs();
        GeminiGatewayException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (GeminiGatewayException e) {
                if (e.getErrorCode() == GeminiGatewayException.ErrorCode.AUTH_ERROR
                        || e.getErrorCode() == GeminiGatewayException.ErrorCode.PARSE_ERROR) {
                    throw e;
                }
                lastError = e;
            } catch (Exception e) {
                GeminiGatewayException mapped = classifyException(operation, e);
                if (mapped.getErrorCode() == GeminiGatewayException.ErrorCode.AUTH_ERROR) {
                    throw mapped;
                }
                lastError = mapped;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            if (attempt >= maxAttempts || elapsed + (attempt * 400L) >= budgetMs) {
                break;
            }
            try {
                Thread.sleep(attempt * 350L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw lastError != null
                ? lastError
                : new GeminiGatewayException(GeminiGatewayException.ErrorCode.UPSTREAM_ERROR, "Unknown error");
    }

    private GeminiGatewayException classifyException(String operation, Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        log.warn("Gemini {} error ({}): {}", operation, e.getClass().getSimpleName(), sanitizeLogMessage(e.getMessage()));
        if (msg.contains("401") || msg.contains("403") || msg.contains("api key") || msg.contains("permission_denied") || msg.contains("unauthenticated")) {
            return new GeminiGatewayException(GeminiGatewayException.ErrorCode.AUTH_ERROR, "Authentication failed", e);
        }
        if (msg.contains("429") || msg.contains("quota") || msg.contains("resource_exhausted") || msg.contains("rate limit")) {
            return new GeminiGatewayException(GeminiGatewayException.ErrorCode.QUOTA_EXCEEDED, "Rate limit or quota exceeded", e);
        }
        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("deadline")) {
            return new GeminiGatewayException(GeminiGatewayException.ErrorCode.TIMEOUT, "Request timed out", e);
        }
        return new GeminiGatewayException(GeminiGatewayException.ErrorCode.UPSTREAM_ERROR, "Upstream call failed", e);
    }

    private String sanitizeLogMessage(String raw) {
        if (raw == null) {
            return "";
        }
        String key = props.getApiKey();
        if (key != null && !key.isBlank() && raw.contains(key)) {
            return raw.replace(key, "[REDACTED_KEY]");
        }
        return raw.length() > 300 ? raw.substring(0, 300) + "..." : raw;
    }

    @FunctionalInterface
    private interface RetryableSupplier<T> {
        T get() throws Exception;
    }
}

