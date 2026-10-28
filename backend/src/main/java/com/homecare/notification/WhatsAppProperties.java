package com.homecare.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsAppProperties(
        @DefaultValue("false") boolean enabled,
        String token,
        String phoneNumberId,
        String apiVersion,
        String adminTo,
        @DefaultValue("new_booking_details") String templateName,
        @DefaultValue("en") String language,
        @DefaultValue("https://graph.facebook.com") String baseUrl) {

    /** Never print the token, even if these properties end up in a log line. */
    @Override
    public String toString() {
        return "WhatsAppProperties[enabled=" + enabled + ", template=" + templateName + ", token=***]";
    }
}
