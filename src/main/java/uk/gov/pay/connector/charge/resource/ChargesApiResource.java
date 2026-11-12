package uk.gov.pay.connector.charge.resource;

import org.springframework.transaction.annotation.Transactional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.Length;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.exception.InvalidAttributeValueException;
import uk.gov.pay.connector.charge.exception.MissingMandatoryAttributeException;
import uk.gov.pay.connector.charge.exception.TelephonePaymentNotificationsNotAllowedException;
import uk.gov.pay.connector.charge.exception.UnexpectedAttributeException;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.validation.ReturnUrlValidator;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import uk.gov.pay.connector.web.MediaTypes;
import uk.gov.pay.connector.web.BaseUri;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;

import static uk.gov.pay.connector.util.ResponseUtil.buildErrorResponse;
import static uk.gov.pay.connector.util.ResponseUtil.noContentResponse;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithGatewayTransactionNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.successResponseWithEntity;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@Validated
public class ChargesApiResource {
    public static final String EMAIL_KEY = "email";
    public static final String AMOUNT_KEY = "amount";
    public static final String LANGUAGE_KEY = "language";
    public static final String DELAYED_CAPTURE_KEY = "delayed_capture";
    private static final String DESCRIPTION_KEY = "description";
    private static final String REFERENCE_KEY = "reference";
    public static final Map<String, Integer> MAXIMUM_FIELDS_SIZE = Map.of(
            DESCRIPTION_KEY, 255,
            REFERENCE_KEY, 255,
            EMAIL_KEY, 254
    );
    private static final String ACCOUNT_ID = "accountId";
    private static final String RETURN_URL = "return_url";
    private static final EnumSet<AuthorisationMode> AUTHORISATION_MODES_INCOMPATIBLE_WITH_RETURN_URL = EnumSet.of(AuthorisationMode.MOTO_API, AuthorisationMode.AGREEMENT);
    private static final Logger logger = LoggerFactory.getLogger(ChargesApiResource.class);
    public static final int MIN_AMOUNT = 1;
    public static final int MAX_AMOUNT = 10_000_000;
    private final ChargeService chargeService;
    private final ChargeExpiryService chargeExpiryService;
    private final GatewayAccountService gatewayAccountService;
    private final UserNotificationService userNotificationService;

    @Inject
    public ChargesApiResource(ChargeService chargeService,
                              ChargeExpiryService chargeExpiryService,
                              GatewayAccountService gatewayAccountService,
                              UserNotificationService userNotificationService) {
        this.chargeService = chargeService;
        this.chargeExpiryService = chargeExpiryService;
        this.gatewayAccountService = gatewayAccountService;
        this.userNotificationService = userNotificationService;
    }

    @GetMapping("/v1/api/accounts/{accountId}/charges/{chargeId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get charge by account ID and charge external ID",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public ResponseEntity<?> getChargeByGatewayAccountId(@Parameter(example = "1", description = "Gateway account ID") @PathVariable(ACCOUNT_ID) Long accountId,
                              @Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID") @PathVariable("chargeId") String chargeId,
                              HttpServletRequest request) {
        return chargeService.findChargeForAccount(chargeId, accountId, BaseUri.from(request))
                .<ResponseEntity<?>>map(chargeResponse -> ResponseEntity.ok(chargeResponse))
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @GetMapping("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get charge by service ID, account type and charge external ID",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public ResponseEntity<?> getChargeByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathVariable("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID") @PathVariable("chargeId") String chargeId,
            HttpServletRequest request) {
        return chargeService.findChargeForServiceIdAndAccountType(chargeId, serviceId, accountType, BaseUri.from(request))
                .<ResponseEntity<?>>map(chargeResponse -> ResponseEntity.ok(chargeResponse))
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @PostMapping("/v1/api/accounts/{accountId}/charges")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create new charge for gateway account",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "403", description = "Gateway account is disabled"),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "422", description = "Missing required fields or invalid values", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @Transactional
    public ResponseEntity<?> createNewCharge(
            @Parameter(example = "1", description = "Gateway account ID") @PathVariable(ACCOUNT_ID) Long accountId,
            @NotNull @Valid @RequestBody(required = false) ChargeCreateRequest chargeRequest,
            HttpServletRequest request,
            @Nullable @Length(min = 1, max = 255, message = "Header [Idempotency-Key] can have a size between 1 and 255") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        logger.info("Creating new charge - {}", chargeRequest.toStringWithoutPersonalIdentifiableInformation());

        AuthorisationMode authorisationMode = chargeRequest.getAuthorisationMode();
        if (authorisationMode == AuthorisationMode.WEB) {
            chargeRequest.getReturnUrl().ifPresentOrElse(returnUrl -> {
                        if (!ReturnUrlValidator.isValid(returnUrl)) {
                            throw new InvalidAttributeValueException(RETURN_URL, "Must be a valid URL format");
                        }
                    },
                    () -> {
                        throw new MissingMandatoryAttributeException(RETURN_URL);
                    });

        } else if (AUTHORISATION_MODES_INCOMPATIBLE_WITH_RETURN_URL.contains(authorisationMode) && chargeRequest.getReturnUrl().isPresent()) {
            throw new UnexpectedAttributeException(RETURN_URL);
        }

        if (idempotencyKey != null && chargeRequest.getAuthorisationMode() == AuthorisationMode.AGREEMENT) {
            Optional<ChargeResponse> maybeExistingChargeResponse = chargeService.checkForChargeCreatedWithIdempotencyKey(
                    chargeRequest, accountId, idempotencyKey, BaseUri.from(request));
            if (maybeExistingChargeResponse.isPresent()) {
                ChargeResponse existingChargeResponse = maybeExistingChargeResponse.get();
                return ResponseEntity.ok(existingChargeResponse);
            }
        }

        return chargeService.create(chargeRequest, accountId, BaseUri.from(request), idempotencyKey)
                .<ResponseEntity<?>>map(response -> {
                    if (authorisationMode == AuthorisationMode.AGREEMENT) {
                        chargeService.markChargeAsEligibleForAuthoriseUserNotPresent(response.getChargeId());
                    }
                    return ResponseEntity.created(response.getLink("self")).body(response);
                })
                .orElseGet(() -> notFoundResponse("Unknown gateway account: " + accountId));
    }

    @PostMapping("/v1/api/service/{serviceId}/account/{accountType}/charges")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create new charge for service ID and account type",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "403", description = "Gateway account is disabled"),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "422", description = "Missing required fields or invalid values", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @Transactional
    public ResponseEntity<?> createNewChargeByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathVariable("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @NotNull @Valid @RequestBody(required = false) ChargeCreateRequest chargeRequest,
            HttpServletRequest request,
            @Nullable @Length(min = 1, max = 255, message = "Header [Idempotency-Key] can have a size between 1 and 255") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        logger.info("Creating new charge - {}", chargeRequest.toStringWithoutPersonalIdentifiableInformation());

        AuthorisationMode authorisationMode = chargeRequest.getAuthorisationMode();
        if (authorisationMode == AuthorisationMode.WEB) {
            chargeRequest.getReturnUrl().ifPresentOrElse(returnUrl -> {
                        if (!ReturnUrlValidator.isValid(returnUrl)) {
                            throw new InvalidAttributeValueException(RETURN_URL, "Must be a valid URL format");
                        }
                    },
                    () -> {
                        throw new MissingMandatoryAttributeException(RETURN_URL);
                    });

        } else if (AUTHORISATION_MODES_INCOMPATIBLE_WITH_RETURN_URL.contains(authorisationMode) && chargeRequest.getReturnUrl().isPresent()) {
            throw new UnexpectedAttributeException(RETURN_URL);
        }

        Long accountId = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(GatewayAccountEntity::getId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));

        if (idempotencyKey != null && chargeRequest.getAuthorisationMode() == AuthorisationMode.AGREEMENT) {
            Optional<ChargeResponse> maybeExistingChargeResponse = chargeService.checkForChargeCreatedWithIdempotencyKey(
                    chargeRequest, accountId, idempotencyKey, BaseUri.from(request));
            if (maybeExistingChargeResponse.isPresent()) {
                ChargeResponse existingChargeResponse = maybeExistingChargeResponse.get();
                return ResponseEntity.ok(existingChargeResponse);
            }
        }

        return chargeService.create(chargeRequest, accountId, BaseUri.from(request), idempotencyKey)
                .<ResponseEntity<?>>map(response -> {
                    if (authorisationMode == AuthorisationMode.AGREEMENT) {
                        chargeService.markChargeAsEligibleForAuthoriseUserNotPresent(response.getChargeId());
                    }
                    return ResponseEntity.created(response.getLink("self")).body(response);
                })
                .orElseGet(() -> notFoundResponse("Unknown gateway account: " + accountId));
    }

    @PostMapping("/v1/api/accounts/{accountId}/telephone-charges")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a new telephone charge for gateway account.",
            description = "Create a new telephone charge for gateway account. These are externally taken payments and the outcome is reported to this endpoint. " +
                    "provider_id is used as an idempotency key for API calls. If a payment already exists with the provider_id provided, the API will not store a record about a new payment, or update or change the record about a payment previously stored.",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK - returns existing charge for provider_id",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(example = "{" +
                            "    \"error_identifier\": \"TELEPHONE_PAYMENT_NOTIFICATIONS_NOT_ALLOWED\"," +
                            "    \"message\": [" +
                            "        \"Telephone payment notifications are not enabled for this gateway account\"" +
                            "    ]" +
                            "}"))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "422", description = "Missing required fields or invalid values", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<?> createNewTelephoneChargeByAccountId(
            @Parameter(example = "1", description = "Gateway account ID") @PathVariable(ACCOUNT_ID) Long accountId,
            @NotNull @Valid @RequestBody(required = false) TelephoneChargeCreateRequest telephoneChargeCreateRequest,
            HttpServletRequest request
    ) {
        GatewayAccountEntity gatewayAccount = gatewayAccountService.getGatewayAccount(accountId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(accountId));

        if (!gatewayAccount.isAllowTelephonePaymentNotifications()) {
            throw new TelephonePaymentNotificationsNotAllowedException(gatewayAccount.getId());
        }

        return chargeService.findCharge(accountId, telephoneChargeCreateRequest)
                .map(response -> ResponseEntity.status(200).body(response))
                .orElseGet(() -> ResponseEntity.status(201).body(chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount)));
    }

    @PostMapping("/v1/api/service/{serviceId}/account/{accountType}/telephone-charges")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a new telephone charge by service id and account type",
            description = "Create a new telephone charge for a service and account type. These are externally taken payments and the outcome is reported to this endpoint. " +
                    "provider_id is used as an idempotency key for API calls. If a payment already exists with the provider_id provided, the API will not store a record about a new payment, or update or change the record about a payment previously stored.",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK - returns existing charge for provider_id",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(example = "{" +
                            "    \"error_identifier\": \"TELEPHONE_PAYMENT_NOTIFICATIONS_NOT_ALLOWED\"," +
                            "    \"message\": [" +
                            "        \"Telephone payment notifications are not enabled for this gateway account\"" +
                            "    ]" +
                            "}"))),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "422", description = "Missing required fields or invalid values", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    public ResponseEntity<?> createNewTelephoneChargeByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathVariable("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @NotNull @Valid @RequestBody(required = false) TelephoneChargeCreateRequest telephoneChargeCreateRequest,
            HttpServletRequest request
    ) {
        GatewayAccountEntity gatewayAccount = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));

        if (!gatewayAccount.isAllowTelephonePaymentNotifications()) {
            throw new TelephonePaymentNotificationsNotAllowedException(gatewayAccount.getId());
        }

        return chargeService.findCharge(gatewayAccount.getId(), telephoneChargeCreateRequest)
                .map(response -> ResponseEntity.status(200).body(response))
                .orElseGet(() -> ResponseEntity.status(201).body(chargeService.createFromTelephonePaymentNotification(telephoneChargeCreateRequest, gatewayAccount)));
    }

    @PostMapping("/v1/api/accounts/{accountId}/charges/{chargeId}/resend-confirmation-email")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Resend confirmation email for a charge",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "402", description = "Could not send email"),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> resendConfirmationEmail(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathVariable(ACCOUNT_ID) Long accountId,
            @Parameter(example = "spmh0fb7rbi1lebv1j3f7hc3m9", description = "Charge external ID")
            @PathVariable("chargeId") String chargeId) {
        var account = gatewayAccountService.getGatewayAccount(accountId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(accountId));

        return chargeService.findCharge(chargeId, accountId)
                .map(charge -> userNotificationService.sendPaymentConfirmedEmailSynchronously(charge, account, true)
                        .map((reference) -> noContentResponse())
                        .orElseGet(() -> buildErrorResponse(HttpStatus.PAYMENT_REQUIRED, "Failed to send email")))
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @PostMapping("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}/resend-confirmation-email")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Resend confirmation email for a charge",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "402", description = "Could not send email"),
                    @ApiResponse(responseCode = "404", description = "Not found - charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> resendConfirmationEmailByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") // pragma: allowlist secret
            @PathVariable("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type")
            @PathVariable("accountType") GatewayAccountType accountType,
            @Parameter(example = "spmh0fb7rbi1lebv1j3f7hc3m9", description = "Charge external ID")
            @PathVariable("chargeId") String chargeId) {

        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .or(() -> {
                    throw new GatewayAccountNotFoundException(serviceId, accountType);
                })
                .flatMap(account -> chargeService.findCharge(chargeId, account.getId())
                        .map(charge -> Pair.of(charge, account))
                )
                .or(() -> {
                    throw new ChargeNotFoundRuntimeException(chargeId);
                })
                .flatMap(chargeAccountPair -> userNotificationService.sendPaymentConfirmedEmailSynchronously(
                        chargeAccountPair.getLeft(),
                        chargeAccountPair.getRight(),
                        true))
                .map(reference -> noContentResponse())
                .orElseGet(() -> buildErrorResponse(HttpStatus.PAYMENT_REQUIRED, "Failed to send email"));
    }

    @PostMapping("/v1/tasks/expired-charges-sweep")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Expire charges, tokens and idempotency keys",
            description = "This starts a task to expire the charges with a default window of 90 minutes. " +
                    "The default value can be overridden by setting an environment variable CHARGE_EXPIRY_WINDOW_SECONDS in seconds. " +
                    "Response of the call will tell you how many charges were successfully expired and how many of them failed for some reason. " +
                    "This endpoint also expires charges in AWAITING_CAPTURE_REQUEST status. The default window is 120 hours. " +
                    "It can be overriden by setting an environment variable AWAITING_DELAY_CAPTURE_EXPIRY_WINDOW in seconds. " +
                    "Also expires tokens older than the configured TOKEN_EXPIRY_WINDOW_SECONDS, " +
                    "and expires idempotency keys older than the configured IDEMPOTENCY_KEY_EXPIRY_WINDOW_SECONDS.",
            tags = {"Tasks"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"expiry-success\": 2," +
                                    "    \"expiry-failed\": 0" +
                                    "}")))
            }
    )
    public ResponseEntity<?> expireCharges(HttpServletRequest request) {
        Map<String, Integer> resultMap = chargeExpiryService.sweepAndExpireChargesAndTokensAndIdempotencyKeys();
        return successResponseWithEntity(resultMap);
    }

    @Operation(
            summary = "Find charge by gateway transaction ID",
            tags = {"Charges"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = ChargeResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    @GetMapping("/v1/api/charges/gateway_transaction/{gatewayTransactionId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getChargeForGatewayTransactionId(@Parameter(example = "5422624d-12b1-4821-8b26-d0383ecf1602", description = "Gateway transaction ID")
                                                     @PathVariable("gatewayTransactionId") String gatewayTransactionId, HttpServletRequest request) {
        return chargeService.findChargeByGatewayTransactionId(gatewayTransactionId, BaseUri.from(request))
                .<ResponseEntity<?>>map(chargeResponse -> ResponseEntity.ok(chargeResponse))
                .orElseGet(() -> responseWithGatewayTransactionNotFound(gatewayTransactionId));
    }
}
