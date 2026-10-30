package com.homecare.payment;

import com.homecare.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Which ways of paying are in use right now. A way is on when this server is set up for it (PayHere keys, bank details in
 * .env.local) and the admin has its switch on. With both off, completing a visit asks for no fee and nothing else changes.
 */
@Component
public class PaymentOptions {

    private static final Logger log = LoggerFactory.getLogger(PaymentOptions.class);

    private final PaymentSwitchesRepository switches;
    private final PayHereSettings payhere;
    private final BankDetails bank;

    public PaymentOptions(PaymentSwitchesRepository switches, PayHereSettings payhere, BankDetails bank) {
        this.switches = switches;
        this.payhere = payhere;
        this.bank = bank;
    }

    public PayMode payhereMode() {
        return payhere.mode();
    }

    /** PayHere is set up on this server (a mode other than off). */
    public boolean payhereAvailable() {
        return payhere.mode().enabled();
    }

    /** The bank details are filled in. */
    public boolean bankAvailable() {
        return bank.configured();
    }

    public boolean payhereOn() {
        return payhereAvailable() && current().isPayhereEnabled();
    }

    public boolean bankOn() {
        return bankAvailable() && current().isBankEnabled();
    }

    /** Whether completing a visit asks for a fee at all. */
    public boolean anyOn() {
        return payhereOn() || bankOn();
    }

    /** Changes only the switches that are given. A way this server is not set up for cannot be switched on. */
    @Transactional
    public void update(User admin, Boolean payhereEnabled, Boolean bankEnabled) {
        if (Boolean.TRUE.equals(payhereEnabled) && !payhereAvailable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PayHere is not set up on this server. Set PAYHERE_MODE and the merchant keys in .env.local first.");
        }
        if (Boolean.TRUE.equals(bankEnabled) && !bankAvailable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank details are not set up on this server. Set BANK_NAME, BANK_ACCOUNT_NAME and BANK_ACCOUNT_NUMBER in .env.local first.");
        }
        PaymentSwitches row = current();
        row.set(payhereEnabled, bankEnabled, admin.getId());
        switches.save(row);
        log.info("Admin {} set payments: PayHere {}, bank transfer {}", admin.getId(), row.isPayhereEnabled(), row.isBankEnabled());
    }

    private PaymentSwitches current() {
        return switches.findById(PaymentSwitches.ID).orElseGet(PaymentSwitches::new);
    }
}
