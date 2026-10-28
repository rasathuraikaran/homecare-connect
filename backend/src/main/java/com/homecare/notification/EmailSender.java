package com.homecare.notification;

public interface EmailSender {

    enum Outcome { SENT, DRY_RUN }

    /** Sends one plain-text email to one address or a comma-separated list. Throws EmailSendException when it could not be delivered. */
    Outcome send(String to, String subject, String body);
}
