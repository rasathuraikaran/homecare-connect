package com.homecare.payment;

import com.homecare.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The admin's two switches for how seekers may pay. Admin only (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/payment-settings")
public class PaymentSettingsController {

    /** on: in use right now. available: this server is set up for it, so the switch can be turned on. */
    public record Way(boolean on, boolean available) {}

    public record PaymentSettingsView(Way payhere, Way bank, PayMode payhereMode) {}

    /** A switch left out (null) is not changed. */
    public record PaymentSettingsRequest(Boolean payhereEnabled, Boolean bankEnabled) {}

    private final PaymentOptions options;

    public PaymentSettingsController(PaymentOptions options) {
        this.options = options;
    }

    @GetMapping
    public PaymentSettingsView get() {
        return view();
    }

    @PutMapping
    public PaymentSettingsView set(@AuthenticationPrincipal User admin, @RequestBody PaymentSettingsRequest req) {
        options.update(admin, req.payhereEnabled(), req.bankEnabled());
        return view();
    }

    private PaymentSettingsView view() {
        return new PaymentSettingsView(new Way(options.payhereOn(), options.payhereAvailable()),
                new Way(options.bankOn(), options.bankAvailable()), options.payhereMode());
    }
}
