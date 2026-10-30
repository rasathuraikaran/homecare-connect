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
import com.homecare.payment.PaymentReceipt;
import com.homecare.payment.PaymentReceiptRepository;
import com.homecare.payment.ReceiptStorage;
import com.homecare.verification.DocumentStorage;

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
    private final PaymentReceiptRepository receipts;
    private final ReceiptStorage receiptFiles;

    public NotificationDispatcher(NotificationRepository notifications, WhatsAppSender whatsApp, EmailSender email,
                                  EmailProperties emailSettings, TransactionTemplate tx, ObjectMapper json,
                                  PaymentReceiptRepository receipts, ReceiptStorage receiptFiles) {
        this.notifications = notifications;
        this.whatsApp = whatsApp;
        this.email = email;
        this.emailSettings = emailSettings;
        this.tx = tx;
        this.json = json;
        this.receipts = receipts;
        this.receiptFiles = receiptFiles;
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

    /** The receipt a seeker uploaded goes along with the admin's email. A file that is gone is left out; the email still says what happened. */
    private List<EmailSender.Attachment> attachments(String template, List<String> params) {
        if (!PaymentMessages.RECEIPT_UPLOADED.equals(template)) return List.of();
        Long id = PaymentMessages.receiptId(params);
        PaymentReceipt receipt = id == null ? null : receipts.findById(id).orElse(null);
        byte[] content = receipt == null ? null : receiptFiles.read(receipt.getFileName());
        if (content == null) return List.of();
        String name = receipt.getFileName();
        return List.of(new EmailSender.Attachment("receipt-HC" + receipt.getBookingId() + name.substring(name.lastIndexOf('.')),
                DocumentStorage.mediaType(name).toString(), content));
    }

    /** Sends by the channel the alert was queued for, even if the setting has changed since. @return true when only test mode ran */
    private boolean deliver(Notification n, List<String> params) {
        return switch (AlertChannel.valueOf(n.getChannel())) {
            case EMAIL -> (PaymentMessages.isPaymentTemplate(n.getTemplate())
                    ? email.send(n.getRecipient(), PaymentMessages.subject(n.getTemplate(), params), PaymentMessages.body(n.getTemplate(), params),
                            attachments(n.getTemplate(), params))
                    : email.send(n.getRecipient(), NewBookingAlert.emailSubject(params), NewBookingAlert.emailBody(params, emailSettings.adminUrl())))
                    == EmailSender.Outcome.DRY_RUN;
            case WHATSAPP -> whatsApp.send(n.getRecipient(), n.getTemplate(), n.getLanguage(), params) == WhatsAppSender.Outcome.DRY_RUN;
        };
    }
}
