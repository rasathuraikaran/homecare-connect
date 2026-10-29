package com.homecare.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.payhere")
public record PayHereProperties(
        @DefaultValue("off") PayMode mode,
        String merchantId,
        String merchantSecret,
        /** The public address of this backend, such as https://api.example.com. PayHere calls it back, so localhost will not do. */
        String publicApiUrl,
        /** The address families open in the browser, where PayHere sends them back. */
        @DefaultValue("http://localhost:5173") String siteUrl) {

    /** Never print the secret, even if these properties end up in a log line. */
    @Override
    public String toString() {
        return "PayHereProperties[mode=" + mode + ", merchantId=" + merchantId + ", merchantSecret=***]";
    }
}
