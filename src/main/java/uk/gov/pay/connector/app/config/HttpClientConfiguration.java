package uk.gov.pay.connector.app.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.Optional;

/**
 * The connection pool and timeouts of the HTTP client that talks to the payment gateways, as the
 * {@code jerseyClient} section of the configuration describes them.
 */
public class HttpClientConfiguration {

    @NotNull
    @JsonProperty
    private Duration timeout = Duration.ofMillis(500);

    @NotNull
    @JsonProperty
    private Duration connectionTimeout = Duration.ofMillis(500);

    @NotNull
    @JsonProperty
    private Duration timeToLive = Duration.ofHours(1);

    @JsonProperty
    private boolean cookiesEnabled = false;

    @Min(1)
    @Max(Integer.MAX_VALUE)
    @JsonProperty
    private int maxConnections = 1024;

    @Min(1)
    @Max(Integer.MAX_VALUE)
    @JsonProperty
    private int maxConnectionsPerRoute = 1024;

    @NotNull
    @JsonProperty
    private Duration keepAlive = Duration.ZERO;

    @Min(0)
    @Max(1000)
    @JsonProperty
    private int retries = 0;

    @JsonProperty
    private String userAgent;

    @JsonProperty
    private boolean gzipEnabledForRequests = true;

    public Duration getTimeout() {
        return timeout;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public Duration getTimeToLive() {
        return timeToLive;
    }

    public boolean isCookiesEnabled() {
        return cookiesEnabled;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public Duration getKeepAlive() {
        return keepAlive;
    }

    public int getRetries() {
        return retries;
    }

    public Optional<String> getUserAgent() {
        return Optional.ofNullable(userAgent);
    }

    public boolean isGzipEnabledForRequests() {
        return gzipEnabledForRequests;
    }
}
