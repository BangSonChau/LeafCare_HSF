package com.son.leafcare.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "leafcare.gemini")
@Getter
@Setter
public class GeminiProperties {

    /**
     * Whether AI calls to Google Gemini are enabled.
     * Can be turned off so CRUD and basic retrieval still work.
     */
    private boolean enabled = true;

    /**
     * Gemini Developer API key (from GEMINI_API_KEY env or application-local.properties).
     */
    private String apiKey = "";

    /**
     * Model used for multimodal/text chat generation.
     */
    private String chatModel = "gemini-2.5-flash";

    /**
     * Model used for vector embeddings.
     */
    private String embeddingModel = "gemini-embedding-001";

    /**
     * Output dimensionality for embeddings (e.g. 768).
     */
    private int embeddingDimension = 768;

    /**
     * Timeout in milliseconds for Gemini HTTP requests.
     */
    private int timeoutMs = 30000;

    /**
     * Maximum output tokens per chat response.
     */
    private int maxOutputTokens = 1536;

    /**
     * Maximum number of retries on transient failures.
     */
    private int maxRetries = 2;

    /**
     * Maximum requests per minute per session.
     */
    private int maxRequestsPerMinutePerSession = 10;

    /**
     * Maximum requests per minute globally across the application.
     */
    private int maxRequestsPerMinuteGlobal = 60;

    /**
     * Whether to automatically embed unembedded approved chunks on startup when API key is configured.
     */
    private boolean autoEmbedOnStartup = true;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}

