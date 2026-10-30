package com.homecare.booking;

import com.homecare.booking.BookingDtos.*;
import com.homecare.notification.Notification;
import com.homecare.notification.NotificationRepository;
import com.homecare.notification.NotificationService;
import com.homecare.notification.NotificationStatus;
import com.homecare.notification.PaymentMessages;
import com.homecare.payment.Payment;
import com.homecare.payment.PaymentInfo;
import com.homecare.payment.PaymentReceipt;
import com.homecare.payment.PaymentReceiptRepository;
import com.homecare.payment.PaymentRepository;
import com.homecare.payment.PaymentService;
import com.homecare.profile.CaregiverProfile;
import com.homecare.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminBookingService {

    private static final int LIST_LIMIT = 200;

    private final BookingRepository bookings;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final PaymentService paymentService;
    private final PaymentRepository payments;
    private final PaymentReceiptRepository receipts;

    public AdminBookingService(BookingRepository bookings, NotificationRepository notifications,
                               NotificationService notificationService, PaymentService paymentService, PaymentRepository payments,
                               PaymentReceiptRepository receipts) {
        this.bookings = bookings;
        this.notifications = notifications;
        this.notificationService = notificationService;
        this.paymentService = paymentService;
        this.payments = payments;
        this.receipts = receipts;
    }

    @Transactional(readOnly = true)
    public List<AdminBooking> list(BookingStatus status) {
        List<Object[]> rows = status == null
                ? bookings.forAdmin(PageRequest.of(0, LIST_LIMIT))
                : bookings.forAdminByStatus(status, PageRequest.of(0, LIST_LIMIT));
        return toAdmin(rows);
    }

    /**
     * The fee (and the caregiver's charge collected with it) is only for completing a visit while a way of paying is switched on:
     * then the fee is required, and the seeker is asked to pay. With both ways off, completing asks for nothing.
     */
    @Transactional
    public AdminBooking updateStatus(User admin, Long id, BookingStatus next, BigDecimal fee, BigDecimal caregiverAmount) {
        Booking booking = bookings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!booking.getStatus().canMoveTo(next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + booking.getStatus() + " booking cannot be changed to " + next);
        }
        boolean askForFee = next == BookingStatus.COMPLETED && paymentService.enabled();
        if (askForFee) {
            paymentService.requestFee(admin, booking, fee, caregiverAmount);   // refuses a missing or silly amount before anything changes
        } else if (fee != null || caregiverAmount != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A fee is only asked for when a visit is marked completed, and only while a way of paying is switched on");
        }
        booking.changeStatus(next);
        bookings.flush();
        return one(id);
    }

    @Transactional
    public AdminBooking setFee(User admin, Long id, BigDecimal fee, BigDecimal caregiverAmount) {
        paymentService.changeFee(admin, id, fee, caregiverAmount);
        bookings.flush();
        return one(id);
    }

    @Transactional
    public AdminBooking resendPayment(User admin, Long id) {
        paymentService.resend(admin, id);
        return one(id);
    }

    @Transactional
    public AdminBooking markPaid(User admin, Long id) {
        paymentService.markPaidOffline(admin, id);
        bookings.flush();
        return one(id);
    }

    /** Queues the alert again, for when it failed or only ran in test mode. */
    @Transactional
    public AdminBooking resendAlert(Long id) {
        Object[] row = bookings.forAdminById(id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        // the fee emails share the table, but this is about the alert for a new request
        List<Notification> history = notifications.findByBookingIdInOrderByIdAsc(List.of(id)).stream()
                .filter(n -> !PaymentMessages.isPaymentTemplate(n.getTemplate())).toList();
        if (!history.isEmpty()) {
            NotificationStatus last = history.get(history.size() - 1).getStatus();
            if (last == NotificationStatus.PENDING || last == NotificationStatus.SENT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The alert is already " + last.name().toLowerCase());
            }
        }
        notificationService.queueNewBookingAlert(BookingAlerts.of((Booking) row[0], (User) row[1], (User) row[2], ((CaregiverProfile) row[3]).getJobType()));
        return one(id);
    }

    private AdminBooking one(Long id) {
        return toAdmin(bookings.forAdminById(id)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private List<AdminBooking> toAdmin(List<Object[]> rows) {
        List<Long> ids = rows.stream().map(r -> ((Booking) r[0]).getId()).toList();
        Map<Long, Notification> latest = new HashMap<>();
        Map<Long, List<Payment>> paid = new HashMap<>();
        Map<Long, List<PaymentReceipt>> sent = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Notification n : notifications.findByBookingIdInOrderByIdAsc(ids)) {
                if (!PaymentMessages.isPaymentTemplate(n.getTemplate())) latest.put(n.getBookingId(), n); // ascending: last wins
            }
            for (Payment p : payments.findByBookingIdIn(ids)) paid.computeIfAbsent(p.getBookingId(), k -> new ArrayList<>()).add(p);
            for (PaymentReceipt r : receipts.findByBookingIdIn(ids)) sent.computeIfAbsent(r.getBookingId(), k -> new ArrayList<>()).add(r);
        }
        return rows.stream().map(r -> {
            Booking b = (Booking) r[0];
            User seeker = (User) r[1];
            User caregiver = (User) r[2];
            CaregiverProfile profile = (CaregiverProfile) r[3];
            Notification n = latest.get(b.getId());
            Alert alert = n == null ? null : new Alert(n.getChannel(), n.getStatus(), n.getAttempts(), n.getLastError(), n.getSentAt());
            return new AdminBooking(b.getId(), b.getStatus(), b.getCreatedAt(),
                    new Person(seeker.getId(), seeker.getFullName(), seeker.getEmail()), b.getSeekerPhone(), b.getAddress(),
                    new Person(caregiver.getId(), caregiver.getFullName(), caregiver.getEmail()), profile.getAddress(), profile.getJobType(),
                    b.getRateType(), b.getQuotedAmount(), b.getNote(), alert, PaymentInfo.of(b, paid.getOrDefault(b.getId(), List.of()), sent.getOrDefault(b.getId(), List.of())));
        }).toList();
    }
}
