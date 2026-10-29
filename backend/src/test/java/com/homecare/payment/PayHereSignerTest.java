package com.homecare.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** The formulas are PayHere's Checkout API as published; the test works them out again with plain MD5 so a slip in our code shows. */
class PayHereSignerTest {

    private static String md5(String text) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void md5MatchesTheWellKnownValues() {
        assertEquals("D41D8CD98F00B204E9800998ECF8427E", PayHereSigner.md5Upper(""));
        assertEquals("900150983CD24FB0D6963F7D28E17F72", PayHereSigner.md5Upper("abc"));
    }

    @Test
    void amountsHaveTwoDecimalsAndNoSeparators() {
        assertEquals("500.00", PayHereSigner.amount(new BigDecimal("500")));
        assertEquals("1234.50", PayHereSigner.amount(new BigDecimal("1234.5")));
        assertEquals("100.00", PayHereSigner.amount(new BigDecimal("99.999")));
        assertEquals("0.01", PayHereSigner.amount(new BigDecimal("0.005")));
        assertEquals("1000000.00", PayHereSigner.amount(new BigDecimal("1000000")), "no thousands separator");
    }

    @Test
    void theCheckoutHashFollowsPayHeresFormula() throws Exception {
        String expected = md5("1230000" + "HC12-ABC" + "500.00" + "LKR" + md5("my-secret"));
        assertEquals(expected, PayHereSigner.checkoutHash("1230000", "HC12-ABC", new BigDecimal("500"), "LKR", "my-secret"));
        assertEquals(expected, expected.toUpperCase(), "upper case");
        assertNotEquals(expected, PayHereSigner.checkoutHash("1230000", "HC12-ABC", new BigDecimal("501"), "LKR", "my-secret"), "the amount is part of it");
        assertNotEquals(expected, PayHereSigner.checkoutHash("1230000", "HC12-ABC", new BigDecimal("500"), "LKR", "other-secret"), "so is the secret");
    }

    @Test
    void theCallbackSignatureFollowsPayHeresFormula() throws Exception {
        String expected = md5("1230000" + "HC12-ABC" + "500.00" + "LKR" + "2" + md5("my-secret"));
        assertEquals(expected, PayHereSigner.notifySignature("1230000", "HC12-ABC", "500.00", "LKR", "2", "my-secret"));
        assertNotEquals(expected, PayHereSigner.notifySignature("1230000", "HC12-ABC", "500.00", "LKR", "-2", "my-secret"), "a failed payment cannot pass as a paid one");
        assertNotEquals(expected, PayHereSigner.notifySignature("1230000", "HC12-ABC", "5000.00", "LKR", "2", "my-secret"));
    }

    @Test
    void signaturesAreComparedWithoutCaringAboutCaseOrSpaces() {
        String sig = PayHereSigner.notifySignature("1", "o", "5.00", "LKR", "2", "s");
        assertTrue(PayHereSigner.sameSignature(sig, sig));
        assertTrue(PayHereSigner.sameSignature(sig, "  " + sig.toLowerCase() + " "));
        assertFalse(PayHereSigner.sameSignature(sig, sig.substring(1)));
        assertFalse(PayHereSigner.sameSignature(sig, ""));
        assertFalse(PayHereSigner.sameSignature(sig, null));
        assertFalse(PayHereSigner.sameSignature(null, sig));
    }
}
