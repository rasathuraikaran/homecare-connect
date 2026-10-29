package com.homecare.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayHereConfigTest {

    private final PayHereConfig config = new PayHereConfig();

    private static PayHereProperties props(PayMode mode, String id, String secret, String api) {
        return new PayHereProperties(mode, id, secret, api, "https://shop.example.test/");
    }

    @Test
    void offAndSimulatedNeedNothing() {
        PayHereSettings off = config.payHereSettings(props(PayMode.OFF, "", "", ""));
        assertEquals(PayMode.OFF, off.mode());
        assertFalse(off.mode().enabled());

        PayHereSettings simulated = config.payHereSettings(props(PayMode.SIMULATED, "", "", ""));
        assertEquals(PayMode.SIMULATED, simulated.mode());
        assertEquals("SIMULATED", simulated.merchantId());
        assertNotEquals(simulated.merchantSecret(), config.payHereSettings(props(PayMode.SIMULATED, "", "", "")).merchantSecret(),
                "a fresh secret each time, so a forged callback is impossible");
        assertEquals("https://shop.example.test", simulated.siteUrl(), "a trailing slash is dropped");
    }

    @Test
    void sandboxAndLiveNameTheSettingsThatAreMissing() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> config.payHereSettings(props(PayMode.SANDBOX, "", "", "")));
        for (String name : new String[]{"PAYHERE_MERCHANT_ID", "PAYHERE_MERCHANT_SECRET", "PUBLIC_API_URL"}) {
            assertTrue(e.getMessage().contains(name), e.getMessage());
        }
        assertThrows(IllegalStateException.class, () -> config.payHereSettings(props(PayMode.LIVE, "1", "s", "")));
        assertThrows(IllegalStateException.class, () -> config.payHereSettings(props(PayMode.SANDBOX, "1", "s", "api.example.test")), "needs http or https");
        assertThrows(IllegalStateException.class, () -> config.payHereSettings(props(PayMode.LIVE, "1", "s", "http://api.example.test")), "live must be https");
    }

    @Test
    void theRealPayHereAddressesAndTheCallbackAddressAreBuilt() {
        PayHereSettings sandbox = config.payHereSettings(props(PayMode.SANDBOX, "1230000", "s", "https://api.example.test/"));
        assertEquals("https://sandbox.payhere.lk/pay/checkout", sandbox.checkoutUrl());
        assertEquals("https://api.example.test/api/payments/payhere/notify", sandbox.notifyUrl());

        PayHereSettings live = config.payHereSettings(props(PayMode.LIVE, "1230000", "s", "https://api.example.test"));
        assertEquals("https://www.payhere.lk/pay/checkout", live.checkoutUrl(), "PayHere needs the www");
    }

    @Test
    void theSecretIsNeverPrinted() {
        PayHereSettings s = config.payHereSettings(props(PayMode.SANDBOX, "1230000", "super-secret-value", "https://api.example.test"));
        assertFalse(s.toString().contains("super-secret-value"));
        assertFalse(props(PayMode.SANDBOX, "1", "super-secret-value", "x").toString().contains("super-secret-value"));
    }
}
