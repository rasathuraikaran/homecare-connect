package com.homecare.payment;

import com.homecare.payment.PaymentService.Checkout;
import com.homecare.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    public record PaymentConfig(PayMode mode, String currency) {}

    public record SimulateRequest(@NotBlank(message = "Say which payment") String orderId, @NotBlank(message = "Choose an outcome") String outcome) {}

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /** Whether fees are on, so the admin page knows whether to ask for one. Any signed-in user may read it. */
    @GetMapping("/api/payments/config")
    public PaymentConfig config() {
        return new PaymentConfig(service.mode(), PaymentService.CURRENCY);
    }

    /** A care seeker starts paying the fee of one of their own completed requests (the /api/bookings/** rule limits this to seekers). */
    @PostMapping("/api/bookings/{id}/pay")
    public Checkout pay(@AuthenticationPrincipal User seeker, @PathVariable Long id) {
        return service.startCheckout(seeker, id);
    }

    /**
     * PayHere calls this by itself after a payment. It has no sign-in, so everything rests on the signature check inside.
     * The answer says nothing about why a message was refused.
     */
    @PostMapping(value = "/api/payments/payhere/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> notify(@RequestParam Map<String, String> params) {
        try {
            return service.handleNotification(params) ? ResponseEntity.ok("OK") : ResponseEntity.badRequest().body("Rejected");
        } catch (RuntimeException e) {
            log.error("A PayHere callback could not be processed", e);
            return ResponseEntity.badRequest().body("Rejected");
        }
    }

    /** Only in simulated mode, for trying the flow without PayHere. */
    @PostMapping("/api/payments/simulate")
    public ResponseEntity<Void> simulate(@AuthenticationPrincipal User seeker, @Valid @RequestBody SimulateRequest req) {
        service.simulate(seeker, req.orderId(), req.outcome());
        return ResponseEntity.noContent().build();
    }
}
