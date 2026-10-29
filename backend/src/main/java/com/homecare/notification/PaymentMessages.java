package com.homecare.notification;

import java.util.List;

/**
 * The emails about the fee. Each has a fixed list of values, in the order below, saved with the notification and filled
 * into the text when it is sent. Always sent by email: a seeker's WhatsApp number is not known.
 */
public final class PaymentMessages {

    /** To the seeker: [request number, seeker name, caregiver name, amount such as "LKR 500.00", link to the site] */
    public static final String FEE_DUE = "fee_due";
    /** To the admin: [request number, seeker name, amount, method, PayHere payment id, link to the admin page] */
    public static final String PAYMENT_RECEIVED = "payment_received";
    /** To the seeker: [request number, seeker name, caregiver name, amount, method] */
    public static final String PAYMENT_RECEIPT = "payment_receipt";

    private PaymentMessages() {}

    public static boolean isPaymentTemplate(String template) {
        return FEE_DUE.equals(template) || PAYMENT_RECEIVED.equals(template) || PAYMENT_RECEIPT.equals(template);
    }

    public static List<String> feeDue(long bookingId, String seeker, String caregiver, String amount, String link) {
        return clean("#" + bookingId, seeker, caregiver, amount, link);
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
            case PAYMENT_RECEIVED -> "Payment received for request " + at(p, 0);
            case PAYMENT_RECEIPT -> "Your payment for request " + at(p, 0) + " was received";
            default -> throw new IllegalArgumentException("Not a payment message: " + template);
        };
    }

    public static String body(String template, List<String> p) {
        return switch (template) {
            case FEE_DUE -> """
                    Hello %s,

                    Your visit with %s is marked as completed. The HomeCare Connect fee for request %s is %s.

                    To pay securely with PayHere, sign in and press "Pay now" under Your requests:
                    %s

                    If you have already paid, or something looks wrong, just reply to our team.""".formatted(at(p, 1), at(p, 2), at(p, 0), at(p, 3), at(p, 4));
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

    private static String at(List<String> p, int i) {
        return i < p.size() ? p.get(i) : "-";
    }

    private static List<String> clean(String... values) {
        return java.util.Arrays.stream(values).map(v -> NotificationService.cleanParam(v, 300)).toList();
    }
}
