package com.homecare.booking;

import com.homecare.notification.NotificationStatus;
import com.homecare.profile.JobType;
import com.homecare.profile.RateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class BookingDtos {
    private BookingDtos() {}

    public record CreateBookingRequest(
            @NotNull(message = "Choose a caregiver") Long caregiverId,
            RateType rateType,
            @Size(max = 500, message = "Note must be 500 characters or fewer") String note,
            @NotBlank(message = "Enter the address where care is needed") @Size(max = 300, message = "Address must be 300 characters or fewer") String address,
            @NotBlank(message = "Enter a phone number so we can reach you")
            @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "Enter a valid phone number") String phone) {}

    public record UpdateStatusRequest(@NotNull(message = "Choose a status") BookingStatus status) {}

    /** What a care seeker sees about their own request. */
    public record BookingResponse(Long id, BookingStatus status, Long caregiverId, String caregiverName, JobType jobType,
                                  RateType rateType, BigDecimal quotedAmount, String note, String address, Instant createdAt) {}

    public record Person(Long id, String name, String email) {}

    /** The state of the alert for a booking (channel is EMAIL or WHATSAPP), so the admin knows whether it went out. */
    public record Alert(String channel, NotificationStatus status, int attempts, String error, Instant sentAt) {}

    /** What the admin sees: both people, the seeker's phone, where the care is needed, and whether the alert was delivered. */
    public record AdminBooking(Long id, BookingStatus status, Instant createdAt, Person seeker, String seekerPhone, String address,
                               Person caregiver, String caregiverAddress, JobType jobType, RateType rateType, BigDecimal quotedAmount,
                               String note, Alert alert) {}
}
