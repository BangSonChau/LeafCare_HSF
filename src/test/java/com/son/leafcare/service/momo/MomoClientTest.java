package com.son.leafcare.service.momo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.son.leafcare.service.momo.MomoClient.MomoResult;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MomoClientTest {

    MockRestServiceServer server;
    MomoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MomoClient(builder, "http://momo.test", "MOMO", "AK", "secret", "http://h/ret", "http://h/ipn");
    }

    @Test
    void signIsHmacSha256HexOfRawString() {
        // Expected value computed independently with Python hmac/hashlib.
        String raw = "accessKey=AK&amount=49000&extraData=&ipnUrl=http://h/ipn&orderId=LC1&orderInfo=LeafCare Premium"
                + "&partnerCode=MOMO&redirectUrl=http://h/ret&requestId=LC1&requestType=captureWallet";
        assertThat(client.sign(raw)).isEqualTo("f063452e64a58df7987c04c707bb4e84b389156d8fbdb978b2ad877e77363870");
    }

    @Test
    void createSendsSignedOrderAndReturnsPayUrl() {
        String expectedSig = client.sign("accessKey=AK&amount=49000&extraData=&ipnUrl=http://h/ipn&orderId=LC1"
                + "&orderInfo=LeafCare Premium&partnerCode=MOMO&redirectUrl=http://h/ret&requestId=LC1"
                + "&requestType=captureWallet");
        server.expect(requestTo("http://momo.test/v2/gateway/api/create")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.signature").value(expectedSig))
                .andExpect(jsonPath("$.amount").value(49000))
                .andExpect(jsonPath("$.requestType").value("captureWallet"))
                .andRespond(withSuccess("{\"resultCode\":0,\"payUrl\":\"https://pay.test/x\"}", MediaType.APPLICATION_JSON));

        assertThat(client.createPayUrl("LC1", 49000, "LeafCare Premium")).isEqualTo("https://pay.test/x");
        server.verify();
    }

    @Test
    void createRefusedByMomoThrows() {
        server.expect(requestTo("http://momo.test/v2/gateway/api/create"))
                .andRespond(withSuccess("{\"resultCode\":11,\"message\":\"Bad\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.createPayUrl("LC1", 49000, "x")).isInstanceOf(MomoException.class);
    }

    @Test
    void createServerErrorThrowsMomoException() {
        server.expect(requestTo("http://momo.test/v2/gateway/api/create")).andRespond(withServerError());
        assertThatThrownBy(() -> client.createPayUrl("LC1", 49000, "x")).isInstanceOf(MomoException.class);
    }

    @Test
    void queryIsSignedAndParsed() {
        String expectedSig = client.sign("accessKey=AK&orderId=LC1&partnerCode=MOMO&requestId=LC1");
        server.expect(requestTo("http://momo.test/v2/gateway/api/query"))
                .andExpect(jsonPath("$.signature").value(expectedSig))
                .andRespond(withSuccess("{\"orderId\":\"LC1\",\"amount\":49000,\"resultCode\":0,\"transId\":123,"
                        + "\"message\":\"Thành công.\"}", MediaType.APPLICATION_JSON));

        MomoResult r = client.query("LC1");
        assertThat(r).isEqualTo(new MomoResult("LC1", 49000, 0, 123L, "Thành công."));
        assertThat(r.success()).isTrue();
    }

    Map<String, Object> ipn() {
        Map<String, Object> p = new HashMap<>();
        p.put("partnerCode", "MOMO");
        p.put("orderId", "LC1");
        p.put("requestId", "LC1");
        p.put("amount", 49000);
        p.put("orderInfo", "LeafCare Premium");
        p.put("orderType", "momo_wallet");
        p.put("transId", 4088878653L);
        p.put("resultCode", 0);
        p.put("message", "Thành công.");
        p.put("payType", "qr");
        p.put("responseTime", 1731000000000L);
        p.put("extraData", "");
        p.put("signature", client.sign("accessKey=AK&amount=49000&extraData=&message=Thành công.&orderId=LC1"
                + "&orderInfo=LeafCare Premium&orderType=momo_wallet&partnerCode=MOMO&payType=qr&requestId=LC1"
                + "&responseTime=1731000000000&resultCode=0&transId=4088878653"));
        return p;
    }

    @Test
    void validIpnIsAccepted() {
        assertThat(client.verifyIpn(ipn())).isEqualTo(new MomoResult("LC1", 49000, 0, 4088878653L, "Thành công."));
    }

    @Test
    void tamperedIpnIsRejected() {
        Map<String, Object> p = ipn();
        p.put("amount", 1000);
        assertThat(client.verifyIpn(p)).isNull();
    }

    @Test
    void ipnWithoutSignatureIsRejected() {
        Map<String, Object> p = ipn();
        p.remove("signature");
        assertThat(client.verifyIpn(p)).isNull();
    }

    @Test
    void pendingCodes() {
        assertThat(new MomoResult("x", 1, 1000, null, "").pending()).isTrue();
        assertThat(new MomoResult("x", 1, 7000, null, "").pending()).isTrue();
        assertThat(new MomoResult("x", 1, 1006, null, "").pending()).isFalse();
    }
}
