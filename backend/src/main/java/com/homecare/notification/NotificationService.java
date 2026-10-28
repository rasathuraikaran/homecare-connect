package com.homecare.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String EMAIL_TEMPLATE = "new_booking_details";

    private final NotificationRepository notifications;
    private final WhatsAppProperties whatsApp;
    private final EmailProperties email;
    private final AlertChannel channel;
    private final ObjectMapper json;

    public NotificationService(NotificationRepository notifications, WhatsAppProperties whatsApp, EmailProperties email,
                               NotificationProperties settings, ObjectMapper json) {
        this.notifications = notifications;
        this.whatsApp = whatsApp;
        this.email = email;
        this.channel = settings.channel();
        this.json = json;

        boolean live = channel == AlertChannel.EMAIL ? email.enabled() : whatsApp.enabled();
        if (live) {
            log.info("Admin alerts are sent by {}", channel);
        } else {
            log.warn("Admin alerts ({}) are in TEST MODE: messages are only written to the log. Set {}=true to really send.",
                    channel, channel == AlertChannel.EMAIL ? "EMAIL_ENABLED" : "WHATSAPP_ENABLED");
        }
    }

    /** Queues the admin alert. Call inside the booking's transaction so both are saved together or not at all. */
    public Notification queueNewBookingAlert(NewBookingAlert alert) {
        Notification n = switch (channel) {
            case EMAIL -> Notification.email(alert.bookingId(), recipientOrNotSet(email.adminTo()),
                    EMAIL_TEMPLATE, "en", toJson(alert.emailParams()));
            case WHATSAPP -> Notification.whatsapp(alert.bookingId(), recipientOrNotSet(whatsApp.adminTo()),
                    whatsApp.templateName(), whatsApp.language(), toJson(alert.params()));
        };
        return notifications.save(n);
    }

    private static String recipientOrNotSet(String value) {
        return value == null || value.isBlank() ? "not-set" : value.trim();
    }

    private String toJson(List<String> params) {
        try {
            return json.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String cleanParam(String value) {
        return cleanParam(value, 60);
    }

    /**
     * WhatsApp template variables may not hold line breaks, tabs or long runs of spaces, and long values
     * are rejected, so squash whitespace and shorten. Email gets the same treatment so the subject can never hold a line break.
     */
    static String cleanParam(String value, int maxLength) {
        String s = value == null ? "" : value.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (s.length() > maxLength) s = s.substring(0, maxLength - 1) + "…";
        return s.isEmpty() ? "-" : s;
    }
}
