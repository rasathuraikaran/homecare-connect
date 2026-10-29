package com.homecare.payment;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** One attempt to pay the fee of a booking. A booking can have several (a failed card, then a good one). */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentSource source;

    private String method;

    @Column(name = "payhere_payment_id")
    private String payherePaymentId;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Payment() {}

    public Payment(Long bookingId, String orderId, BigDecimal amount, String currency, PaymentSource source) {
        this.bookingId = bookingId;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.source = source;
    }

    /** A payment the admin saw happen outside PayHere. */
    public static Payment offline(Long bookingId, String orderId, BigDecimal amount, String currency) {
        Payment p = new Payment(bookingId, orderId, amount, currency, PaymentSource.MANUAL);
        p.status = PaymentStatus.PAID;
        p.method = "OFFLINE";
        p.paidAt = Instant.now();
        return p;
    }

    /** Applies what PayHere said. Text fields are cut to what the columns hold. */
    public void record(PaymentStatus next, Integer code, String method, String payherePaymentId) {
        this.status = next;
        this.statusCode = code;
        if (method != null) this.method = cut(method, 20);
        if (payherePaymentId != null) this.payherePaymentId = cut(payherePaymentId, 40);
        this.updatedAt = Instant.now();
        if (next == PaymentStatus.PAID && paidAt == null) paidAt = Instant.now();
    }

    public void cancelAttempt() {
        this.status = PaymentStatus.CANCELED;
        this.updatedAt = Instant.now();
    }

    private static String cut(String s, int max) {
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public String getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public PaymentSource getSource() { return source; }
    public String getMethod() { return method; }
    public String getPayherePaymentId() { return payherePaymentId; }
    public Instant getPaidAt() { return paidAt; }
}
