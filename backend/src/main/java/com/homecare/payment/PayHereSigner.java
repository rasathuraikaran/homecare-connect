package com.homecare.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** The two signatures PayHere uses, exactly as its Checkout API describes them. MD5 is PayHere's choice, not ours. */
public final class PayHereSigner {

    private PayHereSigner() {}

    public static String md5Upper(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Two decimals, a dot, no thousands separator: 500 becomes 500.00 and 1234.5 becomes 1234.50. */
    public static String amount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** The hash that goes in the checkout form. Worked out on the server only: it needs the merchant secret. */
    public static String checkoutHash(String merchantId, String orderId, BigDecimal amount, String currency, String merchantSecret) {
        return md5Upper(merchantId + orderId + amount(amount) + currency + md5Upper(merchantSecret));
    }

    /** What md5sig should be for a callback. The amount and status code are the exact texts PayHere sent. */
    public static String notifySignature(String merchantId, String orderId, String payhereAmount, String currency, String statusCode, String merchantSecret) {
        return md5Upper(merchantId + orderId + payhereAmount + currency + statusCode + md5Upper(merchantSecret));
    }

    /** Compares in constant time, so the check does not leak how much of a guess was right. */
    public static boolean sameSignature(String expected, String received) {
        if (expected == null || received == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }
}
