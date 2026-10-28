package com.homecare.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        @DefaultValue("false") boolean enabled,
        String host,
        @DefaultValue("587") int port,
        String username,
        String password,
        String from,
        String adminTo,
        @DefaultValue("http://localhost:5173/admin") String adminUrl) {

    /** Deliberately simple: no spaces, commas or angle brackets, so a setting can never smuggle in a second header line or recipient. */
    static final Pattern ADDRESS = Pattern.compile("^[^@\\s,<>]+@[^@\\s,<>]+\\.[^@\\s,<>]+$");

    /** The address mail is sent from. Gmail only accepts the signed-in account, so this defaults to the username. */
    public String sender() {
        return from == null || from.isBlank() ? username : from.trim();
    }

    /** The admin addresses from the comma-separated setting; empty when it is not set. */
    public List<String> recipients() {
        if (adminTo == null || adminTo.isBlank()) return List.of();
        return Arrays.stream(adminTo.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Never print the password, even if these properties end up in a log line. */
    @Override
    public String toString() {
        return "EmailProperties[enabled=" + enabled + ", host=" + host + ", port=" + port + ", password=***]";
    }
}
