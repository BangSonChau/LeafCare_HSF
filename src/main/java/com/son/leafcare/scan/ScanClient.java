package com.son.leafcare.scan;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/** Calls the leaf-disease model service (leafcare-scan-service, FastAPI): POST /predict with the photo. */
@Component
public class ScanClient {

    /** The service's answer: best label, its confidence, and every label's probability (extra fields ignored). */
    public record Prediction(String label, double confidence, Map<String, Double> probs) {

        /** An answer without per-label probabilities (older service). */
        public Prediction(String label, double confidence) {
            this(label, confidence, null);
        }
    }

    private final RestClient client;
    // Render Free cho service ngủ sau 15 phút; lúc thức dậy trả 502/503/504 khoảng 30-60s
    Duration wakeDelay = Duration.ofSeconds(15);
    static final int TRIES = 4;

    @Autowired
    public ScanClient(@Value("${leafcare.scan.url}") String url) {
        this(RestClient.builder().requestFactory(timeouts()), url);
    }

    ScanClient(RestClient.Builder builder, String url) {
        this.client = builder.baseUrl(url).build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(10));
        f.setReadTimeout(Duration.ofSeconds(60)); // a sleeping free host can take ~30-60s to wake up
        return f;
    }

    /**
     * Throws a RestClientException when the service is down, slow or answers with an error. A 502/503/504 (the free
     * host waking up) is retried up to 4 times, 15s apart.
     */
    public Prediction predict(byte[] image, String fileName) {
        for (int attempt = 1; ; attempt++) {
            try {
                return predictOnce(image, fileName);
            } catch (HttpServerErrorException e) {
                int code = e.getStatusCode().value();
                if (attempt >= TRIES || (code != 502 && code != 503 && code != 504)) {
                    throw e;
                }
                sleep();
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(wakeDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the scan service", e);
        }
    }

    private Prediction predictOnce(byte[] image, String fileName) {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        Prediction p = client.post().uri("/predict")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Prediction.class);
        if (p == null || p.label() == null) {
            throw new IllegalStateException("Scan service returned no label");
        }
        return p;
    }
}
