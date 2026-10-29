package com.homecare.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties(PayHereProperties.class)
public class PayHereConfig {

    static final String LIVE_URL = "https://www.payhere.lk/pay/checkout";        // PayHere says the www is required
    static final String SANDBOX_URL = "https://sandbox.payhere.lk/pay/checkout";
    static final String NOTIFY_PATH = "/api/payments/payhere/notify";

    private static final Logger log = LoggerFactory.getLogger(PayHereConfig.class);

    @Bean
    PayHereSettings payHereSettings(PayHereProperties p) {
        String site = trimSlash(p.siteUrl());
        switch (p.mode()) {
            case OFF -> {
                log.info("Online payments are off: completing a visit asks for no fee. Set PAYHERE_MODE to simulated, sandbox or live to turn them on.");
                return new PayHereSettings(PayMode.OFF, "", "", "", "", site);
            }
            case SIMULATED -> {
                log.warn("Payments are SIMULATED: a built-in pretend PayHere page is used and nothing real is charged.");
                // a new secret each start, kept in memory only: nobody outside this process can sign a callback, only the simulate button can
                return new PayHereSettings(PayMode.SIMULATED, "SIMULATED", UUID.randomUUID().toString(), null, "", site);
            }
            default -> {
                // Fail at startup, not when the first seeker tries to pay, when a setting is missing
                List<String> missing = new ArrayList<>();
                if (blank(p.merchantId())) missing.add("PAYHERE_MERCHANT_ID");
                if (blank(p.merchantSecret())) missing.add("PAYHERE_MERCHANT_SECRET");
                if (blank(p.publicApiUrl())) missing.add("PUBLIC_API_URL");
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("PayHere is set to " + p.mode().name().toLowerCase() + " but these settings are missing: " + String.join(", ", missing));
                }
                String api = trimSlash(p.publicApiUrl());
                if (!api.matches("https?://.+")) {
                    throw new IllegalStateException("PUBLIC_API_URL must start with http:// or https://");
                }
                if (p.mode() == PayMode.LIVE && !api.startsWith("https://")) {
                    throw new IllegalStateException("PUBLIC_API_URL must be https:// when payments are live");
                }
                if (api.contains("localhost") || api.contains("127.0.0.1")) {
                    log.warn("PUBLIC_API_URL points at this computer, so PayHere cannot call back and payments will stay unconfirmed. Use a public address or a tunnel.");
                }
                log.info("Payments are ON through PayHere {} for merchant {}", p.mode().name().toLowerCase(), p.merchantId());
                return new PayHereSettings(p.mode(), p.merchantId().trim(), p.merchantSecret().trim(),
                        p.mode() == PayMode.LIVE ? LIVE_URL : SANDBOX_URL, api + NOTIFY_PATH, site);
            }
        }
    }

    private static String trimSlash(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
