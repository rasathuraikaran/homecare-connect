package com.homecare.notification;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    static final int MAX_ATTEMPTS = 5;
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String template;

    @Column(nullable = false)
    private String language;

    /** JSON array of the template's text variables, in the order of {@link NewBookingAlert#TEMPLATE_BODY} */
    @Column(nullable = false)
    private String params;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {}

    public static Notification whatsapp(Long bookingId, String recipient, String template, String language, String paramsJson) {
        return create(AlertChannel.WHATSAPP, bookingId, recipient, template, language, paramsJson);
    }

    /** The template name is only a label here; the email text is built from the same variables when it is sent. */
    public static Notification email(Long bookingId, String recipient, String template, String language, String paramsJson) {
        return create(AlertChannel.EMAIL, bookingId, recipient, template, language, paramsJson);
    }

    private static Notification create(AlertChannel channel, Long bookingId, String recipient, String template, String language, String paramsJson) {
        Notification n = new Notification();
        n.bookingId = bookingId;
        n.channel = channel.name();
        n.recipient = recipient;
        n.template = template;
        n.language = language;
        n.params = paramsJson;
        return n;
    }

    public void markSent(boolean dryRun) {
        attempts++;
        status = dryRun ? NotificationStatus.DRY_RUN : NotificationStatus.SENT;
        sentAt = Instant.now();
        lastError = null;
    }

    public void markFailed(String error, boolean retryable) {
        attempts++;
        lastError = error == null ? "Unknown error" : error.substring(0, Math.min(error.length(), 480));
        if (!retryable || attempts >= MAX_ATTEMPTS) {
            status = NotificationStatus.FAILED;
            return;
        }
        Duration wait = FIRST_BACKOFF.multipliedBy(1L << (attempts - 1));   // 30s, 60s, 2m, 4m
        nextAttemptAt = Instant.now().plus(wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait);
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public String getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public String getTemplate() { return template; }
    public String getLanguage() { return language; }
    public String getParams() { return params; }
    public NotificationStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getSentAt() { return sentAt; }
}
