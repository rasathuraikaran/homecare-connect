package com.homecare.notification;

import java.util.List;

public interface WhatsAppSender {

    enum Outcome { SENT, DRY_RUN }

    /** Sends one template message. Throws WhatsAppSendException when it could not be delivered. */
    Outcome send(String to, String template, String language, List<String> bodyParams);
}
