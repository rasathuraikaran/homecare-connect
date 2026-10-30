package com.homecare.notification;

import java.util.List;

/**
 * The emails about the fee. Each has a fixed list of values, in the order below, saved with the notification and filled
 * into the text when it is sent. Always sent by email: a seeker's WhatsApp number is not known.
 */
public final class PaymentMessages {

    /**
     * To the seeker: [request number, seeker name, caregiver name, total such as "LKR 500.00", link to the site or "-" when card
     * payment is off, how the total is made up or "-", the bank details or "-" when bank transfer is off]
     */
    public static final String FEE_DUE = "fee_due";
    /** To the admin, with the receipt attached: [request number, seeker name, total, the seeker's note or "-", link to the admin page, receipt id] */
    public static final String RECEIPT_UPLOADED = "receipt_uploaded";
    /** To the admin: [request number, seeker name, amount, method, PayHere payment id, link to the admin page] */
    public static final String PAYMENT_RECEIVED = "payment_received";
    /** To the seeker: [request number, seeker name, caregiver name, amount, method] */
    public static final String PAYMENT_RECEIPT = "payment_receipt";

    private PaymentMessages() {}

    public static boolean isPaymentTemplate(String template) {
        return FEE_DUE.equals(template) || PAYMENT_RECEIVED.equals(template) || PAYMENT_RECEIPT.equals(template) || RECEIPT_UPLOADED.equals(template);
    }

    public static List<String> feeDue(long bookingId, String seeker, String caregiver, String total, String link, String breakdown, String bank) {
        return clean("#" + bookingId, seeker, caregiver, total, link, breakdown, bank);
    }

    public static List<String> receiptUploaded(long bookingId, String seeker, String total, String note, String adminLink, long receiptId) {
        return clean("#" + bookingId, seeker, total, note, adminLink, String.valueOf(receiptId));
    }

    /** The id of the receipt whose file goes with a receipt_uploaded email, or null when the values do not hold one. */
    public static Long receiptId(List<String> p) {
        try {
            return p.size() > 5 ? Long.valueOf(p.get(5)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<String> paymentReceived(long bookingId, String seeker, String amount, String method, String paymentId, String adminLink) {
        return clean("#" + bookingId, seeker, amount, method, paymentId, adminLink);
    }

    public static List<String> paymentReceipt(long bookingId, String seeker, String caregiver, String amount, String method) {
        return clean("#" + bookingId, seeker, caregiver, amount, method);
    }

    public static String subject(String template, List<String> p) {
        return switch (template) {
            case FEE_DUE -> "Please pay your HomeCare Connect fee for request " + at(p, 0);
            case RECEIPT_UPLOADED -> "Receipt received for request " + at(p, 0);
            case PAYMENT_RECEIVED -> "Payment received for request " + at(p, 0);
            case PAYMENT_RECEIPT -> "Your payment for request " + at(p, 0) + " was received";
            default -> throw new IllegalArgumentException("Not a payment message: " + template);
        };
    }

    public static String body(String template, List<String> p) {
        return switch (template) {
            case FEE_DUE -> feeDueBody(p);
            case RECEIPT_UPLOADED -> """
                    %s says they paid by bank transfer, and sent the receipt (attached).

                    Request: %s
                    Amount: %s
                    Reference they should have used: HC%s
                    Their note: %s

                    Check your bank account. When the money is there, open the admin page and press "Mark paid":
                    %s""".formatted(at(p, 1), at(p, 0), at(p, 2), at(p, 0).replace("#", ""), opt(p, 3) == null ? "None" : opt(p, 3), at(p, 4));
            case PAYMENT_RECEIVED -> """
                    Payment received for request %s.

                    Care seeker: %s
                    Amount: %s
                    Method: %s
                    PayHere payment id: %s

                    Admin page: %s""".formatted(at(p, 0), at(p, 1), at(p, 2), at(p, 3), at(p, 4), at(p, 5));
            case PAYMENT_RECEIPT -> """
                    Hello %s,

                    Thank you. We received your payment of %s for request %s (%s).
                    Payment method: %s

                    HomeCare Connect""".formatted(at(p, 1), at(p, 3), at(p, 0), at(p, 2), at(p, 4));
            default -> throw new IllegalArgumentException("Not a payment message: " + template);
        };
    }

    /** The way to pay depends on what was switched on when the email was queued, so it is built from the saved values. */
    private static String feeDueBody(List<String> p) {
        String link = opt(p, 4), breakdown = opt(p, 5), bank = opt(p, 6);
        StringBuilder text = new StringBuilder("Hello ").append(at(p, 1)).append(",\n\nYour visit with ").append(at(p, 2))
                .append(" is marked as completed. The amount to pay for request ").append(at(p, 0)).append(" is ").append(at(p, 3)).append('.');
        if (breakdown != null) text.append(" (").append(breakdown).append(')');
        text.append("\n\n");
        if (link != null) {
            text.append("To pay by card securely with PayHere, sign in and press \"Pay now\" under Your requests:\n").append(link).append("\n\n");
        }
        if (bank != null) {
            text.append("To pay by bank transfer, send the amount to this account:\n").append(bank)
                    .append("\nPlease write the reference HC").append(at(p, 0).replace("#", "")).append(" with your transfer so we can match it.\n\n");
        }
        if (link == null && bank == null) text.append("Our team will contact you about how to pay.\n\n");
        return text.append("If you have already paid, or something looks wrong, just reply to our team.").toString();
    }

    private static String at(List<String> p, int i) {
        return i < p.size() ? p.get(i) : "-";
    }

    /** A value that was given, or null when it is missing or "-" (also for emails queued before the value existed). */
    private static String opt(List<String> p, int i) {
        String v = i < p.size() ? p.get(i) : null;
        return v == null || v.isBlank() || v.equals("-") ? null : v;
    }

    private static List<String> clean(String... values) {
        return java.util.Arrays.stream(values).map(v -> NotificationService.cleanParam(v, 300)).toList();
    }
}
