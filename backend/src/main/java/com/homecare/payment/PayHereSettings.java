package com.homecare.payment;

/** What the payment code works from once the settings have been checked. */
public record PayHereSettings(PayMode mode, String merchantId, String merchantSecret, String checkoutUrl, String notifyUrl, String siteUrl) {

    @Override
    public String toString() {
        return "PayHereSettings[mode=" + mode + ", merchantSecret=***]";
    }
}
