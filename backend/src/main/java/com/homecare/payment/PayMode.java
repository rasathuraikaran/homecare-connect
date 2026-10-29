package com.homecare.payment;

/** off: no fees. simulated: a built-in pretend PayHere for trying the flow. sandbox and live: the real PayHere. */
public enum PayMode {
    OFF, SIMULATED, SANDBOX, LIVE;

    public boolean enabled() {
        return this != OFF;
    }
}
