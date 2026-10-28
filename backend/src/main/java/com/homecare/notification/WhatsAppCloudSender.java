package com.homecare.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Sends template messages through the WhatsApp Business Platform (Cloud API). */
public class WhatsAppCloudSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudSender.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient client;
    private final String path;

    public WhatsAppCloudSender(WhatsAppProperties props, RestClient.Builder builder) {
        this.client = builder
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.token())
                .build();
        this.path = "/" + props.apiVersion() + "/" + props.phoneNumberId() + "/messages";
    }

    @Override
    public Outcome send(String to, String template, String language, List<String> bodyParams) {
        List<Map<String, String>> parameters = new ArrayList<>();
        for (String p : bodyParams) parameters.add(Map.of("type", "text", "text", p));

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "template",
                "template", Map.of(
                        "name", template,
                        "language", Map.of("code", language),
                        "components", List.of(Map.of("type", "body", "parameters", parameters))));
        try {
            client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            log.info("WhatsApp alert sent to {} using template {}", mask(to), template);
            return Outcome.SENT;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;   // other 4xx mean our request or setup is wrong
            throw new WhatsAppSendException("WhatsApp answered HTTP " + status + ": " + metaMessage(e), retryable);
        } catch (ResourceAccessException e) {
            throw new WhatsAppSendException("Could not reach WhatsApp: " + e.getMessage(), true);
        }
    }

    /** Pulls the human-readable reason out of Meta's error body; never includes request headers (the token). */
    static String metaMessage(RestClientResponseException e) {
        try {
            JsonNode message = JSON.readTree(e.getResponseBodyAsString()).path("error").path("message");
            if (!message.isMissingNode() && !message.asText().isBlank()) return message.asText();
        } catch (Exception ignored) {
            // not JSON: fall through
        }
        return e.getStatusText();
    }

    /** 94771234567 becomes ******4567 so logs do not hold full phone numbers. */
    static String mask(String number) {
        if (number == null || number.length() <= 4) return "****";
        return "*".repeat(number.length() - 4) + number.substring(number.length() - 4);
    }
}
