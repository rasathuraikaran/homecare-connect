package com.homecare.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMessagesTest {

    private static String body(String link, String breakdown, String bank) {
        return PaymentMessages.body(PaymentMessages.FEE_DUE,
                PaymentMessages.feeDue(8, "Sam Seeker", "Nina Fernando", "LKR 9450.00", link, breakdown, bank));
    }

    @Test
    void anEmailOffersOnlyTheWaysThatWereOnWhenItWasQueued() {
        String both = body("https://shop.example", "LKR 9000.00 for the caregiver + LKR 450.00 service fee", "Bank: B, Account name: N, Account number: 1");
        assertTrue(both.contains("press \"Pay now\""), both);
        assertTrue(both.contains("Account number: 1"), both);
        assertTrue(both.contains("reference HC8"), both);
        assertTrue(both.contains("is LKR 9450.00. (LKR 9000.00 for the caregiver + LKR 450.00 service fee)"), both);

        String card = body("https://shop.example", "-", "-");
        assertTrue(card.contains("Pay now"), card);
        assertFalse(card.contains("bank transfer"), card);
        assertFalse(card.contains("("), "no breakdown when only the fee is asked: " + card);

        String bank = body("-", "-", "Bank: B, Account name: N, Account number: 1");
        assertFalse(bank.contains("Pay now"), bank);
        assertTrue(bank.contains("bank transfer"), bank);

        assertTrue(body("-", "-", "-").contains("Our team will contact you about how to pay."));
    }

    @Test
    void anEmailQueuedBeforeBankTransfersExistedStillReadsWell() {
        // the old list had five values: no breakdown and no bank
        String old = PaymentMessages.body(PaymentMessages.FEE_DUE, List.of("#3", "Sam", "Nina", "LKR 450.00", "https://shop.example"));
        assertTrue(old.contains("The amount to pay for request #3 is LKR 450.00."), old);
        assertTrue(old.contains("Pay now"), old);
        assertFalse(old.contains("bank transfer"), old);
        assertEquals("Please pay your HomeCare Connect fee for request #3",
                PaymentMessages.subject(PaymentMessages.FEE_DUE, List.of("#3", "Sam", "Nina", "LKR 450.00", "https://shop.example")));
    }
}
