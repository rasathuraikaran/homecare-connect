package com.homecare.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BankDetailsTest {

    @Test
    void theBankNameAccountNameAndNumberAreNeededButNotTheBranch() {
        assertTrue(new BankDetails("Bank", "Name", "123", null).configured());
        assertTrue(new BankDetails("Bank", "Name", "123", "").configured());
        assertFalse(new BankDetails("", "Name", "123", null).configured());
        assertFalse(new BankDetails("Bank", " ", "123", null).configured());
        assertFalse(new BankDetails("Bank", "Name", null, null).configured());
        assertFalse(new BankDetails(null, null, null, null).configured());
    }

    @Test
    void theEmailLineHoldsEverythingAndLeavesOutAnEmptyBranch() {
        assertEquals("Bank: Test Bank, Branch: Jaffna, Account name: Karan Rasa, Account number: 1234567890",
                new BankDetails(" Test Bank ", " Karan Rasa ", " 1234567890 ", " Jaffna ").emailText());
        assertEquals("Bank: Test Bank, Account name: Karan Rasa, Account number: 1234567890",
                new BankDetails("Test Bank", "Karan Rasa", "1234567890", null).emailText());
    }

    @Test
    void theAccountNumberIsNeverPrinted() {
        assertFalse(new BankDetails("Bank", "Name", "1234567890", null).toString().contains("1234567890"));
    }
}
