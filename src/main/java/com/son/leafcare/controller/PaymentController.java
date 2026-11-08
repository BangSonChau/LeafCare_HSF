package com.son.leafcare.controller;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Payment;
import com.son.leafcare.entity.PaymentStatus;
import com.son.leafcare.service.AppUserService;
import com.son.leafcare.service.PaymentService;
import com.son.leafcare.service.SubscriptionService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

@Controller
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final AppUserService users;
    private final SubscriptionService subscriptions;
    private final PaymentService payments;
    private final BankAccount bank;

    /** Where buyers transfer money; shown under the QR. */
    public record BankAccount(String bank, String account, String name) {
    }

    public PaymentController(AppUserService users, SubscriptionService subscriptions, PaymentService payments,
                             @Value("${sepay.bank}") String bank, @Value("${sepay.account}") String account,
                             @Value("${sepay.account-name}") String name) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.bank = new BankAccount(bank, account, name);
    }

    @GetMapping("/payments/checkout")
    public String checkout(Authentication auth, Model model) {
        model.addAttribute("premiumUntil", subscriptions.premiumUntil(users.current(auth)));
        model.addAttribute("price", subscriptions.premiumPrice());
        model.addAttribute("days", subscriptions.premiumDays());
        return "payments/checkout";
    }

    @GetMapping("/payments")
    public String history(@RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        model.addAttribute("page", payments.history(users.current(auth), page));
        return "payments/history";
    }

    @PostMapping("/payments/premium")
    public String start(Authentication auth) {
        Payment p = payments.startPremium(users.current(auth));
        return "redirect:/payments/" + UriUtils.encodePathSegment(p.getOrderId(), StandardCharsets.UTF_8);
    }

    /** Server-to-server from SePay: public and CSRF-exempt (SecurityConfig); the API key protects it. */
    @PostMapping("/payments/sepay/webhook")
    public ResponseEntity<Map<String, Boolean>> webhook(@RequestHeader(value = "Authorization", required = false) String auth,
                                                        @RequestBody Map<String, Object> body) {
        if (!payments.validKey(auth)) {
            log.warn("SePay webhook bị từ chối: sai API key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        payments.handleWebhook(body);
        return ResponseEntity.ok(Map.of("success", true)); // SePay retries unless it gets exactly this
    }

    @GetMapping("/payments/{orderId}")
    public String result(@PathVariable String orderId, Authentication auth, Model model) {
        AppUser me = users.current(auth);
        Payment p = payments.getForUser(orderId, me);
        model.addAttribute("payment", p);
        model.addAttribute("bank", bank);
        if (p.getStatus() == PaymentStatus.SUCCESS) {
            model.addAttribute("premiumUntil", subscriptions.premiumUntil(me));
        }
        return "payments/result";
    }
}
