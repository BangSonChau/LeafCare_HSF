package com.son.leafcare.scan;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Calls the leaf-disease model service (leafcare-scan-service, FastAPI): POST /predict with the photo. */
@Component
public class ScanClient {

    /** The service's answer; extra fields (top3, isLeaf, isHealthy) are ignored. */
    public record Prediction(String label, double confidence) {
    }

    private final RestClient client;

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

    /** Throws a RestClientException when the service is down, slow or answers with an error. */
    public Prediction predict(byte[] image, String fileName) {
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
