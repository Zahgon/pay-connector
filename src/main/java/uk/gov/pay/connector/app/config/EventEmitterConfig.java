package uk.gov.pay.connector.app.config;


public class EventEmitterConfig {
    
    private long defaultDoNotRetryEmittingEventUntilDurationInSeconds;

    public long getDefaultDoNotRetryEmittingEventUntilDurationInSeconds() {
        return defaultDoNotRetryEmittingEventUntilDurationInSeconds;
    }
}
