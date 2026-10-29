package com.homecare.booking;

import com.homecare.notification.NewBookingAlert;
import com.homecare.profile.JobType;
import com.homecare.profile.RateType;
import com.homecare.user.User;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Turns a booking into the plain-text details shown in the admin's WhatsApp alert. */
final class BookingAlerts {

    private static final String CURRENCY = "LKR";   // keep in sync with frontend/src/constants/rates.js

    private BookingAlerts() {}

    static NewBookingAlert of(Booking booking, User seeker, User caregiver, JobType job) {
        String note = booking.getNote() == null || booking.getNote().isBlank() ? "None" : booking.getNote();
        return new NewBookingAlert(booking.getId(), seeker.getFullName(), booking.getSeekerPhone(), seeker.getEmail(), booking.getAddress(),
                caregiver.getFullName(), jobLabel(job), charge(booking.getRateType(), booking.getQuotedAmount()), note);
    }

    private static String jobLabel(JobType job) {
        return switch (job) {
            case CARETAKER -> "Caretaker";
            case NURSING -> "Nursing";
            case PHYSIOTHERAPIST -> "Physiotherapist";
        };
    }

    private static String charge(RateType type, BigDecimal amount) {
        if (type == null || amount == null) return "Not chosen yet";
        String unit = switch (type) {
            case HOURLY -> "per hour";
            case SESSION -> "per session";
            case DAILY -> "per day";
            case FULL_DAY_NIGHT -> "per full day and night";
        };
        // DecimalFormat is not thread-safe, so make one per call
        return CURRENCY + " " + new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(amount) + " " + unit;
    }
}
