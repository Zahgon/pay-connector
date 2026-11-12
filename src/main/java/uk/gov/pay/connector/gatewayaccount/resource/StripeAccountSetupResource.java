package uk.gov.pay.connector.gatewayaccount.resource;

import jakarta.inject.Inject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetup;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupUpdateRequest;
import uk.gov.pay.connector.gatewayaccount.model.StripeSetupPatchRequest;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.StripeAccountSetupService;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.pay.connector.web.MediaTypes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import uk.gov.pay.connector.common.exception.WebApplicationException;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Gateway accounts")
@RestController
public class StripeAccountSetupResource {
    private final StripeAccountSetupService stripeAccountSetupService;
    private final GatewayAccountService gatewayAccountService;

    @Inject
    public StripeAccountSetupResource(StripeAccountSetupService stripeAccountSetupService,
                                      GatewayAccountService gatewayAccountService) {
        this.stripeAccountSetupService = stripeAccountSetupService;
        this.gatewayAccountService = gatewayAccountService;
    }

    @GetMapping("/v1/api/accounts/{accountId}/stripe-setup")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Retrieve Stripe connect account setup tasks for a given gateway account ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = StripeAccountSetup.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public StripeAccountSetup getStripeAccountSetup(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathVariable("accountId") Long accountId) {
        return gatewayAccountService.getGatewayAccount(accountId)
                .map(gatewayAccountEntity -> stripeAccountSetupService.getCompletedTasks(gatewayAccountEntity.getId()))
                .orElseThrow(() -> new WebApplicationException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/v1/api/service/{serviceId}/account/{accountType}/stripe-setup")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Retrieve Stripe connect account setup tasks for a given service ID and account type",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = StripeAccountSetup.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public StripeAccountSetup getStripeAccountSetupByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathVariable("serviceId") String serviceId, 
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType) {
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .or(() -> { throw new GatewayAccountNotFoundException(serviceId, accountType); })
                .map(gatewayAccountEntity -> stripeAccountSetupService.getCompletedTasks(gatewayAccountEntity.getId()))
                .orElseThrow(() -> new IllegalStateException("Internal Server Error"));
    }

    @PatchMapping("/v1/api/accounts/{accountId}/stripe-setup")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update Stripe Connect account setup tasks have been completed for a given accountId",
            description = "Support patching following paths: <br>" +
                    "bank_account, responsible_person, vat_number, company_number, director, government_entity_document, organisation_details",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(example = "[" +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"bank_account\"," +
                    "        \"value\": true" +
                    "    }," +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"responsible_person\"," +
                    "        \"value\": false" +
                    "    }" +
                    "]"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Content - operation not allowed"),
            }
    )
    public ResponseEntity<?> patchStripeAccountSetup(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathVariable("accountId") Long accountId,
            @Valid @RequestBody(required = false) List<StripeSetupPatchRequest> request) {
        return gatewayAccountService.getGatewayAccount(accountId)
                .map(gatewayAccountEntity -> {
                    List<StripeAccountSetupUpdateRequest> updateRequests = request.stream()
                            .map(StripeAccountSetupUpdateRequest::from)
                            .collect(Collectors.toList());

                    stripeAccountSetupService.update(gatewayAccountEntity, updateRequests);
                    return ResponseEntity.ok().build();
                }).orElseThrow(() -> new WebApplicationException(HttpStatus.NOT_FOUND));
    }

    @PatchMapping("/v1/api/service/{serviceId}/account/{accountType}/stripe-setup")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update Stripe Connect account setup tasks have been completed for a given service ID and account type",
            description = "Support patching following paths: <br>" +
                    "bank_account, responsible_person, vat_number, company_number, director, government_entity_document, organisation_details",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(example = "[" +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"bank_account\"," +
                    "        \"value\": true" +
                    "    }," +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"responsible_person\"," +
                    "        \"value\": false" +
                    "    }" +
                    "]"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Content - operation not allowed"),
            }
    )
    public ResponseEntity<?> patchStripeAccountSetupByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathVariable("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @Valid @RequestBody(required = false) List<StripeSetupPatchRequest> request) {
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .or(() -> { throw new GatewayAccountNotFoundException(serviceId, accountType); })
                .map(gatewayAccountEntity -> {
                    List<StripeAccountSetupUpdateRequest> updateRequests = request.stream()
                            .map(StripeAccountSetupUpdateRequest::from)
                            .collect(Collectors.toList());

                    stripeAccountSetupService.update(gatewayAccountEntity, updateRequests);
                    return ResponseEntity.ok().build();
                })
                .orElseThrow(() -> new IllegalStateException("Internal Server Error"));
    }

}
