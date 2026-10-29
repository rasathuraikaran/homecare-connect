package com.homecare.payment;

import com.homecare.booking.Booking;
import com.homecare.booking.BookingRepository;
import com.homecare.booking.BookingStatus;
import com.homecare.notification.NotificationService;
import com.homecare.notification.PaymentMessages;
import com.homecare.user.User;
import com.homecare.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The fee a seeker pays through PayHere after a visit is completed.
 * The amount always comes from our own database, never from the browser, and a payment only counts when PayHere's
 * signed callback says so: the page the seeker returns to proves nothing.
 */
@Service
public class PaymentService {

    public static final String CURRENCY = "LKR";
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final BigDecimal MAX_FEE = new BigDecimal("9999999.99");

    /** What the browser needs to start paying: a form to post to PayHere, or, when simulated, our own pretend page. */
    public record Checkout(String mode, String actionUrl, Map<String, String> fields, String orderId) {}

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final NotificationService notifications;
    private final PayHereSettings settings;

    public PaymentService(PaymentRepository payments, BookingRepository bookings, UserRepository users,
                          NotificationService notifications, PayHereSettings settings) {
        this.payments = payments;
        this.bookings = bookings;
        this.users = users;
        this.notifications = notifications;
        this.settings = settings;
    }

    public PayMode mode() {
        return settings.mode();
    }

    public boolean enabled() {
        return settings.mode().enabled();
    }

    // ------------------------------------------------------------------ the admin's side

    /** Sets the fee when a visit is completed, and tells the seeker. Called inside the admin's status change. */
    @Transactional
    public void requestFee(User admin, Booking booking, BigDecimal fee) {
        booking.setFee(checkFee(fee));
        queueFeeDue(booking);
        log.info("Admin {} asked for a fee on request {}", admin.getId(), booking.getId());
    }

    @Transactional
    public void changeFee(User admin, Long bookingId, BigDecimal fee) {
        Booking booking = lockedBooking(bookingId);
        requireUnpaidFee(booking);
        BigDecimal amount = checkFee(fee);
        // an attempt for the old amount can no longer succeed: PayHere's answer is checked against what we asked for
        payments.findByBookingIdOrderByIdAsc(bookingId).stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).forEach(Payment::cancelAttempt);
        booking.setFee(amount);
        queueFeeDue(booking);
        log.info("Admin {} changed the fee of request {}", admin.getId(), bookingId);
    }

    /** Sends the "please pay" email again. */
    @Transactional
    public void resend(User admin, Long bookingId) {
        if (!enabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Online payments are switched off, so there is no link to send");
        Booking booking = lockedBooking(bookingId);
        requireUnpaidFee(booking);
        queueFeeDue(booking);
        log.info("Admin {} sent the payment email again for request {}", admin.getId(), bookingId);
    }

    /** The seeker paid some other way (a bank deposit, cash): record it so the fee shows as paid. */
    @Transactional
    public void markPaidOffline(User admin, Long bookingId) {
        Booking booking = lockedBooking(bookingId);
        requireUnpaidFee(booking);
        Payment payment = payments.save(Payment.offline(bookingId, "MANUAL-" + bookingId + "-" + shortId(), booking.getFeeAmount(), CURRENCY));
        sendReceipt(booking, payment);
        log.info("Admin {} recorded an offline payment for request {}", admin.getId(), bookingId);
    }

    // ------------------------------------------------------------------ the seeker's side

    @Transactional
    public Checkout startCheckout(User seeker, Long bookingId) {
        if (!enabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Online payments are switched off right now");
        Booking booking = bookings.findForUpdate(bookingId)
                .filter(b -> b.getSeekerId().equals(seeker.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (booking.getStatus() != BookingStatus.COMPLETED || booking.getFeeAmount() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no fee to pay for this request");
        }
        if (isPaid(bookingId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This fee is already paid");

        String orderId = "HC" + bookingId + "-" + shortId();
        BigDecimal amount = booking.getFeeAmount();
        payments.save(new Payment(bookingId, orderId, amount, CURRENCY, PaymentSource.PAYHERE));
        if (settings.mode() == PayMode.SIMULATED) return new Checkout("SIMULATED", null, Map.of(), orderId);

        String name = seeker.getFullName().trim();
        int space = name.indexOf(' ');
        String address = booking.getAddress() == null || booking.getAddress().isBlank() ? "Not given" : booking.getAddress().trim();
        String[] parts = address.split(",");
        String city = parts[parts.length - 1].trim().isEmpty() ? "Sri Lanka" : parts[parts.length - 1].trim();

        Map<String, String> f = new LinkedHashMap<>();
        f.put("merchant_id", settings.merchantId());
        f.put("return_url", settings.siteUrl() + "/pay/return?booking=" + bookingId);
        f.put("cancel_url", settings.siteUrl() + "/pay/cancel?booking=" + bookingId);
        f.put("notify_url", settings.notifyUrl());
        f.put("first_name", space < 0 ? name : name.substring(0, space));
        f.put("last_name", space < 0 ? "-" : name.substring(space + 1).trim());
        f.put("email", seeker.getEmail());
        f.put("phone", booking.getSeekerPhone());
        f.put("address", address);
        f.put("city", city);
        f.put("country", "Sri Lanka");
        f.put("order_id", orderId);
        f.put("items", "Booking fee, request #" + bookingId);
        f.put("currency", CURRENCY);
        f.put("amount", PayHereSigner.amount(amount));
        f.put("hash", PayHereSigner.checkoutHash(settings.merchantId(), orderId, amount, CURRENCY, settings.merchantSecret()));
        return new Checkout(settings.mode().name(), settings.checkoutUrl(), f, orderId);
    }

    // ------------------------------------------------------------------ PayHere's callback

    /**
     * Handles PayHere's server-to-server message. Returns false for anything that is not a genuine, matching message, and
     * changes nothing then. A genuine message that changes nothing (a repeat, or an out-of-order one) returns true.
     */
    @Transactional
    public boolean handleNotification(Map<String, String> p) {
        if (!enabled()) return false;
        String merchantId = p.get("merchant_id"), orderId = p.get("order_id"), amountText = p.get("payhere_amount");
        String currency = p.get("payhere_currency"), code = p.get("status_code"), signature = p.get("md5sig");
        if (blank(merchantId) || blank(orderId) || blank(amountText) || blank(currency) || blank(code) || blank(signature)) {
            log.warn("A PayHere callback was missing fields");
            return false;
        }
        if (!merchantId.equals(settings.merchantId())) {
            log.warn("A PayHere callback was for another merchant");
            return false;
        }
        String expected = PayHereSigner.notifySignature(merchantId, orderId, amountText, currency, code, settings.merchantSecret());
        if (!PayHereSigner.sameSignature(expected, signature)) {
            log.warn("A PayHere callback for order {} had a wrong signature", orderId);
            return false;
        }

        Payment payment = payments.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            log.warn("A signed PayHere callback named an order we do not know: {}", orderId);
            return false;
        }
        BigDecimal reported;
        int status;
        try {
            reported = new BigDecimal(amountText.trim());
            status = Integer.parseInt(code.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (reported.compareTo(payment.getAmount()) != 0 || !currency.equals(payment.getCurrency())) {
            log.warn("A PayHere callback for order {} had a different amount or currency than we asked for", orderId);
            return false;
        }
        PaymentStatus next = switch (status) {
            case 2 -> PaymentStatus.PAID;
            case 0 -> PaymentStatus.PENDING;
            case -1 -> PaymentStatus.CANCELED;
            case -2 -> PaymentStatus.FAILED;
            case -3 -> PaymentStatus.CHARGEDBACK;
            default -> null;
        };
        if (next == null) return false;

        Booking booking = bookings.findForUpdate(payment.getBookingId()).orElse(null);   // holds the booking, so two callbacks queue up
        if (booking == null) return false;
        boolean paidBefore = isPaid(booking.getId());
        if (payment.getStatus() == PaymentStatus.PAID && next != PaymentStatus.CHARGEDBACK) return true;   // a repeat or a late message never undoes a payment

        payment.record(next, status, p.get("method"), p.get("payment_id"));
        if (next == PaymentStatus.PAID && !paidBefore) sendReceipt(booking, payment);
        if (next == PaymentStatus.PAID && paidBefore) log.warn("Request {} was paid twice (order {}): one payment needs refunding in PayHere", booking.getId(), orderId);
        if (next == PaymentStatus.CHARGEDBACK) log.warn("The payment for request {} was charged back (order {})", booking.getId(), orderId);
        return true;
    }

    /** The pretend PayHere: only in simulated mode. It builds the same signed callback and runs it through the real checks. */
    @Transactional
    public void simulate(User seeker, String orderId, String outcome) {
        if (settings.mode() != PayMode.SIMULATED) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Payment payment = payments.findByOrderId(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        bookings.findById(payment.getBookingId()).filter(b -> b.getSeekerId().equals(seeker.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        int code = switch (outcome) {
            case "success" -> 2;
            case "failed" -> -2;
            case "cancel" -> -1;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose success, failed or cancel");
        };
        String amountText = PayHereSigner.amount(payment.getAmount());
        Map<String, String> params = new HashMap<>();
        params.put("merchant_id", settings.merchantId());
        params.put("order_id", orderId);
        params.put("payment_id", "SIM" + shortId());
        params.put("payhere_amount", amountText);
        params.put("payhere_currency", payment.getCurrency());
        params.put("status_code", String.valueOf(code));
        params.put("method", "SIMULATED");
        params.put("md5sig", PayHereSigner.notifySignature(settings.merchantId(), orderId, amountText, payment.getCurrency(), String.valueOf(code), settings.merchantSecret()));
        if (!handleNotification(params)) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The simulated payment was not accepted");
    }

    // ------------------------------------------------------------------ helpers

    private void sendReceipt(Booking booking, Payment payment) {
        User seeker = users.findById(booking.getSeekerId()).orElseThrow();
        User caregiver = users.findById(booking.getCaregiverId()).orElseThrow();
        String amount = "LKR " + PayHereSigner.amount(payment.getAmount());
        String method = payment.getMethod() == null ? "-" : payment.getMethod();
        notifications.queueMail(booking.getId(), seeker.getEmail(), PaymentMessages.PAYMENT_RECEIPT,
                PaymentMessages.paymentReceipt(booking.getId(), seeker.getFullName(), caregiver.getFullName(), amount, method));
        if (payment.getSource() == PaymentSource.PAYHERE) {   // the admin recorded an offline payment themselves
            notifications.queueAdminMail(booking.getId(), PaymentMessages.PAYMENT_RECEIVED,
                    PaymentMessages.paymentReceived(booking.getId(), seeker.getFullName(), amount, method,
                            payment.getPayherePaymentId() == null ? "-" : payment.getPayherePaymentId(), settings.siteUrl() + "/admin"));
        }
    }

    private void queueFeeDue(Booking booking) {
        User seeker = users.findById(booking.getSeekerId()).orElseThrow();
        User caregiver = users.findById(booking.getCaregiverId()).orElseThrow();
        notifications.queueMail(booking.getId(), seeker.getEmail(), PaymentMessages.FEE_DUE,
                PaymentMessages.feeDue(booking.getId(), seeker.getFullName(), caregiver.getFullName(),
                        "LKR " + PayHereSigner.amount(booking.getFeeAmount()), settings.siteUrl()));
    }

    private Booking lockedBooking(Long id) {
        return bookings.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private void requireUnpaidFee(Booking booking) {
        if (booking.getStatus() != BookingStatus.COMPLETED || booking.getFeeAmount() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a completed visit with a fee can be handled here");
        }
        if (isPaid(booking.getId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "This fee is already paid");
    }

    private boolean isPaid(Long bookingId) {
        return payments.findByBookingIdOrderByIdAsc(bookingId).stream().anyMatch(p -> p.getStatus() == PaymentStatus.PAID);
    }

    private static BigDecimal checkFee(BigDecimal fee) {
        if (fee == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the fee the seeker should pay");
        BigDecimal amount = fee.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.compareTo(MAX_FEE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The fee must be more than zero and at most 9,999,999.99");
        }
        return amount;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
