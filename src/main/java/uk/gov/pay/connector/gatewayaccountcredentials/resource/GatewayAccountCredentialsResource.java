package uk.gov.pay.connector.gatewayaccountcredentials.resource;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.worldpay.Worldpay3dsFlexCredentialsValidationService;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCredentialsValidationService;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsWithInternalId;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsRequest;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayValidatableCredentials;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.Worldpay3dsFlexCredentialsService;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.service.payments.commons.model.jsonpatch.JsonPatchRequest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.pay.connector.web.MediaTypes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.validation.annotation.Validated;

import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Gateway account credentials")
@RestController
@Validated
public class GatewayAccountCredentialsResource {
    private final GatewayAccountService gatewayAccountService;
    private final GatewayAccountCredentialsService gatewayAccountCredentialsService;
    private final Worldpay3dsFlexCredentialsService worldpay3dsFlexCredentialsService;
    private final Worldpay3dsFlexCredentialsValidationService worldpay3dsFlexCredentialsValidationService;
    private final WorldpayCredentialsValidationService worldpayCredentialsValidationService;
    private final GatewayAccountCredentialsRequestValidator gatewayAccountCredentialsRequestValidator;

    @Inject
    public GatewayAccountCredentialsResource(GatewayAccountService gatewayAccountService,
                                             GatewayAccountCredentialsService gatewayAccountCredentialsService,
                                             Worldpay3dsFlexCredentialsService worldpay3dsFlexCredentialsService,
                                             Worldpay3dsFlexCredentialsValidationService worldpay3dsFlexCredentialsValidationService,
                                             WorldpayCredentialsValidationService worldpayCredentialsValidationService,
                                             GatewayAccountCredentialsRequestValidator gatewayAccountCredentialsRequestValidator) {
        this.gatewayAccountService = gatewayAccountService;
        this.worldpay3dsFlexCredentialsService = worldpay3dsFlexCredentialsService;
        this.worldpay3dsFlexCredentialsValidationService = worldpay3dsFlexCredentialsValidationService;
        this.worldpayCredentialsValidationService = worldpayCredentialsValidationService;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
        this.gatewayAccountCredentialsRequestValidator = gatewayAccountCredentialsRequestValidator;
    }

    @PostMapping("/v1/api/accounts/{accountId}/3ds-flex-credentials")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create or update 3DS flex credentials (worldpay accounts)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid or missing mandatory fields",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - account not found or not a Worldpay gateway account")
            }
    )
    public ResponseEntity<?> createOrUpdateWorldpay3dsCredentials(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathVariable("accountId") Long gatewayAccountId,
            @Valid @RequestBody(required = false) Worldpay3dsFlexCredentialsRequest worldpay3dsCredentials) {

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .filter(gatewayAccountEntity -> (gatewayAccountEntity.getGatewayName().equals(PaymentGatewayName.WORLDPAY.getName()) || gatewayAccountEntity.hasPendingWorldpayCredential()))
                .map(gatewayAccountEntity -> {
                    worldpay3dsFlexCredentialsService.setGatewayAccountWorldpay3dsFlexCredentials(worldpay3dsCredentials,
                            gatewayAccountEntity);
                    return ResponseEntity.ok().build();
                })
                .orElseGet(() -> notFoundResponse("Not a Worldpay gateway account"));
    }

    @PostMapping("/v1/api/accounts/{accountId}/worldpay/check-3ds-flex-config")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Validate Worldpay 3DS flex credentials",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = ValidationResult.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid or missing mandatory fields",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - account not found or not a Worldpay gateway account"),
                    @ApiResponse(responseCode = "503", description = "Service unavailable")
            }
    )
    public ValidationResult validateWorldpay3dsCredentials(@Parameter(example = "1", description = "Gateway account ID")
                                                           @PathVariable("accountId") Long gatewayAccountId,
                                                           @Valid @RequestBody(required = false) Worldpay3dsFlexCredentialsRequest worldpay3dsCredentials) {
        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .map(gatewayAccountEntity ->
                        worldpay3dsFlexCredentialsValidationService.validateCredentials(gatewayAccountEntity, Worldpay3dsFlexCredentials.from(worldpay3dsCredentials)))
                .map(ValidationResult::new)
                .orElseThrow(() -> new GatewayAccountNotFoundException(gatewayAccountId));
    }

    @PostMapping("/v1/api/accounts/{accountId}/worldpay/check-credentials")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Validate Worldpay credentials",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = ValidationResult.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid or missing mandatory fields",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - account not found or not a Worldpay gateway account"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ValidationResult validateWorldpayCredentials(@PathVariable("accountId") Long gatewayAccountId,
                                                        @Valid @RequestBody(required = false) WorldpayValidatableCredentials worldpayValidatableCredentials) {
        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .map(gatewayAccountEntity -> worldpayCredentialsValidationService.validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials))
                .map(ValidationResult::new)
                .orElseThrow(() -> new GatewayAccountNotFoundException(gatewayAccountId));
    }

    @PostMapping("/v1/api/accounts/{accountId}/credentials")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create credentials for a gateway account",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = GatewayAccountCredentialsWithInternalId.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request - Invalid or missing mandatory fields",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - account not found")
            }
    )
    public GatewayAccountCredentialsWithInternalId createGatewayAccountCredentials(@Parameter(example = "1", description = "Gateway account ID")
                                                                                   @PathVariable("accountId") Long gatewayAccountId,
                                                                                   @NotNull @RequestBody(required = false) GatewayAccountCredentialsRequest gatewayAccountCredentialsRequest) {
        gatewayAccountCredentialsRequestValidator.validateCreate(gatewayAccountCredentialsRequest);

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .map(gatewayAccount -> {
                    Map<String, String> credentials = gatewayAccountCredentialsRequest.credentials() == null ? Map.of() : gatewayAccountCredentialsRequest.credentials();
                    return gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccount, gatewayAccountCredentialsRequest.paymentProvider(), credentials);
                })
                .map(GatewayAccountCredentialsWithInternalId::new)
                .orElseThrow(() -> new GatewayAccountNotFoundException(gatewayAccountId));
    }

    @PatchMapping("/v1/api/accounts/{accountId}/credentials/{credentialsId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update a gateway account credential",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(example = "[" +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"state\"," +
                    "        \"value\": \"VERIFIED_WITH_LIVE_PAYMENT\"" +
                    "    }" +
                    "]"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = GatewayAccountCredentialsWithInternalId.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request - Invalid or missing mandatory fields",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - account or credential not found")
            }
    )
    public GatewayAccountCredentialsWithInternalId updateGatewayAccountCredentials(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathVariable("accountId") Long gatewayAccountId,
            @Parameter(example = "1", description = "Credential ID")
            @PathVariable("credentialsId") Long credentialsId,
            @RequestBody(required = false) JsonNode payload) {

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .map(gatewayAccountEntity -> gatewayAccountEntity.getGatewayAccountCredentials()
                        .stream()
                        .filter(c -> c.getId().equals(credentialsId))
                        .findFirst()
                        .map(gatewayAccountCredentialsEntity -> {
                            gatewayAccountCredentialsRequestValidator.validatePatch(payload,
                                    gatewayAccountCredentialsEntity.getPaymentProvider(),
                                    gatewayAccountCredentialsEntity.getCredentialsObject());
                            List<JsonPatchRequest> updateRequests = StreamSupport.stream(payload.spliterator(), false)
                                    .map(JsonPatchRequest::from)
                                    .collect(Collectors.toList());
                            return gatewayAccountCredentialsService.updateGatewayAccountCredentials(gatewayAccountCredentialsEntity, updateRequests);
                        })
                        .orElseThrow(() -> new GatewayAccountCredentialsNotFoundException(credentialsId)))
                .map(GatewayAccountCredentialsWithInternalId::new)
                .orElseThrow(() -> new GatewayAccountNotFoundException(gatewayAccountId));
    }

    @PostMapping("/v1/api/service/{serviceId}/account/{accountType}/credentials")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create credentials for a gateway account by service external ID and account type (test|live)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = GatewayAccountCredentials.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request - Invalid or missing mandatory fields",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - account not found")
            }
    )
    public GatewayAccountCredentials createGatewayAccountCredentialsByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service external ID") @PathVariable("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @NotNull @RequestBody(required = false) GatewayAccountCredentialsRequest gatewayAccountCredentialsRequest) {
        gatewayAccountCredentialsRequestValidator.validateCreate(gatewayAccountCredentialsRequest);

        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(gatewayAccount -> {
                    Map<String, String> credentials = gatewayAccountCredentialsRequest.credentials() == null ? Map.of() : gatewayAccountCredentialsRequest.credentials();
                    return gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccount, gatewayAccountCredentialsRequest.paymentProvider(), credentials);
                })
                .map(GatewayAccountCredentials::new)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));
    }

    @PutMapping("/v1/api/service/{serviceId}/account/{accountType}/3ds-flex-credentials")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create or update 3DS flex credentials (worldpay accounts)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid or missing mandatory fields",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - account not found or not a Worldpay gateway account")
            }
    )
    public ResponseEntity<?> createOrUpdateWorldpay3dsCredentialsByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID")
            @PathVariable("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type")
            @PathVariable("accountType") GatewayAccountType accountType,
            @Valid @RequestBody(required = false) Worldpay3dsFlexCredentialsRequest worldpay3dsCredentials) {
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .or(() -> {
                    throw new GatewayAccountNotFoundException(serviceId, accountType);
                })
                .filter(gatewayAccountEntity ->
                        (gatewayAccountEntity.getGatewayName().equals(PaymentGatewayName.WORLDPAY.getName()) || gatewayAccountEntity.hasPendingWorldpayCredential()))
                .map(gatewayAccountEntity -> {
                    worldpay3dsFlexCredentialsService.setGatewayAccountWorldpay3dsFlexCredentials(worldpay3dsCredentials,
                            gatewayAccountEntity);
                    return ResponseEntity.ok().build();
                })
                .orElseGet(() -> notFoundResponse("Not a Worldpay gateway account"));
    }

    @PostMapping("/v1/api/service/{serviceId}/account/{accountType}/worldpay/check-3ds-flex-config")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Validate Worldpay 3DS flex credentials",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = ValidationResult.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid or missing mandatory fields",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - account not found or not a Worldpay gateway account"),
                    @ApiResponse(responseCode = "503", description = "Service unavailable")
            }
    )
    public ValidationResult validateWorldpay3dsCredentialsByServiceIdAndType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service external ID") @PathVariable("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @Valid @RequestBody(required = false) Worldpay3dsFlexCredentialsRequest worldpay3dsCredentials) {
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(gatewayAccountEntity ->
                        worldpay3dsFlexCredentialsValidationService.validateCredentials(gatewayAccountEntity, Worldpay3dsFlexCredentials.from(worldpay3dsCredentials)))
                .map(ValidationResult::new)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));
    }

    @PatchMapping("/v1/api/service/{serviceId}/account/{accountType}/credentials/{credentialsId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update a gateway account credential by service ID and account Type",
            description = "A generic endpoint that allows the patching of credentials, " +
                    "credentials/worldpay/one_off_customer_initiated " +
                    "credentials/worldpay/recurring_customer_initiated, " +
                    "credentials/worldpay/recurring_merchant_initiated, " +
                    "last_updated_by_user_external_id, state, gateway_merchant_id, credentials/gateway_merchant_id, " +
                    "using a JSON Patch-esque message body.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(example = "[" +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"credentials/worldpay/one_off_customer_initiated\"," +
                    "        \"value\": \"VERIFIED_WITH_LIVE_PAYMENT\"" +
                    "    }" +
                    "]"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = GatewayAccountCredentials.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request - Invalid or missing mandatory fields",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - account or credential not found")
            }
    )
    public GatewayAccountCredentials updateGatewayAccountCredentialsByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service external ID") @PathVariable("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @Parameter(example = "787460d16d4a4d14b4c94787b8f427db", description = "Credential external ID") @PathVariable("credentialsId") String credentialsId,
            @RequestBody(required = false) JsonNode payload) {

        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .or(() -> {
                    throw new GatewayAccountNotFoundException(serviceId, accountType);
                })
                .map(GatewayAccountEntity::getGatewayAccountCredentials)
                .flatMap(gatewayAccountCredentials -> gatewayAccountCredentials.stream()
                        .filter(c -> c.getExternalId().equals(credentialsId))
                        .findFirst())
                .or(() -> {
                    throw GatewayAccountCredentialsNotFoundException.forExternalId(credentialsId);
                })
                .map(gatewayAccountCredentialsEntity -> {
                    gatewayAccountCredentialsRequestValidator.validatePatch(payload,
                            gatewayAccountCredentialsEntity.getPaymentProvider(),
                            gatewayAccountCredentialsEntity.getCredentialsObject());

                    List<JsonPatchRequest> updateRequests = StreamSupport.stream(payload.spliterator(), false)
                            .map(JsonPatchRequest::from)
                            .collect(Collectors.toList());
                    return gatewayAccountCredentialsService.updateGatewayAccountCredentials(gatewayAccountCredentialsEntity, updateRequests);
                })
                .map(GatewayAccountCredentials::new)
                .orElseThrow(IllegalStateException::new);
    }

    @PostMapping("/v1/api/service/{serviceId}/account/{accountType}/worldpay/check-credentials")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Validate Worldpay credentials by service ID and account type",
            responses = {
                    @ApiResponse(
                            responseCode = "200", 
                            description = "The response body will contain either 'valid' or 'invalid' to indicate if the supplied credentials are valid or not.",
                            content = @Content(schema = @Schema(implementation = ValidationResult.class))),
                    @ApiResponse(
                            responseCode = "422", 
                            description = "Unprocessable Entity - Invalid or missing mandatory fields",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(
                            responseCode = "404", 
                            description = "Not found - account not found, not a Worldpay gateway account or not a gateway account switching to Worldpay"),
                    @ApiResponse(
                            responseCode = "500", 
                            description = "Indicates an internal server error in connector, or an upstream Worldpay 5xx error.")
            }
    )
    public ValidationResult validateWorldpayCredentialsByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service external ID") @PathVariable("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @Valid @RequestBody(required = false) WorldpayValidatableCredentials worldpayValidatableCredentials) {
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .or(() -> {
                    throw new GatewayAccountNotFoundException(serviceId, accountType);
                })
                .filter(gatewayAccountEntity -> gatewayAccountEntity.isWorldpayGatewayAccount() || gatewayAccountEntity.hasPendingWorldpayCredential())
                .or(() -> {
                    throw GatewayAccountNotFoundException.forNonWorldpayAccount(serviceId, accountType);
                })
                .map(gatewayAccountEntity -> worldpayCredentialsValidationService.validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials))
                .map(ValidationResult::new)
                .orElseThrow(() -> new IllegalStateException("Internal server error"));
    }


    public static final class ValidationResult {
        @Schema(example = "valid", description = "valid/invalid result for Worldpay flex credentials")
        private final String result;

        private ValidationResult(boolean isValid) {
            this.result = isValid ? "valid" : "invalid";
        }

        public String getResult() {
            return result;
        }
    }
}
