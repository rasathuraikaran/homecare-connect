package com.homecare.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The admin's two switches: may seekers pay through PayHere, and may they pay by bank transfer. One row, id 1. */
@Entity
@Table(name = "payment_settings")
public class PaymentSwitches {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    @Column(name = "payhere_enabled", nullable = false)
    private boolean payhereEnabled = true;

    @Column(name = "bank_enabled", nullable = false)
    private boolean bankEnabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    public PaymentSwitches() {}

    public void set(Boolean payhere, Boolean bank, Long adminId) {
        if (payhere != null) this.payhereEnabled = payhere;
        if (bank != null) this.bankEnabled = bank;
        this.updatedAt = Instant.now();
        this.updatedBy = adminId;
    }

    public boolean isPayhereEnabled() { return payhereEnabled; }
    public boolean isBankEnabled() { return bankEnabled; }
}
