package uk.gov.pay.connector.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;

public class CustomJerseyClientConfiguration {
    
    private Duration readTimeout;
    
    private Duration connectionTTL;

    @JsonProperty
    public Duration getReadTimeout() {
        return this.readTimeout;
    }

    @JsonProperty
    public Duration getConnectionTTL() {
        return connectionTTL;
    }
}
