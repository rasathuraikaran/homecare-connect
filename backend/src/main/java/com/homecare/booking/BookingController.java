package com.homecare.booking;

import com.homecare.booking.BookingDtos.*;
import com.homecare.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@AuthenticationPrincipal User seeker, @Valid @RequestBody CreateBookingRequest req) {
        return service.create(seeker, req);
    }

    @GetMapping("/mine")
    public List<BookingResponse> mine(@AuthenticationPrincipal User seeker) {
        return service.mine(seeker);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal User seeker, @PathVariable Long id) {
        service.cancelMine(seeker, id);
    }
}
