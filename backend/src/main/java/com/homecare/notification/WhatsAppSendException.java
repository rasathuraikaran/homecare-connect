package com.homecare.notification;

public class WhatsAppSendException extends AlertSendException {

    public WhatsAppSendException(String message, boolean retryable) {
        super(message, retryable);
    }
}
