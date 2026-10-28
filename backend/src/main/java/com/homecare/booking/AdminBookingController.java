package com.homecare.booking;

import com.homecare.booking.BookingDtos.*;
import jakarta.validation.Valid;
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
    public AdminBooking updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        return service.updateStatus(id, req.status());
    }

    @PostMapping("/{id}/resend-alert")
    public AdminBooking resendAlert(@PathVariable Long id) {
        return service.resendAlert(id);
    }
}
