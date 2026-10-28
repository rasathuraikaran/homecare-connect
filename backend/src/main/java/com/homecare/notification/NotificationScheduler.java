package com.homecare.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks for due notifications every few seconds. Switched off in tests, which call the dispatcher directly. */
@Component
@ConditionalOnProperty(name = "app.notifications.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private final NotificationDispatcher dispatcher;

    public NotificationScheduler(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 5_000)
    void run() {
        try {
            dispatcher.dispatchDue();
        } catch (Exception e) {
            log.error("Notification dispatch failed", e);
        }
    }
}
