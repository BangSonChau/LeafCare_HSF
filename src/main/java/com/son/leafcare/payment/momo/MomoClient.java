package com.son.leafcare.payment.momo;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** MoMo payment gateway API v2 (captureWallet / payWithATM / payWithCC), signed with HMAC-SHA256 as in developers.momo.vn. */
@Component
public class MomoClient {

    /** What MoMo says about an order: from IPN or from the query API. */
    public record MomoResult(String orderId, long amount, int resultCode, Long transId, String message) {
        public boolean success() {
            return resultCode == 0;
        }

        /** 1000 = waiting for the user to confirm, 7000/7002 = being processed. */
        public boolean pending() {
            return resultCode == 1000 || resultCode == 7000 || resultCode == 7002;
        }
    }

    private final RestClient http;
    private final String partnerCode;
    private final String accessKey;
    private final String secretKey;
    private final String redirectUrl;
    private final String ipnUrl;
    private final String requestType;

    @Autowired
    public MomoClient(@Value("${momo.endpoint}") String endpoint,
                      @Value("${momo.partner-code}") String partnerCode,
                      @Value("${momo.access-key}") String accessKey,
                      @Value("${momo.secret-key}") String secretKey,
                      @Value("${momo.redirect-url}") String redirectUrl,
                      @Value("${momo.ipn-url}") String ipnUrl,
                      @Value("${momo.request-type}") String requestType) {
        this(RestClient.builder().requestFactory(timeout30s()), endpoint, partnerCode, accessKey, secretKey,
                redirectUrl, ipnUrl, requestType);
    }

    MomoClient(RestClient.Builder builder, String endpoint, String partnerCode, String accessKey, String secretKey,
               String redirectUrl, String ipnUrl, String requestType) {
        this.http = builder.baseUrl(endpoint).build();
        this.partnerCode = partnerCode;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.redirectUrl = redirectUrl;
        this.ipnUrl = ipnUrl;
        this.requestType = requestType;
    }

    private static SimpleClientHttpRequestFactory timeout30s() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(30_000); // MoMo asks for at least 30 s
        return f;
    }

    /** Creates the order at MoMo and returns the payUrl to send the browser to. */
    public String createPayUrl(String orderId, long amount, String orderInfo) {
        String raw = "accessKey=" + accessKey + "&amount=" + amount + "&extraData=&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId + "&orderInfo=" + orderInfo + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl + "&requestId=" + orderId + "&requestType=" + requestType;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", partnerCode);
        body.put("requestId", orderId);
        body.put("amount", amount);
        body.put("orderId", orderId);
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", redirectUrl);
        body.put("ipnUrl", ipnUrl);
        body.put("requestType", requestType);
        body.put("extraData", "");
        body.put("lang", "vi");
        body.put("signature", sign(raw));
        Map<?, ?> res = post("/v2/gateway/api/create", body);
        Object payUrl = res.get("payUrl");
        if (!"0".equals(String.valueOf(res.get("resultCode"))) || payUrl == null) {
            throw new MomoException("MoMo từ chối tạo đơn: " + res.get("message"));
        }
        return payUrl.toString();
    }

    /** Asks MoMo for the current status of an order (used when IPN cannot reach us, e.g. on localhost). */
    public MomoResult query(String orderId) {
        String raw = "accessKey=" + accessKey + "&orderId=" + orderId + "&partnerCode=" + partnerCode
                + "&requestId=" + orderId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", partnerCode);
        body.put("requestId", orderId);
        body.put("orderId", orderId);
        body.put("lang", "vi");
        body.put("signature", sign(raw));
        return toResult(post("/v2/gateway/api/query", body));
    }

    /** Checks the IPN signature; returns null when it is missing or wrong. */
    public MomoResult verifyIpn(Map<String, Object> p) {
        String raw = "accessKey=" + accessKey + "&amount=" + s(p, "amount") + "&extraData=" + s(p, "extraData")
                + "&message=" + s(p, "message") + "&orderId=" + s(p, "orderId") + "&orderInfo=" + s(p, "orderInfo")
                + "&orderType=" + s(p, "orderType") + "&partnerCode=" + s(p, "partnerCode")
                + "&payType=" + s(p, "payType") + "&requestId=" + s(p, "requestId")
                + "&responseTime=" + s(p, "responseTime") + "&resultCode=" + s(p, "resultCode")
                + "&transId=" + s(p, "transId");
        Object given = p.get("signature");
        boolean ok = given != null && MessageDigest.isEqual(sign(raw).getBytes(StandardCharsets.UTF_8),
                given.toString().getBytes(StandardCharsets.UTF_8));
        return ok && partnerCode.equals(s(p, "partnerCode")) ? toResult(p) : null;
    }

    String sign(String raw) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<?, ?> post(String path, Map<String, Object> body) {
        try {
            Map<?, ?> res = http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Map.class);
            if (res == null) {
                throw new MomoException("MoMo không trả lời");
            }
            return res;
        } catch (MomoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MomoException("Không gọi được MoMo: " + e.getMessage());
        }
    }

    private static MomoResult toResult(Map<?, ?> m) {
        Object trans = m.get("transId");
        return new MomoResult(String.valueOf(m.get("orderId")), Long.parseLong(String.valueOf(m.get("amount"))),
                Integer.parseInt(String.valueOf(m.get("resultCode"))),
                trans == null ? null : Long.valueOf(trans.toString()), String.valueOf(m.get("message")));
    }

    private static String s(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v == null ? "" : v.toString();
    }
}
