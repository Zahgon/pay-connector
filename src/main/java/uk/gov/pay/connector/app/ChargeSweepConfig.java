package uk.gov.pay.connector.app;


import java.time.Duration;

public class ChargeSweepConfig {

    private int defaultChargeExpiryThreshold;
    private int awaitingCaptureExpiryThreshold;
    private int tokenExpiryThresholdInSeconds;
    private int skipExpiringChargesLastUpdatedInSeconds;
    private int idempotencyKeyExpiryThresholdInSeconds;
    
    public Duration getDefaultChargeExpiryThreshold() {
        return Duration.ofSeconds(defaultChargeExpiryThreshold);
    }

    public Duration getAwaitingCaptureExpiryThreshold() {
        return Duration.ofSeconds(awaitingCaptureExpiryThreshold);
    }

    public Duration getTokenExpiryThresholdInSeconds() {
        return Duration.ofSeconds(tokenExpiryThresholdInSeconds);
    }

    public Duration getSkipExpiringChargesLastUpdatedInSeconds() {
        return Duration.ofSeconds(skipExpiringChargesLastUpdatedInSeconds);
    }
    
    public Duration getIdempotencyKeyExpiryThresholdInSeconds() {
        return Duration.ofSeconds(idempotencyKeyExpiryThresholdInSeconds);
    }
}
