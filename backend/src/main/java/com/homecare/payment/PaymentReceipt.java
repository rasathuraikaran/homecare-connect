package com.homecare.payment;

import jakarta.persistence.*;

import java.time.Instant;

/** A screenshot or PDF a care seeker sent as proof of a bank transfer. The file is kept on disk under a generated name. */
@Entity
@Table(name = "payment_receipts")
public class PaymentReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    private String note;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    protected PaymentReceipt() {}

    public PaymentReceipt(Long bookingId, String fileName, String note) {
        this.bookingId = bookingId;
        this.fileName = fileName;
        this.note = note;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public String getFileName() { return fileName; }
    public String getNote() { return note; }
    public Instant getUploadedAt() { return uploadedAt; }
}
