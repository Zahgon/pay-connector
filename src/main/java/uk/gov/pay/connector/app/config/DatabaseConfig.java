package uk.gov.pay.connector.app.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The PostgreSQL connection and its pool, as the {@code database} section of the configuration describes them.
 */
public class DatabaseConfig {

    @NotNull
    @JsonProperty
    private String driverClass;

    @JsonProperty
    private String user;

    @JsonProperty
    private String password;

    @NotNull
    @JsonProperty
    private String url;

    @NotNull
    @JsonProperty
    private Duration maxWaitForConnection = Duration.ofSeconds(30);

    @JsonProperty
    private String validationQuery;

    @JsonProperty
    private Duration validationQueryTimeout;

    @Min(0)
    @JsonProperty
    private int initialSize = 10;

    @Min(0)
    @JsonProperty
    private int minSize = 10;

    @Min(1)
    @JsonProperty
    private int maxSize = 100;

    @JsonProperty
    private boolean checkConnectionWhileIdle = true;

    @JsonProperty
    private Duration evictionInterval = Duration.ofSeconds(5);

    @JsonProperty
    private Duration minIdleTime = Duration.ofMinutes(1);

    @NotNull
    @JsonProperty
    private Map<String, String> properties = new LinkedHashMap<>();

    public String getDriverClass() {
        return driverClass;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }

    public Duration getMaxWaitForConnection() {
        return maxWaitForConnection;
    }

    public String getValidationQuery() {
        return validationQuery;
    }

    public Duration getValidationQueryTimeout() {
        return validationQueryTimeout;
    }

    public int getInitialSize() {
        return initialSize;
    }

    public int getMinSize() {
        return minSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public boolean getCheckConnectionWhileIdle() {
        return checkConnectionWhileIdle;
    }

    public Duration getEvictionInterval() {
        return evictionInterval;
    }

    public Duration getMinIdleTime() {
        return minIdleTime;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
