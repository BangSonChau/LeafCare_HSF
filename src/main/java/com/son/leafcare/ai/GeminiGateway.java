package com.son.leafcare.ai;

import java.util.List;

public interface GeminiGateway {

    boolean isAvailable();

    float[] embed(String text, boolean isQuery);

    GeminiChatResponse generateStructuredAnswer(
            String systemPrompt,
            String userPrompt,
            byte[] imageBytes,
            String imageMimeType);

    record GeminiChatResponse(
            String answer,
            boolean needsClarification,
            List<String> followUpQuestions,
            List<String> citationIds,
            int promptTokens,
            int completionTokens,
            String modelName
    ) {}
}

