package com.son.leafcare.controller;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Payment;
import com.son.leafcare.entity.PaymentStatus;
import com.son.leafcare.service.AppUserService;
import com.son.leafcare.service.PaymentService;
import com.son.leafcare.service.SubscriptionService;
import com.son.leafcare.service.momo.MomoException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

@Controller
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final AppUserService users;
    private final SubscriptionService subscriptions;
    private final PaymentService payments;

    public PaymentController(AppUserService users, SubscriptionService subscriptions, PaymentService payments) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.payments = payments;
    }

    @GetMapping("/payments/checkout")
    public String checkout(Authentication auth, Model model) {
        model.addAttribute("premiumUntil", subscriptions.premiumUntil(users.current(auth)));
        model.addAttribute("price", subscriptions.premiumPrice());
        model.addAttribute("days", subscriptions.premiumDays());
        return "payments/checkout";
    }

    @PostMapping("/payments/momo")
    public String pay(Authentication auth, RedirectAttributes ra) {
        try {
            return "redirect:" + payments.startPremium(users.current(auth));
        } catch (MomoException e) {
            log.warn("MoMo create failed: {}", e.getMessage());
            ra.addFlashAttribute("error", "Chưa kết nối được MoMo, thử lại sau ít phút.");
            return "redirect:/payments/checkout";
        }
    }

    /** MoMo sends the browser back here; its query params are not trusted, only orderId is used. */
    @GetMapping("/payments/momo/return")
    public String back(@RequestParam String orderId) {
        return "redirect:/payments/" + UriUtils.encodePathSegment(orderId, StandardCharsets.UTF_8);
    }

    /** Server-to-server from MoMo: public and CSRF-exempt (SecurityConfig). MoMo expects 204. */
    @PostMapping("/payments/momo/ipn")
    public ResponseEntity<Void> ipn(@RequestBody Map<String, Object> body) {
        return payments.handleIpn(body) ? ResponseEntity.noContent().build() : ResponseEntity.badRequest().build();
    }

    @GetMapping("/payments/{orderId}")
    public String result(@PathVariable String orderId, Authentication auth, Model model) {
        AppUser me = users.current(auth);
        Payment p = payments.refresh(orderId, me);
        model.addAttribute("payment", p);
        if (p.getStatus() == PaymentStatus.SUCCESS) {
            model.addAttribute("premiumUntil", subscriptions.premiumUntil(me));
        }
        return "payments/result";
    }
}
