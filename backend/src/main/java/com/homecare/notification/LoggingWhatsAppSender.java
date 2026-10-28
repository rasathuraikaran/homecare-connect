package com.homecare.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Test mode: writes the message to the log instead of sending it. */
public class LoggingWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingWhatsAppSender.class);

    @Override
    public Outcome send(String to, String template, String language, List<String> bodyParams) {
        log.info("[WhatsApp TEST MODE - not sent] to={} template={} language={}\n----------\n{}\n----------",
                WhatsAppCloudSender.mask(to), template, language, NewBookingAlert.preview(bodyParams));
        return Outcome.DRY_RUN;
    }
}
