package uk.gov.pay.connector.paymentprocessor.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationErrorException;
import uk.gov.pay.connector.charge.exception.motoapi.AuthorisationRejectedException;
import uk.gov.pay.connector.charge.service.ChargeCancelService;
import uk.gov.pay.connector.charge.service.ChargeEligibleForCaptureService;
import uk.gov.pay.connector.charge.service.DelayedCaptureService;
import uk.gov.pay.connector.charge.service.motoapi.MotoApiCardNumberValidationService;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.paymentprocessor.model.MotoApiAuthoriseRequest;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;
import uk.gov.pay.connector.paymentprocessor.service.CardAuthoriseService;
import uk.gov.pay.connector.token.TokenService;
import uk.gov.pay.connector.token.model.domain.TokenEntity;
import uk.gov.pay.connector.util.MDCUtils;
import uk.gov.pay.connector.util.ResponseUtil;
import uk.gov.pay.connector.wallets.WalletService;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.AdyenGooglePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;

import java.util.Map;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.pay.connector.web.MediaTypes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.validation.annotation.Validated;

import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.gatewayErrorResponse;
import static uk.gov.service.payments.logging.LoggingKeys.SECURE_TOKEN;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Charge operations")
@RestController
@Validated
public class CardResource {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CardAuthoriseService cardAuthoriseService;
    private final Card3dsResponseAuthService card3dsResponseAuthService;
    private final ChargeEligibleForCaptureService chargeEligibleForCaptureService;
    private final DelayedCaptureService delayedCaptureService;
    private final ChargeCancelService chargeCancelService;
    private final WalletService walletService;
    private final TokenService tokenService;
    private final MotoApiCardNumberValidationService motoApiCardNumberValidationService;
    private final GatewayAccountService gatewayAccountService;

    @Inject
    public CardResource(CardAuthoriseService cardAuthoriseService, Card3dsResponseAuthService card3dsResponseAuthService,
                        ChargeEligibleForCaptureService chargeEligibleForCaptureService, DelayedCaptureService delayedCaptureService,
                        ChargeCancelService chargeCancelService, WalletService walletService,
                        TokenService tokenService, MotoApiCardNumberValidationService motoApiCardNumberValidationService,
                        GatewayAccountService gatewayAccountService) {
        this.cardAuthoriseService = cardAuthoriseService;
        this.card3dsResponseAuthService = card3dsResponseAuthService;
        this.chargeEligibleForCaptureService = chargeEligibleForCaptureService;
        this.delayedCaptureService = delayedCaptureService;
        this.chargeCancelService = chargeCancelService;
        this.walletService = walletService;
        this.tokenService = tokenService;
        this.motoApiCardNumberValidationService = motoApiCardNumberValidationService;
        this.gatewayAccountService = gatewayAccountService;
    }

    @PostMapping("/v1/frontend/charges/{chargeId}/wallets/apple")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Authorise Apple Pay payment",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "202", description = "Accepted - payment has been submitted for authorisation and awaiting response from payment service provider"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid payload or the payment has been declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Gateway error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid payload or missing mandatory attributes",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error"),
            }
    )
    public ResponseEntity<?> authoriseApplePay(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                      @PathVariable("chargeId") String chargeId,
                                      @NotNull @Valid @RequestBody(required = false) ApplePayAuthRequest applePayAuthRequest) {
        return walletService.authorise(chargeId, applePayAuthRequest);
    }


    @PostMapping("/v1/frontend/charges/{chargeId}/wallets/google")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Authorise Google Pay payment",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "202", description = "Accepted - payment has been submitted for authorisation and awaiting response from payment service provider"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid payload or the payment has been declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Gateway error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid payload or missing mandatory attributes",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error"),
            }
    )
    public ResponseEntity<?> authoriseChargeGooglePay(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                             @PathVariable("chargeId") String chargeId,
                                             @NotNull @Valid @RequestBody(required = false) GooglePayAuthRequest googlePayAuthRequest) {
        logger.info("Received wallet payment info \n{} \nfor charge with id {}", googlePayAuthRequest.getPaymentInfo().toString(), chargeId);
        return walletService.authorise(chargeId, googlePayAuthRequest);
    }

    @PostMapping("/v1/frontend/charges/{chargeId}/wallets/google/adyen")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Authorise Adyen Google Pay payment",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "202", description = "Accepted - payment has been submitted for authorisation and awaiting response from payment service provider"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid payload or the payment has been declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Gateway error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid payload or missing mandatory attributes",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error"),
            }
    )
    public ResponseEntity<?> authoriseChargeAdyenGooglePay(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                             @PathVariable("chargeId") String chargeId,
                                             @NotNull @Valid @RequestBody(required = false) AdyenGooglePayAuthRequest adyenGooglePayAuthRequest) {
        logger.info("Received wallet payment info \n{} \nfor charge with id {}", adyenGooglePayAuthRequest.getPaymentInfo().toString(), chargeId);
        return walletService.authorise(chargeId, adyenGooglePayAuthRequest);
    }

    @PostMapping("/v1/frontend/charges/{chargeId}/cards")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Authorise charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "202", description = "Accepted - payment has been submitted for authorisation and awaiting response from payment service provider"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid payload or the payment has been declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Gateway error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid payload or missing mandatory attributes",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error - For gateway errors or anything else not handled"),
            }
    )
    public ResponseEntity<?> authoriseCharge(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                    @PathVariable("chargeId") String chargeId,
                                    @Valid @RequestBody(required = false) AuthCardDetails authCardDetails) {
        AuthorisationResponse response = cardAuthoriseService.doAuthoriseWeb(chargeId, authCardDetails);

        return response.getGatewayError().map(this::handleError)
                .orElseGet(() -> response.getAuthoriseStatus().map(this::handleAuthResponse)
                        .orElseGet(() -> ResponseUtil.serviceErrorResponse("InterpretedStatus not found for Gateway response")));
    }

    private ResponseEntity<?> handleAuthResponse(AuthoriseStatus authoriseStatus) {
        switch (authoriseStatus) {
            case REJECTED:
                return badRequestResponse("This transaction was declined.");
            case ERROR:
            case EXCEPTION:
                return gatewayErrorResponse("There was an error authorising the transaction.");
            default:
                return ResponseUtil.successResponseWithEntity(Map.of("status", authoriseStatus.getMappedChargeStatus().toString()));
        }
    }

    @PostMapping("/v1/frontend/charges/{chargeId}/3ds")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Authorise 3DS charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "202", description = "Accepted - payment has been submitted for 3ds authorisation and awaiting response from payment service provider"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid payload or the payment has been declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Gateway error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error - For gateway errors or anything else not handled"),
            }
    )
    public ResponseEntity<?> authorise3dsCharge(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                       @PathVariable("chargeId") String chargeId, @RequestBody(required = false) Auth3dsResult auth3DsResult) {
        Gateway3DSAuthorisationResponse response = card3dsResponseAuthService.process3DSecureAuthorisation(chargeId, auth3DsResult);

        if (response.isSuccessful()) {
            return ResponseUtil.successResponseWithEntity(
                    Map.of(
                            "status", response.getMappedChargeStatus().toString()
                    ));
        }
        if (response.isException()) {
            return gatewayErrorResponse("There was an error when attempting to authorise the transaction.");
        }
        return ResponseUtil.badRequestResponseWithEntity(
                Map.of(
                        "status", response.getMappedChargeStatus().toString()
                )
        );
    }

    @PostMapping("/v1/frontend/charges/{chargeId}/capture")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Mark charge as eligible for capture",
            description = "Marks charge as eligible (or ready - for delayed capture) for capture and also adds charge to capture queue (if not delayed capture).",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request - if charge is not in correct state"),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> captureCharge(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                  @PathVariable("chargeId") String chargeId) {
        chargeEligibleForCaptureService.markChargeAsEligibleForCapture(chargeId);
        return ResponseUtil.noContentResponse();
    }

    @PostMapping("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}/capture")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Mark delayed capture charge as eligible for capture and adds charge to capture queue",
            description = "This endpoint should be called to capture a delayed capture charge. The charge needs to have been previously marked as AWAITING CAPTURE REQUEST for this call to succeed. " +
                    "When a charge is in any of the states CAPTURED, CAPTURE APPROVED, CAPTURE APPROVED RETRY, CAPTURE READY, CAPTURE SUBMITTED then nothing happens and the response will be a 204. " +
                    "When a charge is in a status that cannot transition (eg. none of the above) then 409 response is returned. ",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "409", description = "Conflict - if charge is not in correct state"),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> markChargeAsCaptureApprovedByServiceId(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathVariable("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @Parameter(example = "spmh0fb7rbi1lebv1j3f7hc3m9", description = "Charge external ID") @PathVariable("chargeId") String chargeId
    ) {
        delayedCaptureService.markDelayedCaptureChargeAsCaptureApproved(chargeId, serviceId, accountType);
        return ResponseUtil.noContentResponse();
    }

    @PostMapping("/v1/api/accounts/{accountId}/charges/{chargeId}/capture")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Mark delayed capture charge as eligible for capture and adds charge to capture queue",
            description = "This endpoint should be called to capture a delayed capture charge. The charge needs to have been previously marked as AWAITING CAPTURE REQUEST for this call to succeed. " +
                    "When a charge is in any of the states CAPTURED, CAPTURE APPROVED, CAPTURE APPROVED RETRY, CAPTURE READY, CAPTURE SUBMITTED then nothing happens and the response will be a 204. " +
                    "When a charge is in a status that cannot transition (eg. none of the above) then 409 response is returned. ",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "409", description = "Conflict - if charge is not in correct state"),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> markChargeAsCaptureApproved(@PathVariable("accountId") Long accountId,
                                                @PathVariable("chargeId") String chargeId,
                                                HttpServletRequest request) {
        delayedCaptureService.markDelayedCaptureChargeAsCaptureApproved(chargeId, accountId);
        return ResponseUtil.noContentResponse();
    }

    @PostMapping("/v1/api/accounts/{accountId}/charges/{chargeId}/cancel")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Cancel charge",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Accepted - operation already in progress"),
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request - charge is not in correct state",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found")
            }
    )
    public ResponseEntity<?> cancelCharge(@Parameter(example = "1", description = "Gateway account ID")
                                 @PathVariable("accountId") Long accountId,
                                 @Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                 @PathVariable("chargeId") String chargeId) {
        return chargeCancelService.doSystemCancel(chargeId, accountId)
                .map(chargeEntity -> ResponseEntity.noContent().build())
                .orElseGet(() -> ResponseUtil.responseWithChargeNotFound(chargeId));
    }

    @PostMapping("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}/cancel")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Cancel charge",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Accepted - operation already in progress"),
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request - charge is not in correct state",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found")
            }
    )
    public ResponseEntity<?> cancelChargeByServiceIdAndAccountType(@Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") // pragma: allowlist secret
                                                          @PathVariable("serviceId") String serviceId,
                                                          @Parameter(example = "test", description = "Account type")
                                                          @PathVariable("accountType") GatewayAccountType accountType,
                                                          @Parameter(example = "spmh0fb7rbi1lebv1j3f7hc3m9", description = "Charge external ID")
                                                          @PathVariable("chargeId") String chargeId) {
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .flatMap(account -> chargeCancelService.doSystemCancel(chargeId, account.getId())
                        .map(chargeEntity -> ResponseEntity.noContent().build())
                ).orElseGet(() -> ResponseUtil.responseWithChargeNotFound(chargeId));
    }

    @PostMapping("/v1/api/charges/authorise")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Authorise MOTO (api) payment",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid one time token or one_time_token has already been used",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "402", description = "Authorisation declined",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Invalid payload or missing mandatory attributes",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Authorisation error"),
            }
    )
    public ResponseEntity<?> authorise(@Valid @NotNull @RequestBody(required = false) MotoApiAuthoriseRequest motoApiAuthoriseRequest) {
        // Fields are removed from the MDC when the API responds in LoggingMDCResponseFilter 
        MDC.put(SECURE_TOKEN, motoApiAuthoriseRequest.getOneTimeToken());
        motoApiAuthoriseRequest.validate();

        TokenEntity tokenEntity = tokenService.validateAndMarkTokenAsUsedForMotoApi(motoApiAuthoriseRequest.getOneTimeToken());
        MDCUtils.addChargeAndGatewayAccountDetailsToMDC(tokenEntity.getChargeEntity());
        CardInformation cardInformation = motoApiCardNumberValidationService.validateCardNumber(tokenEntity.getChargeEntity(), motoApiAuthoriseRequest.getCardNumber());

        AuthorisationResponse response = cardAuthoriseService.doAuthoriseMotoApi(tokenEntity.getChargeEntity(), cardInformation, motoApiAuthoriseRequest);
        return handleAuthResponseForMotoApi(response);
    }

    @PostMapping("/v1/frontend/charges/{chargeId}/cancel")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Cancel charge (action by user)",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Accepted - operation already in progress"),
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request - charge is not in correct state",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found")
            }
    )
    public ResponseEntity<?> userCancelCharge(@Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID")
                                     @PathVariable("chargeId") String chargeId) {
        return chargeCancelService.doUserCancel(chargeId)
                .map(chargeEntity -> ResponseEntity.noContent().build())
                .orElseGet(() -> ResponseUtil.responseWithChargeNotFound(chargeId));
    }

    private ResponseEntity<?> handleError(GatewayError error) {
        switch (error.getErrorType()) {
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
            case GATEWAY_ERROR:
                return gatewayErrorResponse(error.getMessage());
            default:
                return badRequestResponse(error.getMessage());
        }
    }

    private ResponseEntity<?> handleAuthResponseForMotoApi(AuthorisationResponse response) {
        if (response.getGatewayError().isPresent()) {
            throw new AuthorisationErrorException();
        } else if (response.getAuthoriseStatus().isPresent()) {
            switch (response.getAuthoriseStatus().get().getMappedChargeStatus()) {
                case AUTHORISATION_SUCCESS:
                    return ResponseEntity.noContent().build();
                case AUTHORISATION_REJECTED:
                case AUTHORISATION_CANCELLED:
                    throw new AuthorisationRejectedException();
                case AUTHORISATION_ERROR:
                case AUTHORISATION_SUBMITTED:
                    throw new AuthorisationErrorException();
                default:
                    return ResponseUtil.serviceErrorResponse("Authorisation status unexpected");
            }
        } else {
            return ResponseUtil.serviceErrorResponse("InterpretedStatus not found for Gateway response");
        }
    }
}
