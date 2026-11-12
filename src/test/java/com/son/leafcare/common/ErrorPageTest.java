package com.son.leafcare.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Real servlet container: MockMvc does not forward to /error, so it cannot prove the error views.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorPageTest {

    /** Lets these anonymous HTTP calls reach the plant pages; the real rules are tested in SecurityConfigTest. */
    @TestConfiguration
    static class OpenPlants {
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain openPlants(HttpSecurity http) throws Exception {
            return http.securityMatcher("/plants/**").authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        }
    }

    @Value("${local.server.port}")
    int port;

    HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html").build();
        return HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void unknownPlantRendersVietnamese404InsideLayout() throws Exception {
        HttpResponse<String> r = get("/plants/999999/edit");
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.body()).contains("Không tìm thấy").contains("LeafCare, về trang chủ").contains("Về trang chủ");
    }

    @Test
    void otherErrorsRenderGenericVietnamesePage() throws Exception {
        HttpResponse<String> r = get("/plants?page=abc");
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("Có lỗi xảy ra").contains("LeafCare, về trang chủ").contains("Về trang chủ");
    }
}
