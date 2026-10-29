package com.homecare.payment;

import com.homecare.booking.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * What the seeker and the admin are shown about a booking's fee. state is NONE (no fee asked), DUE or PAID.
 * lastAttempt is how the latest try ended while the fee is still due. needsAttention flags a double payment or a charge back,
 * which only a person can sort out in PayHere.
 */
public record PaymentInfo(BigDecimal fee, String state, Instant paidAt, String lastAttempt, String source, boolean needsAttention) {

    public static final String NONE = "NONE";
    public static final String DUE = "DUE";
    public static final String PAID = "PAID";

    public static PaymentInfo of(Booking booking, List<Payment> payments) {
        if (booking.getFeeAmount() == null) return new PaymentInfo(null, NONE, null, null, null, false);
        List<Payment> paid = payments.stream().filter(p -> p.getStatus() == PaymentStatus.PAID).toList();
        // a double payment, a charge back, or money that does not match the fee (it was changed after the seeker started paying)
        boolean attention = paid.size() > 1
                || payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.CHARGEDBACK)
                || paid.stream().anyMatch(p -> p.getAmount().compareTo(booking.getFeeAmount()) != 0);
        if (!paid.isEmpty()) {
            Payment first = paid.stream().min(Comparator.comparing(Payment::getPaidAt)).orElseThrow();
            return new PaymentInfo(booking.getFeeAmount(), PAID, first.getPaidAt(), null, first.getSource().name(), attention);
        }
        String last = payments.stream().max(Comparator.comparing(Payment::getId)).map(p -> p.getStatus().name()).orElse(null);
        return new PaymentInfo(booking.getFeeAmount(), DUE, null, last, null, attention);
    }
}
