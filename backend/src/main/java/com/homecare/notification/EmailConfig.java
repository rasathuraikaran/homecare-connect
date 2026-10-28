package com.homecare.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Configuration
@EnableConfigurationProperties({EmailProperties.class, NotificationProperties.class})
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    EmailSender emailSender(EmailProperties p) {
        if (!p.enabled()) {
            log.info("Email is in test mode: nothing is sent. Set EMAIL_ENABLED=true to really send.");
            return new LoggingEmailSender();
        }
        // Fail at startup, not on the first booking, when a setting is missing
        List<String> missing = new ArrayList<>();
        if (blank(p.host())) missing.add("EMAIL_SMTP_HOST");
        if (blank(p.username())) missing.add("EMAIL_USERNAME");
        if (blank(p.password())) missing.add("EMAIL_PASSWORD");
        if (blank(p.adminTo())) missing.add("EMAIL_ADMIN_TO");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Email is enabled but these settings are missing: " + String.join(", ", missing));
        }
        if (p.port() < 1 || p.port() > 65535) {
            throw new IllegalStateException("EMAIL_SMTP_PORT must be a port number such as 587");
        }
        if (!EmailProperties.ADDRESS.matcher(p.sender()).matches()) {
            throw new IllegalStateException("The sender '" + p.sender() + "' is not an email address. Set EMAIL_FROM (EMAIL_USERNAME is used when it is empty)");
        }
        for (String address : p.recipients()) {
            if (!EmailProperties.ADDRESS.matcher(address).matches()) {
                throw new IllegalStateException("EMAIL_ADMIN_TO must hold email addresses separated by commas, but '" + address + "' is not one");
            }
        }

        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(p.host());
        mail.setPort(p.port());
        mail.setUsername(p.username());
        mail.setPassword(p.password());
        mail.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties smtp = mail.getJavaMailProperties();
        smtp.put("mail.smtp.auth", "true");
        if (p.port() == 465) {
            smtp.put("mail.smtp.ssl.enable", "true");
        } else {
            smtp.put("mail.smtp.starttls.enable", "true");
            smtp.put("mail.smtp.starttls.required", "true");   // never send the password over an unencrypted connection
        }
        smtp.put("mail.smtp.ssl.checkserveridentity", "true");
        // do not let a slow mail server hold up our own work for long
        smtp.put("mail.smtp.connectiontimeout", "5000");
        smtp.put("mail.smtp.timeout", "10000");
        smtp.put("mail.smtp.writetimeout", "10000");

        log.info("Email alerts are ON: sending to {} through {}:{}", SmtpEmailSender.mask(p.adminTo()), p.host(), p.port());
        return new SmtpEmailSender(mail, p.sender());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
