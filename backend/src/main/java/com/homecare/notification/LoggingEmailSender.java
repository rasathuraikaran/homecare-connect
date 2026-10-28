package com.homecare.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test mode: writes the email to the log instead of sending it. */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public Outcome send(String to, String subject, String body) {
        log.info("[Email TEST MODE - not sent] to={} subject={}\n----------\n{}\n----------",
                SmtpEmailSender.mask(to), subject, body);
        return Outcome.DRY_RUN;
    }
}
