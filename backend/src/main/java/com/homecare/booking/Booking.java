package com.homecare.booking;

import com.homecare.profile.RateType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seeker_id", nullable = false)
    private Long seekerId;

    @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.REQUESTED;

    @Column(name = "seeker_phone", nullable = false)
    private String seekerPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type")
    private RateType rateType;

    /** The caregiver's charge at the moment of the request, so later rate changes do not alter it. */
    @Column(name = "quoted_amount", precision = 10, scale = 2)
    private BigDecimal quotedAmount;

    private String note;

    /** Where the care is needed. Empty for requests made before the question was asked. */
    private String address;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Booking() {}

    public Booking(Long seekerId, Long caregiverId, String seekerPhone, String address, RateType rateType, BigDecimal quotedAmount, String note) {
        this.seekerId = seekerId;
        this.caregiverId = caregiverId;
        this.seekerPhone = seekerPhone;
        this.address = address;
        this.rateType = rateType;
        this.quotedAmount = quotedAmount;
        this.note = note;
    }

    public void changeStatus(BookingStatus next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSeekerId() { return seekerId; }
    public Long getCaregiverId() { return caregiverId; }
    public BookingStatus getStatus() { return status; }
    public String getSeekerPhone() { return seekerPhone; }
    public RateType getRateType() { return rateType; }
    public BigDecimal getQuotedAmount() { return quotedAmount; }
    public String getNote() { return note; }
    public String getAddress() { return address; }
    public Instant getCreatedAt() { return createdAt; }
}
