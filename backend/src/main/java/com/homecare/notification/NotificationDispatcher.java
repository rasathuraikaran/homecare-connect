package com.homecare.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/** Sends the notifications that are due, one at a time, recording the result of each. */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int BATCH = 20;   // also caps how fast alerts can go out

    private final NotificationRepository notifications;
    private final WhatsAppSender whatsApp;
    private final EmailSender email;
    private final EmailProperties emailSettings;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public NotificationDispatcher(NotificationRepository notifications, WhatsAppSender whatsApp, EmailSender email,
                                  EmailProperties emailSettings, TransactionTemplate tx, ObjectMapper json) {
        this.notifications = notifications;
        this.whatsApp = whatsApp;
        this.email = email;
        this.emailSettings = emailSettings;
        this.tx = tx;
        this.json = json;
    }

    /** @return how many notifications were attempted */
    public int dispatchDue() {
        List<Long> due = notifications.findDueIds(Instant.now(), PageRequest.of(0, BATCH));
        for (Long id : due) attempt(id);
        return due.size();
    }

    private void attempt(Long id) {
        tx.executeWithoutResult(status -> {
            Notification n = notifications.findById(id).orElse(null);
            if (n == null || n.getStatus() != NotificationStatus.PENDING) return;
            try {
                List<String> params = json.readValue(n.getParams(), new TypeReference<List<String>>() {});
                n.markSent(deliver(n, params));
            } catch (AlertSendException e) {
                n.markFailed(e.getMessage(), e.isRetryable());
                log.warn("{} alert {} failed (attempt {}, {}): {}", n.getChannel(), id, n.getAttempts(),
                        n.getStatus() == NotificationStatus.FAILED ? "giving up" : "will retry", e.getMessage());
            } catch (Exception e) {
                n.markFailed("Unexpected error: " + e.getClass().getSimpleName(), false);
                log.error("{} alert {} could not be processed", n.getChannel(), id, e);
            }
        });
    }

    /** Sends by the channel the alert was queued for, even if the setting has changed since. @return true when only test mode ran */
    private boolean deliver(Notification n, List<String> params) {
        return switch (AlertChannel.valueOf(n.getChannel())) {
            case EMAIL -> (PaymentMessages.isPaymentTemplate(n.getTemplate())
                    ? email.send(n.getRecipient(), PaymentMessages.subject(n.getTemplate(), params), PaymentMessages.body(n.getTemplate(), params))
                    : email.send(n.getRecipient(), NewBookingAlert.emailSubject(params), NewBookingAlert.emailBody(params, emailSettings.adminUrl())))
                    == EmailSender.Outcome.DRY_RUN;
            case WHATSAPP -> whatsApp.send(n.getRecipient(), n.getTemplate(), n.getLanguage(), params) == WhatsAppSender.Outcome.DRY_RUN;
        };
    }
}
