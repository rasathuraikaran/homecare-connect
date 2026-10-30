package com.homecare.payment;

import com.homecare.booking.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * What the seeker and the admin are shown about the money for a booking. state is NONE (nothing asked), DUE or PAID.
 * fee is the platform's fee, caregiverAmount the caregiver's own charge when it is collected too (otherwise empty), and total
 * what the seeker pays. lastAttempt is how the latest online try ended while it is still due. needsAttention flags a double
 * payment, a charge back, or money that does not match the total, which only a person can sort out.
 */
public record PaymentInfo(BigDecimal fee, BigDecimal caregiverAmount, BigDecimal total, String state, Instant paidAt,
                          String lastAttempt, String source, boolean needsAttention, List<ReceiptInfo> receipts) {

    /** A bank transfer receipt the seeker sent. The file itself is only for the admin. */
    public record ReceiptInfo(Long id, Instant uploadedAt, String note) {}

    public static final String NONE = "NONE";
    public static final String DUE = "DUE";
    public static final String PAID = "PAID";

    public static PaymentInfo of(Booking booking, List<Payment> payments, List<PaymentReceipt> sent) {
        List<ReceiptInfo> receipts = sent.stream().map(r -> new ReceiptInfo(r.getId(), r.getUploadedAt(), r.getNote())).toList();
        if (booking.getFeeAmount() == null) return new PaymentInfo(null, null, null, NONE, null, null, null, false, receipts);
        BigDecimal total = booking.total();
        List<Payment> paid = payments.stream().filter(p -> p.getStatus() == PaymentStatus.PAID).toList();
        // a double payment, a charge back, or money that does not match the total (it was changed after the seeker started paying)
        boolean attention = paid.size() > 1
                || payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.CHARGEDBACK)
                || paid.stream().anyMatch(p -> p.getAmount().compareTo(total) != 0);
        if (!paid.isEmpty()) {
            Payment first = paid.stream().min(Comparator.comparing(Payment::getPaidAt)).orElseThrow();
            return new PaymentInfo(booking.getFeeAmount(), booking.getCaregiverAmount(), total, PAID, first.getPaidAt(), null, first.getSource().name(), attention, receipts);
        }
        String last = payments.stream().max(Comparator.comparing(Payment::getId)).map(p -> p.getStatus().name()).orElse(null);
        return new PaymentInfo(booking.getFeeAmount(), booking.getCaregiverAmount(), total, DUE, null, last, null, attention, receipts);
    }
}
