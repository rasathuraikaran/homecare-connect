package com.homecare.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ByteArrayResource;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.util.Arrays;
import java.util.List;

/** Sends plain-text email through an SMTP server such as Gmail's. */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mail;
    private final String from;

    public SmtpEmailSender(JavaMailSender mail, String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public Outcome send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipients(to));
        message.setSubject(subject);
        message.setText(body);
        return deliver(to, () -> mail.send(message));
    }

    @Override
    public Outcome send(String to, String subject, String body, List<Attachment> attachments) {
        if (attachments.isEmpty()) return send(to, subject, body);
        MimeMessage message = mail.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipients(to));
            helper.setSubject(subject);
            helper.setText(body);
            for (Attachment a : attachments) helper.addAttachment(a.fileName(), new ByteArrayResource(a.content()), a.contentType());
        } catch (MessagingException e) {
            throw new EmailSendException("The email could not be built: " + reason(e), false);
        }
        return deliver(to, () -> mail.send(message));
    }

    private static String[] recipients(String to) {
        return Arrays.stream(to.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    private Outcome deliver(String to, Runnable sendIt) {
        try {
            sendIt.run();
            log.info("Email alert sent to {}", mask(to));
            return Outcome.SENT;
        } catch (MailAuthenticationException e) {
            // a wrong password will not fix itself, so do not keep retrying. The server's own text is not repeated: it can name the account.
            throw new EmailSendException("The mail server refused the username or password. "
                    + "Check EMAIL_USERNAME and EMAIL_PASSWORD (Gmail needs an app password, not the normal password).", false);
        } catch (MailParseException | MailPreparationException e) {
            throw new EmailSendException("The email could not be built: " + reason(e), false);
        } catch (MailException e) {
            throw new EmailSendException("Could not send the email: " + reason(e), true);   // network or server trouble: try again later
        }
    }

    /** The most useful one-line explanation in the exception, without any stack trace. */
    static String reason(Throwable e) {
        Throwable t = e.getCause() != null ? e.getCause() : e;
        String text = t.getMessage() == null || t.getMessage().isBlank() ? t.getClass().getSimpleName() : t.getMessage();
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 200 ? text.substring(0, 199) + "…" : text;
    }

    /** sam@example.com becomes s***@example.com so logs do not hold full addresses. A list is masked address by address. */
    static String mask(String addresses) {
        return Arrays.stream(addresses.split(",")).map(String::trim).map(SmtpEmailSender::maskOne)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String maskOne(String address) {
        int at = address.indexOf('@');
        return at < 1 ? "***" : address.charAt(0) + "***" + address.substring(at);
    }
}
