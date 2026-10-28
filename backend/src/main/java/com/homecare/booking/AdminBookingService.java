package com.homecare.booking;

import com.homecare.booking.BookingDtos.*;
import com.homecare.notification.Notification;
import com.homecare.notification.NotificationRepository;
import com.homecare.notification.NotificationService;
import com.homecare.notification.NotificationStatus;
import com.homecare.profile.JobType;
import com.homecare.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminBookingService {

    private static final int LIST_LIMIT = 200;

    private final BookingRepository bookings;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;

    public AdminBookingService(BookingRepository bookings, NotificationRepository notifications,
                               NotificationService notificationService) {
        this.bookings = bookings;
        this.notifications = notifications;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<AdminBooking> list(BookingStatus status) {
        List<Object[]> rows = status == null
                ? bookings.forAdmin(PageRequest.of(0, LIST_LIMIT))
                : bookings.forAdminByStatus(status, PageRequest.of(0, LIST_LIMIT));
        return toAdmin(rows);
    }

    @Transactional
    public AdminBooking updateStatus(Long id, BookingStatus next) {
        Booking booking = bookings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!booking.getStatus().canMoveTo(next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + booking.getStatus() + " booking cannot be changed to " + next);
        }
        booking.changeStatus(next);
        bookings.flush();
        return one(id);
    }

    /** Queues the alert again, for when it failed or only ran in test mode. */
    @Transactional
    public AdminBooking resendAlert(Long id) {
        Object[] row = bookings.forAdminById(id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        List<Notification> history = notifications.findByBookingIdInOrderByIdAsc(List.of(id));
        if (!history.isEmpty()) {
            NotificationStatus last = history.get(history.size() - 1).getStatus();
            if (last == NotificationStatus.PENDING || last == NotificationStatus.SENT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The alert is already " + last.name().toLowerCase());
            }
        }
        notificationService.queueNewBookingAlert(BookingAlerts.of((Booking) row[0], (User) row[1], (User) row[2], (JobType) row[3]));
        return one(id);
    }

    private AdminBooking one(Long id) {
        return toAdmin(bookings.forAdminById(id)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private List<AdminBooking> toAdmin(List<Object[]> rows) {
        List<Long> ids = rows.stream().map(r -> ((Booking) r[0]).getId()).toList();
        Map<Long, Notification> latest = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Notification n : notifications.findByBookingIdInOrderByIdAsc(ids)) latest.put(n.getBookingId(), n); // ascending: last wins
        }
        return rows.stream().map(r -> {
            Booking b = (Booking) r[0];
            User seeker = (User) r[1];
            User caregiver = (User) r[2];
            Notification n = latest.get(b.getId());
            Alert alert = n == null ? null : new Alert(n.getChannel(), n.getStatus(), n.getAttempts(), n.getLastError(), n.getSentAt());
            return new AdminBooking(b.getId(), b.getStatus(), b.getCreatedAt(),
                    new Person(seeker.getId(), seeker.getFullName(), seeker.getEmail()), b.getSeekerPhone(),
                    new Person(caregiver.getId(), caregiver.getFullName(), caregiver.getEmail()), (JobType) r[3],
                    b.getRateType(), b.getQuotedAmount(), b.getNote(), alert);
        }).toList();
    }
}
