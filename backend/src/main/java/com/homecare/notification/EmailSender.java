package com.homecare.notification;

import java.util.List;

public interface EmailSender {

    enum Outcome { SENT, DRY_RUN }

    /** A file sent along with an email. */
    record Attachment(String fileName, String contentType, byte[] content) {}

    /** Sends one plain-text email to one address or a comma-separated list. Throws EmailSendException when it could not be delivered. */
    Outcome send(String to, String subject, String body);

    /** The same with files attached. A sender that cannot attach files sends the text alone. */
    default Outcome send(String to, String subject, String body, List<Attachment> attachments) {
        return send(to, subject, body);
    }
}
