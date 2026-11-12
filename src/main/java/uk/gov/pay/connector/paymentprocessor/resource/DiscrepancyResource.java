package uk.gov.pay.connector.paymentprocessor.resource;

import jakarta.inject.Inject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.paymentprocessor.service.DiscrepancyService;
import uk.gov.pay.connector.paymentprocessor.model.GatewayStatusComparison;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import uk.gov.pay.connector.web.MediaTypes;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.validation.annotation.Validated;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Discrepancies")
@RestController
@Validated
public class DiscrepancyResource {

    private final DiscrepancyService discrepancyService;

    @Inject
    public DiscrepancyResource(DiscrepancyService discrepancyService) {
        this.discrepancyService = discrepancyService;
    }

    @PostMapping("/v1/api/discrepancies/report")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Compare charge status with gateway",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = GatewayStatusComparison.class)))),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public List<GatewayStatusComparison> listDiscrepancies(
            @Parameter(array = @ArraySchema(schema = @Schema(implementation = String.class, example = "charge-external-id")))
            @NotEmpty @RequestBody(required = false) List<String> chargeIds) {
        return discrepancyService.listGatewayStatusComparisons(chargeIds);
    }

    @PostMapping("/v1/api/discrepancies/resolve")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Resolve charge status discrepancy",
            description = "When charge status mismatches with Gateway and is in cancellable state, charge is cancelled. Otherwise no action takes place",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = GatewayStatusComparison.class)))),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public List<GatewayStatusComparison> resolveDiscrepancies(
            @Parameter(array = @ArraySchema(schema = @Schema(implementation = String.class, example = "charge-external-id")))
            @NotEmpty @RequestBody(required = false) List<String> chargeIds) {
        return discrepancyService.resolveDiscrepancies(chargeIds);
    }
}
