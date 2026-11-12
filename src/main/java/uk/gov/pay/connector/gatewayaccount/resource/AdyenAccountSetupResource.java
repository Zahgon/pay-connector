package uk.gov.pay.connector.gatewayaccount.resource;

import jakarta.inject.Inject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.AdyenSetupPatchRequest;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupResponse;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupUpdateRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.AdyenAccountSetupService;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.List;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.pay.connector.web.MediaTypes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import uk.gov.pay.connector.common.exception.WebApplicationException;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Gateway accounts")
@RestController
public class AdyenAccountSetupResource {
    private final GatewayAccountService gatewayAccountService;
    private final AdyenAccountSetupService adyenAccountSetupService;

    @Inject
    public AdyenAccountSetupResource(GatewayAccountService gatewayAccountService, AdyenAccountSetupService adyenAccountSetupService) {
        this.gatewayAccountService = gatewayAccountService;
        this.adyenAccountSetupService = adyenAccountSetupService;
    }

    @GetMapping("/v1/api/service/{serviceId}/account/{accountType}/adyen-setup/{credentialExternalId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Retrieve Adyen account setup tasks for a given service ID, account type and credential ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = AdyenAccountSetupResponse.class), examples = @ExampleObject(
                                    value = """
                                            {
                                              "service_id": "46eb1b601348499196c99de90482ee68",
                                              "credential_external_id": "46eb1b601348499196c99de90482ee68",
                                              "gateway_account_id": 123,
                                              "tasks": {
                                                       "organisation_details": {
                                                         "status": "COMPLETED"
                                                       },
                                                       "legal_terms": {
                                                         "status": "COMPLETED"
                                                       },
                                                       "bank_details": {
                                                         "status": "COMPLETED"
                                                       },
                                                       "responsible_person": {
                                                         "status": "COMPLETED"
                                                       },
                                                       "director": {
                                                         "status": "COMPLETED"
                                                       },
                                                       "reason_for_taking_payments": {
                                                         "status": "COMPLETED"
                                                       }
                                                     }
                                            }
                                            """))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public AdyenAccountSetupResponse getAdyenAccountSetup(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathVariable("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Credential External ID") @PathVariable("credentialExternalId") String credentialExternalId) { // pragma: allowlist secret

        var gatewayAccountEntity = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));

        var gatewayAccountCredentialsEntity = validateGatewayAccountCredentialsEntity(credentialExternalId, gatewayAccountEntity);

        return adyenAccountSetupService.buildResponse(serviceId, gatewayAccountEntity.getId(), gatewayAccountCredentialsEntity);
    }

    @PatchMapping("/v1/api/service/{serviceId}/account/{accountType}/adyen-setup/{credentialExternalId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update Adyen account setup tasks for a given service ID, account type and credential ID",
            description = "Support patching following paths: <br>" +
                    "bank_account, responsible_person, vat_number, company_number, director, government_entity_document, organisation_details",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            schema = @Schema(implementation = AdyenSetupPatchRequest.class),
                            examples = @ExampleObject(
                                    value = """
                                            [
                                              {
                                                "op": "replace",
                                                "path": "bank_details",
                                                "value": "COMPLETED"
                                              },
                                              {
                                                "op": "replace",
                                                "path": "responsible_person",
                                                "value": "NOT_STARTED"
                                              }
                                            ]
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
            }
    )
    public ResponseEntity<?> patchAdyenAccountSetup(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathVariable("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Credential External ID") @PathVariable("credentialExternalId") String credentialExternalId, // pragma: allowlist secret
            @Valid @RequestBody(required = false) List<AdyenSetupPatchRequest> requests) {

        var gatewayAccountEntity = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));
        var gatewayAccountCredentialsEntity = validateGatewayAccountCredentialsEntity(credentialExternalId, gatewayAccountEntity);

        requests.forEach(patchRequest ->  {
            AdyenAccountSetupUpdateRequest updateRequest = AdyenAccountSetupUpdateRequest.from(patchRequest);
            adyenAccountSetupService.update(gatewayAccountEntity, updateRequest, gatewayAccountCredentialsEntity);
        });
        
        return ResponseEntity.ok().build();
    }
    
    private GatewayAccountCredentialsEntity validateGatewayAccountCredentialsEntity(String credentialExternalId, GatewayAccountEntity gatewayAccountEntity) {
        var gatewayAccountCredentialsEntity = gatewayAccountEntity.getGatewayAccountCredentials().stream().filter(credentialsEntity ->
                        credentialsEntity.getExternalId().equals(credentialExternalId)).findFirst()
                .orElseThrow(() -> new WebApplicationException(HttpStatus.NOT_FOUND));

        if (!gatewayAccountCredentialsEntity.getPaymentProvider().equals(ADYEN.getName())) {
            throw new WebApplicationException("Credential is not associated with payment provider Adyen", HttpStatus.BAD_REQUEST);
        }

        return gatewayAccountCredentialsEntity;
    }
}
