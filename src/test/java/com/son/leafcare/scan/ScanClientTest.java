package com.son.leafcare.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class ScanClientTest {

    MockRestServiceServer server;
    ScanClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ScanClient(builder, "http://scan.test");
    }

    @Test
    void sendsPhotoAsMultipartAndReadsLabelAndConfidence() {
        server.expect(requestTo("http://scan.test/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", containsString("multipart/form-data")))
                .andExpect(content().string(containsString("name=\"image\"; filename=\"leaf.jpg\"")))
                .andRespond(withSuccess("""
                        {"label":"Tomato___Early_blight","confidence":0.9663,
                         "top3":[{"label":"Tomato___Early_blight","confidence":0.9663}],"isLeaf":true,"isHealthy":false,
                         "probs":{"Tomato___Early_blight":0.9663,"Tomato___Late_blight":0.0259}}""",
                        MediaType.APPLICATION_JSON));

        ScanClient.Prediction p = client.predict(new byte[] {1, 2, 3}, "leaf.jpg");

        assertThat(p.label()).isEqualTo("Tomato___Early_blight");
        assertThat(p.confidence()).isEqualTo(0.9663);
        assertThat(p.probs()).containsEntry("Tomato___Late_blight", 0.0259).hasSize(2);
        server.verify();
    }

    @Test
    void serverErrorThrows() {
        server.expect(requestTo("http://scan.test/predict")).andRespond(withServerError());
        assertThatThrownBy(() -> client.predict(new byte[] {1}, "a.png")).isInstanceOf(RestClientException.class);
    }

    @Test
    void answerWithoutLabelThrows() {
        server.expect(requestTo("http://scan.test/predict"))
                .andRespond(withSuccess("{\"confidence\":0.5}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.predict(new byte[] {1}, "a.png")).isInstanceOf(IllegalStateException.class);
    }
}
