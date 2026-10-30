package com.homecare.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The bank account seekers send money to when bank transfers are switched on. Set in .env.local, never in code. */
@ConfigurationProperties(prefix = "app.bank")
public record BankDetails(String name, String accountName, String accountNumber, String branch) {

    /** Bank transfers can only be switched on once these three are given. */
    public boolean configured() {
        return filled(name) && filled(accountName) && filled(accountNumber);
    }

    /** One line for the email: the seeker is asked to type the account number, so it is spelled out. */
    public String emailText() {
        StringBuilder text = new StringBuilder("Bank: ").append(name.trim());
        if (filled(branch)) text.append(", Branch: ").append(branch.trim());
        return text.append(", Account name: ").append(accountName.trim()).append(", Account number: ").append(accountNumber.trim()).toString();
    }

    /** Never print the account number, even if these properties end up in a log line. */
    @Override
    public String toString() {
        return "BankDetails[name=" + name + ", accountNumber=***]";
    }

    private static boolean filled(String s) {
        return s != null && !s.isBlank();
    }
}
