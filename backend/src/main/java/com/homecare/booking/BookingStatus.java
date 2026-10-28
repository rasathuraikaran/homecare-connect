package com.homecare.booking;

public enum BookingStatus {
    REQUESTED,   // seeker confirmed; waiting for the admin to arrange it
    CONFIRMED,   // admin arranged it with the caregiver
    COMPLETED,
    CANCELLED;

    public boolean canMoveTo(BookingStatus next) {
        return switch (this) {
            case REQUESTED -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /** Still counts against the seeker's limits and blocks a duplicate request. */
    public boolean isOpen() {
        return this == REQUESTED || this == CONFIRMED;
    }
}
