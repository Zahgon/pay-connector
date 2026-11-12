package uk.gov.pay.connector.healthcheck.resource;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.ImmutableMap;
import jakarta.inject.Inject;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import uk.gov.pay.connector.web.MediaTypes;
import org.springframework.http.ResponseEntity;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
public class HealthCheckResource {

    private final HealthCheckRegistry healthCheckRegistry;
    private final Logger logger = LoggerFactory.getLogger(HealthCheckResource.class);

    @Inject
    public HealthCheckResource(HealthCheckRegistry healthCheckRegistry) {
        this.healthCheckRegistry = healthCheckRegistry;
    }

    @GetMapping("/healthcheck")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Healthcheck endpoint for connector (checks postgresql, cardExecutorService, ping, sqsQueue, deadlocks)",
            tags = {"Other"},
            responses = {@ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(example = "{" +
                    "    \"database\": {" +
                    "        \"healthy\": true," +
                    "        \"message\": \"Healthy\"" +
                    "    }," +
                    "    \"cardExecutorService\": {" +
                    "        \"healthy\": true," +
                    "        \"message\": \"Healthy\"" +
                    "    }," +
                    "    \"ping\": {" +
                    "        \"healthy\": true," +
                    "        \"message\": \"Healthy\"" +
                    "    }," +
                    "    \"sqsQueue\": {" +
                    "        \"healthy\": true," +
                    "        \"message\": \"Healthy\"" +
                    "    }," +
                    "    \"deadlocks\": {" +
                    "        \"healthy\": true," +
                    "        \"message\": \"Healthy\"" +
                    "    }" +
                    "}")
            )),
                    @ApiResponse(responseCode = "503", description = "Service Unavailable")
            }
    )
    public ResponseEntity<?> healthCheck() {
        SortedMap<String, HealthCheck.Result> results = healthCheckRegistry.runHealthChecks();

        Map<String, Map<String, Object>> response = results.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                                healthCheck -> ImmutableMap.of(
                                        "healthy", healthCheck.getValue().isHealthy(),
                                        "message", Objects.toString(healthCheck.getValue().getMessage(), "Healthy"))
                        )
                );

        if (allHealthy(results.values())) {
            return ResponseEntity.ok(response);
        }
        logger.error("Healthcheck Failure: {}", response);
        return ResponseEntity.status(503).body(response);
    }

    private boolean allHealthy(Collection<HealthCheck.Result> results) {
        return results.stream().allMatch(HealthCheck.Result::isHealthy);
    }
}
