package uk.gov.pay.connector.charge.resource;

import com.google.common.collect.ImmutableSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.Worldpay3dsFlexJwtCredentialsException;
import uk.gov.pay.connector.charge.model.FrontendChargeResponse;
import uk.gov.pay.connector.charge.model.NewChargeStatusRequest;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.common.service.PatchRequestBuilder;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.pay.connector.web.MediaTypes;
import uk.gov.pay.connector.web.BaseUri;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.validation.annotation.Validated;

import static uk.gov.pay.connector.charge.resource.ChargesApiResource.EMAIL_KEY;
import static uk.gov.pay.connector.common.service.PatchRequestBuilder.aPatchRequestBuilder;
import static uk.gov.pay.connector.common.validator.ApiValidators.validateChargePatchParams;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Charges - Frontend")
@RestController
@Validated
public class ChargesFrontendResource {

    private static final Logger logger = LoggerFactory.getLogger(ChargesFrontendResource.class);
    private final ChargeDao chargeDao;
    private final ChargeService chargeService;
    private final CardTypeDao cardTypeDao;
    private final Worldpay3dsFlexJwtService worldpay3dsFlexJwtService;
    private final AgreementService agreementService;
    private final ExternalTransactionStateFactory externalTransactionStateFactory;

    @Inject
    public ChargesFrontendResource(ChargeDao chargeDao, ChargeService chargeService, CardTypeDao cardTypeDao,
                                   Worldpay3dsFlexJwtService worldpay3dsFlexJwtService,
                                   AgreementService agreementService,
                                   ExternalTransactionStateFactory externalTransactionStateFactory) {
        this.chargeDao = chargeDao;
        this.chargeService = chargeService;
        this.cardTypeDao = cardTypeDao;
        this.worldpay3dsFlexJwtService = worldpay3dsFlexJwtService;
        this.agreementService = agreementService;
        this.externalTransactionStateFactory = externalTransactionStateFactory;
    }

    @GetMapping("/v1/frontend/charges/{chargeId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Find a charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = FrontendChargeResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found")
            }
    )
    public ResponseEntity<?> getCharge(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                              @PathVariable("chargeId") String chargeId, HttpServletRequest request) {

        return chargeDao.findByExternalId(chargeId)
                .<ResponseEntity<?>>map(charge -> ResponseEntity.ok(chargeService.buildChargeResponse(BaseUri.from(request), charge)))
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @GetMapping("/v1/frontend/charges/{chargeId}/worldpay/3ds-flex/ddc")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get Worldpay 3DS Flex DDC JWT",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"jwt\": \"token\"" +
                                    "}"))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "409", description = "Conflict - Cannot generate Worldpay 3ds Flex JWT because credentials are unavailable or not a Worldpay account"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> getWorldpay3dsFlexDdcJwt(@Schema(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                             @PathVariable("chargeId") String chargeId) {

        ChargeEntity chargeEntity = chargeService.findChargeByExternalId(chargeId);
        GatewayAccount gatewayAccount = GatewayAccount.valueOf(chargeEntity.getGatewayAccount());
        var worldpay3dsFlexCredentials = chargeEntity.getGatewayAccount().getWorldpay3dsFlexCredentials()
                .orElseThrow(() -> new Worldpay3dsFlexJwtCredentialsException(gatewayAccount.getId()));
        String token = worldpay3dsFlexJwtService.generateDdcToken(gatewayAccount, worldpay3dsFlexCredentials,
                chargeEntity.getCreatedDate(), chargeEntity.getPaymentProvider());

        return ResponseEntity.ok(Map.of("jwt", token));
    }

    @PatchMapping("/v1/frontend/charges/{chargeId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update charge (email field only)",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "    \"op\": \"replace\"," +
                    "    \"path\": \"email\"," +
                    "    \"value\": \"newemail@example.org\"" +
                    "}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = FrontendChargeResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request"),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> patchCharge(@Schema(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                @PathVariable("chargeId") String chargeId, @RequestBody(required = false) Map<String, String> chargePatchMap, HttpServletRequest request) {
        PatchRequestBuilder.PatchRequest chargePatchRequest;

        try {
            chargePatchRequest = aPatchRequestBuilder(chargePatchMap)
                    .withValidOps(Collections.singletonList("replace"))
                    .withValidPaths(ImmutableSet.of(EMAIL_KEY))
                    .build();
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse("Bad patch parameters" + chargePatchMap.toString());
        }

        if (!validateChargePatchParams(chargePatchRequest)) {
            return badRequestResponse("Invalid patch parameters" + chargePatchMap.toString());
        }

        return chargeService.updateCharge(chargeId, chargePatchRequest)
                .<ResponseEntity<?>>map(chargeEntity -> ResponseEntity.ok(chargeService.buildChargeResponse(BaseUri.from(request), chargeEntity)))
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @PutMapping("/v1/frontend/charges/{chargeId}/status")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update status of a charge",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(example = "{" +
                    "    \"new_status\": \"ENTERING CARD DETAILS\"" +
                    "}"))),
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request - charge cannot be updated to new status"),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - invalid new status"),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> updateChargeStatus(
            @Parameter(example = "spmh0fb7rbi1lebv1j3f7hc3m9", description = "Charge external ID")
            @PathVariable("chargeId") String chargeId,
            @Valid @NotNull @RequestBody(required = false) NewChargeStatusRequest newChargeStatusRequest) {
        ChargeStatus newChargeStatus;

        try {
            newChargeStatus = ChargeStatus.fromString(newChargeStatusRequest.getNewStatus());
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
            return badRequestResponse(e.getMessage());
        }

        return chargeService.updateFromInitialStatus(chargeId, newChargeStatus)
                .<ResponseEntity<?>>map(chargeEntity -> ResponseEntity.noContent().build())
                .orElseGet(() -> getInvalidStatusResponse(chargeId, newChargeStatus));
    }

    private ResponseEntity<?> getInvalidStatusResponse(String chargeId, ChargeStatus newChargeStatus) {
        return badRequestResponse("charge with id: " + chargeId +
                " cannot be updated to the new status: " + newChargeStatus.getValue());
    }

    private Optional<String> findCardBrandLabel(String cardBrand) {
        if (cardBrand == null) {
            return Optional.empty();
        }

        return cardTypeDao.findByBrand(cardBrand)
                .stream()
                .findFirst()
                .map(CardTypeEntity::getLabel);
    }

    private Optional<AgreementResponse> findAgreement(String agreementExternalId, long gatewayAccountId) {
        return agreementService.findByExternalId(agreementExternalId, gatewayAccountId);
    }
}
