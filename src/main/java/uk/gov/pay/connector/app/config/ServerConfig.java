package uk.gov.pay.connector.app.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Where the service listens, as the {@code server} section of the configuration describes it: the application port
 * and the admin port, each with the address it binds to.
 */
public class ServerConfig {

    @Valid
    @NotEmpty
    @JsonProperty
    private List<Connector> applicationConnectors = List.of();

    @Valid
    @NotEmpty
    @JsonProperty
    private List<Connector> adminConnectors = List.of();

    @JsonProperty
    private boolean registerDefaultExceptionMappers = true;

    /**
     * The access log's layout; request logging is configured in the logging configuration.
     */
    @JsonProperty
    private JsonNode requestLog;

    public Connector getApplicationConnector() {
        return applicationConnectors.getFirst();
    }

    public Connector getAdminConnector() {
        return adminConnectors.getFirst();
    }

    public boolean getRegisterDefaultExceptionMappers() {
        return registerDefaultExceptionMappers;
    }

    public static class Connector {

        @NotNull
        @JsonProperty
        private String type = "http";

        @JsonProperty
        private String bindHost;

        @JsonProperty
        private int port;

        public String getType() {
            return type;
        }

        public String getBindHost() {
            return bindHost;
        }

        public int getPort() {
            return port;
        }
    }
}
