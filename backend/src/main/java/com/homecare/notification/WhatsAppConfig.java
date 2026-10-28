package com.homecare.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppConfig {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConfig.class);

    @Bean
    WhatsAppSender whatsAppSender(WhatsAppProperties p, RestClient.Builder builder) {
        if (!p.enabled()) {
            log.info("WhatsApp is in test mode: nothing is sent. Set WHATSAPP_ENABLED=true to really send.");
            return new LoggingWhatsAppSender();
        }
        // Fail at startup, not on the first booking, when a setting is missing
        List<String> missing = new ArrayList<>();
        if (blank(p.token())) missing.add("WHATSAPP_TOKEN");
        if (blank(p.phoneNumberId())) missing.add("WHATSAPP_PHONE_NUMBER_ID");
        if (blank(p.apiVersion())) missing.add("WHATSAPP_API_VERSION");
        if (blank(p.adminTo())) missing.add("WHATSAPP_ADMIN_TO");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("WhatsApp is enabled but these settings are missing: " + String.join(", ", missing));
        }
        if (!p.adminTo().matches("\\d{8,15}")) {
            throw new IllegalStateException("WHATSAPP_ADMIN_TO must be digits only, with the country code and no + or spaces (for example 94771234567)");
        }
        // do not let a slow WhatsApp hold up our own work for long
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(5_000);
        timeouts.setReadTimeout(10_000);
        builder.requestFactory(timeouts);
        log.info("WhatsApp alerts are ON: sending to {} with template '{}'", WhatsAppCloudSender.mask(p.adminTo()), p.templateName());
        return new WhatsAppCloudSender(p, builder);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
