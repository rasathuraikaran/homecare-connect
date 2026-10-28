package com.homecare.notification;

/** An alert could not be delivered. Says whether trying again later could help. */
public abstract class AlertSendException extends RuntimeException {

    private final boolean retryable;

    protected AlertSendException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    /** True for temporary problems (network, rate limit, outage); false when retrying cannot help. */
    public boolean isRetryable() {
        return retryable;
    }
}
