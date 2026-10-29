package com.homecare.booking;

import com.homecare.booking.BookingDtos.*;
import com.homecare.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin only (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final AdminBookingService service;

    public AdminBookingController(AdminBookingService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminBooking> list(@RequestParam(required = false) BookingStatus status) {
        return service.list(status);
    }

    @PatchMapping("/{id}/status")
    public AdminBooking updateStatus(@AuthenticationPrincipal User admin, @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        return service.updateStatus(admin, id, req.status(), req.fee());
    }

    /** Changes the fee of a completed visit while it is still unpaid, and tells the seeker. */
    @PutMapping("/{id}/fee")
    public AdminBooking setFee(@AuthenticationPrincipal User admin, @PathVariable Long id, @Valid @RequestBody FeeRequest req) {
        return service.setFee(admin, id, req.fee());
    }

    @PostMapping("/{id}/payment/resend")
    public AdminBooking resendPayment(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        return service.resendPayment(admin, id);
    }

    /** The seeker paid outside PayHere (a bank deposit, cash). */
    @PostMapping("/{id}/payment/mark-paid")
    public AdminBooking markPaid(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        return service.markPaid(admin, id);
    }

    @PostMapping("/{id}/resend-alert")
    public AdminBooking resendAlert(@PathVariable Long id) {
        return service.resendAlert(id);
    }
}
