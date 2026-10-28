package com.homecare.booking;

import com.homecare.booking.BookingDtos.*;
import com.homecare.notification.NotificationService;
import com.homecare.profile.CaregiverProfile;
import com.homecare.profile.CaregiverProfileRepository;
import com.homecare.user.User;
import com.homecare.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/** The care seeker's side: ask for a caregiver, see your requests, cancel one that is still waiting. */
@Service
public class BookingService {

    static final int MAX_OPEN_REQUESTS = 3;   // stops one account from flooding the admin's WhatsApp
    private static final int LIST_LIMIT = 50;

    private final BookingRepository bookings;
    private final CaregiverProfileRepository profiles;
    private final UserRepository users;
    private final NotificationService notifications;

    public BookingService(BookingRepository bookings, CaregiverProfileRepository profiles, UserRepository users,
                          NotificationService notifications) {
        this.bookings = bookings;
        this.profiles = profiles;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional
    public BookingResponse create(User seeker, CreateBookingRequest req) {
        CaregiverProfile profile = profiles.findById(req.caregiverId())
                .filter(p -> p.getJobType() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver not found"));
        if (!profile.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This caregiver is not available right now");
        }

        BigDecimal quoted = null;
        if (req.rateType() != null) {
            quoted = profile.getRates().get(req.rateType());
            if (quoted == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This caregiver does not offer that charge type");
            }
        }

        var open = List.of(BookingStatus.REQUESTED, BookingStatus.CONFIRMED);
        if (bookings.existsBySeekerIdAndCaregiverIdAndStatusIn(seeker.getId(), profile.getUserId(), open)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have an open request with this caregiver");
        }
        if (bookings.countBySeekerIdAndStatus(seeker.getId(), BookingStatus.REQUESTED) >= MAX_OPEN_REQUESTS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have " + MAX_OPEN_REQUESTS + " requests waiting. Please wait for our team to reply, or cancel one.");
        }

        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        Booking booking = bookings.save(new Booking(seeker.getId(), profile.getUserId(), req.phone().trim(),
                req.rateType(), quoted, note));

        User caregiver = users.findById(profile.getUserId()).orElseThrow();
        notifications.queueNewBookingAlert(BookingAlerts.of(booking, seeker, caregiver, profile.getJobType()));   // same transaction as the booking
        return toResponse(booking, caregiver.getFullName(), profile.getJobType());
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> mine(User seeker) {
        return bookings.forSeeker(seeker.getId(), PageRequest.of(0, LIST_LIMIT)).stream()
                .map(row -> toResponse((Booking) row[0], (String) row[1], (com.homecare.profile.JobType) row[2]))
                .toList();
    }

    @Transactional
    public void cancelMine(User seeker, Long bookingId) {
        Booking booking = bookings.findById(bookingId)
                .filter(b -> b.getSeekerId().equals(seeker.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (booking.getStatus() != BookingStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a request that is still waiting can be cancelled. Please contact our team.");
        }
        booking.changeStatus(BookingStatus.CANCELLED);
    }

    private static BookingResponse toResponse(Booking b, String caregiverName, com.homecare.profile.JobType job) {
        return new BookingResponse(b.getId(), b.getStatus(), b.getCaregiverId(), caregiverName, job,
                b.getRateType(), b.getQuotedAmount(), b.getNote(), b.getCreatedAt());
    }
}
