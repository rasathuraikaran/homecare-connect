package com.homecare.notification;

public enum NotificationStatus {
    PENDING,   // waiting to be sent (or to be retried)
    SENT,      // accepted by WhatsApp
    DRY_RUN,   // test mode: only written to the log, nothing was sent
    FAILED     // gave up (permanent error, or too many attempts)
}
