package uk.gov.pay.connector.agreement.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.model.AgreementCancelRequest;
import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;

import jakarta.inject.Inject;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.pay.connector.web.MediaTypes;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import uk.gov.pay.connector.common.exception.WebApplicationException;


@Tag(name = "Agreements")
@RestController
public class AgreementsApiResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgreementsApiResource.class);

    private final AgreementService agreementService;

    @Inject
    public AgreementsApiResource(AgreementService agreementService) {
        this.agreementService = agreementService;
    }

    @PostMapping("/v1/api/accounts/{accountId}/agreements")
    @MediaTypes(produces = "application/json", consumes = "application/json")
    @Operation(
            summary = "Create an agreement",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AgreementResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Missing required fields", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public ResponseEntity<?> createAgreement(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathVariable("accountId") Long accountId,
            @Valid @RequestBody(required = false) AgreementCreateRequest agreementCreateRequest
    ) {
        LOGGER.info("Creating new agreement for gateway account ID {}", accountId);
        AgreementResponse agreementResponse = agreementService.createByGatewayAccountId(agreementCreateRequest, accountId).orElseThrow(() -> new WebApplicationException(HttpStatus.NOT_FOUND));
        return ResponseEntity.status(HttpStatus.CREATED).body(agreementResponse);
    }

    @PostMapping("/v1/api/service/{serviceId}/account/{accountType}/agreements")
    @MediaTypes(produces = "application/json", consumes = "application/json")
    @Operation(
            summary = "Create an agreement for a service ID and account type (test|live)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AgreementResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Missing required fields", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public ResponseEntity<?> createAgreement(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathVariable("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @Valid @RequestBody(required = false) AgreementCreateRequest agreementCreateRequest
    ) {
        LOGGER.info("Creating new agreement for service ID {}, account type {}", serviceId, accountType);
        AgreementResponse agreementResponse = agreementService.createByServiceIdAndAccountType(agreementCreateRequest, serviceId, accountType).orElseThrow(() -> new WebApplicationException(HttpStatus.NOT_FOUND));
        return ResponseEntity.status(HttpStatus.CREATED).body(agreementResponse);
    }

    @PostMapping("/v1/api/accounts/{accountId}/agreements/{agreementId}/cancel")
    @MediaTypes(consumes = "application/json")
    public ResponseEntity<?> cancelAgreement(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathVariable("accountId") Long accountId,
            @PathVariable("agreementId") String agreementId,
            @Valid @RequestBody(required = false) AgreementCancelRequest agreementCancelRequest
    ) {
        agreementService.cancelByGatewayAccountId(agreementId, accountId, agreementCancelRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/api/service/{serviceId}/account/{accountType}/agreements/{agreementId}/cancel")
    @MediaTypes(consumes = "application/json")
    public ResponseEntity<?> cancelAgreement(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathVariable("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @PathVariable("agreementId") String agreementId,
            @Valid @RequestBody(required = false) AgreementCancelRequest agreementCancelRequest
    ) {
        agreementService.cancelByServiceIdAndAccountType(agreementId, serviceId, accountType, agreementCancelRequest);
        return ResponseEntity.noContent().build();
    }
    
    
}
