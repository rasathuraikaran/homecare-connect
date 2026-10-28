package com.homecare.notification;

public class EmailSendException extends AlertSendException {

    public EmailSendException(String message, boolean retryable) {
        super(message, retryable);
    }
}
