package uk.gov.pay.connector.webhook.resource;

import com.adyen.model.notification.NotificationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.adyen.webhook.json.token.AdyenTokenNotification;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.gateway.sandbox.SandboxNotificationService;
import uk.gov.pay.connector.gateway.stripe.StripeNotificationService;
import uk.gov.pay.connector.gateway.worldpay.WorldpayNotificationService;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import uk.gov.pay.connector.web.MediaTypes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import static io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.util.ResponseUtil.forbiddenErrorResponse;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_XML_VALUE;

@Tag(name = "Notifications")
@RestController
public class NotificationResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationResource.class);

    private final WorldpayNotificationService worldpayNotificationService;
    private final SandboxNotificationService sandboxNotificationService;
    private final StripeNotificationService stripeNotificationService;
    private final AdyenNotificationService adyenNotificationService;

    @Inject
    public NotificationResource(WorldpayNotificationService worldpayNotificationService,
                                SandboxNotificationService sandboxNotificationService,
                                StripeNotificationService stripeNotificationService,
                                AdyenNotificationService adyenNotificationService
    ) {
        this.worldpayNotificationService = worldpayNotificationService;
        this.sandboxNotificationService = sandboxNotificationService;
        this.stripeNotificationService = stripeNotificationService;
        this.adyenNotificationService = adyenNotificationService;
    }

    @PostMapping("/v1/api/notifications/sandbox")
    @MediaTypes(consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Handle sandbox notifications",
            description = "This endpoint returns a HTTP status code of 200 for authorized requests. This is used for testing purposes. " +
                    "Note that the authorization methods for each of the v1/api/notifications/<PSP> endpoints uses different authorization " +
                    "methods and a successful response from this endpoint does not guarantee that the other notifications endpoints are working as expected. " +
                    "It does provide assurance that the requests are being correctly proxied to Connector and that Connector is responding. " +
                    "<br>" +
                    "Requests are authorised either via the source IP address extracted from the HTTP x-forwarded-for header against the expected CIDRs " +
                    "from SANDBOX_ALLOWED_CIDRS or by validating the secret provided via the HTTP Authorization header against the secret within SANDBOX_AUTH_TOKEN. " +
                    "The latter use of the HTTP Authorization header provides a means to test the endpoint without needing to send the request from a fixed IP address." +
                    "<br>" +
                    "The request body is not deserialised or processed in any way.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - notification rejected")
            }
    )
    public ResponseEntity<?> authoriseSandboxNotifications(
            @Parameter(in = HEADER, example = "let-me-in") @RequestHeader(value = "Authorization", required = false) String authToken,
            @Parameter(in = HEADER, example = "1.1.1.1, 3.3.3.3") @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedIpAddresses) {

        if (!sandboxNotificationService.handleNotificationFor(forwardedIpAddresses, authToken)) {
            logRejectionMessage(forwardedIpAddresses, SANDBOX);
            return forbiddenErrorResponse();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/v1/api/notifications/worldpay")
    @MediaTypes(produces = {TEXT_XML_VALUE, APPLICATION_JSON_VALUE}, consumes = TEXT_XML_VALUE)
    @Operation(
            summary = "Handle Worldpay notifications",
            description = "See https://github.com/govuk-pay/pay-connector/blob/master/src/test/resources/templates/worldpay/notification.txt for example notification",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - notification rejected")
            }
    )
    public ResponseEntity<?> authoriseWorldpayNotifications(@Parameter(example = "see https://github.com/govuk-pay/pay-connector/blob/master/src/test/resources/templates/worldpay/notification.xml for example notification")
                                                   @RequestBody(required = false) String notification,
                                                   @Parameter(in = HEADER, example = "4.3.2.1")
                                                   @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedIpAddresses) {
        if (!worldpayNotificationService.handleNotificationFor(forwardedIpAddresses, notification)) {
            logRejectionMessage(forwardedIpAddresses, WORLDPAY);
            return forbiddenErrorResponse();
        }
        String response = "[OK]";
        logResponseMessage(response, WORLDPAY);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/v1/api/notifications/stripe")
    @MediaTypes(produces = {TEXT_XML_VALUE, APPLICATION_JSON_VALUE}, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Handle Stripe notifications",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - notification rejected")
            }
    )
    public ResponseEntity<?> authoriseStripeNotifications(@RequestBody(required = false) String notification,
                                                 @Parameter(example = "t=1492774577,v1=5257a869e7ecebeda32affa62cdca3fa51cad7e77a0e56ff536d0ce8e108d8bd,v0=6ffbb59b2300aae63f272406069a9788598b792a944a07aba816edb039989a39")
                                                 @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader,
                                                 @Parameter(example = "1.2.3.4")
                                                 @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedIpAddresses) {
        if (!stripeNotificationService.handleNotificationFor(notification, signatureHeader, forwardedIpAddresses)) {
            logRejectionMessage(forwardedIpAddresses, STRIPE);
            return forbiddenErrorResponse();
        }
        String response = "[OK]";
        logResponseMessage(response, STRIPE);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/v1/api/notifications/adyen")
    @MediaTypes(consumes = APPLICATION_JSON_VALUE)
    @Schema(oneOf = {NotificationRequest.class, AdyenTokenNotification.class})
    @Operation(
            summary = "Handle Adyen payment and token notifications",
            description = "Accepts Adyen payment and token webhooks as JSON and preserves the raw request body for signature verification.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - notification rejected"),
                    @ApiResponse(responseCode = "405", description = "Method Not Allowed - Unsupported HTTP method"),
                    @ApiResponse(responseCode = "415", description = "Unsupported Media Type - Unsupported content type")
            }
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Adyen notification payload",
            required = true,
            content = @Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    schema = @Schema(oneOf = {NotificationRequest.class, AdyenTokenNotification.class})
            )
    )
    public ResponseEntity<?> authoriseAdyenNotifications(@RequestBody(required = false) String notification,
                                                @Parameter(in = HEADER, example = "5.6.7.8")
                                                @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedIpAddresses,
                                                @RequestHeader(value = "hmacSignature", required = false) String hmacSignature) {
        if (!adyenNotificationService.handleNotificationFor(notification, forwardedIpAddresses, hmacSignature)) {
            logRejectionMessage(forwardedIpAddresses, ADYEN);
            return forbiddenErrorResponse();
        }
        logResponseMessage("[accepted]", ADYEN);
        return ResponseEntity.ok().build();
    }


    private void logRejectionMessage(String forwardedIpAddresses, PaymentGatewayName gateway) {
        LOGGER.info(String.format("Rejected notification from provider %s for IP '%s'", gateway.getName(), forwardedIpAddresses),
                kv(PROVIDER, gateway.getName()),
                kv("notification_source", forwardedIpAddresses));
    }

    private void logResponseMessage(String response, PaymentGatewayName gateway) {
        LOGGER.info(String.format("Responding to notification from provider %s with 200 %s", gateway.getName(), response),
                kv(PROVIDER, gateway.getName()));
    }
}
