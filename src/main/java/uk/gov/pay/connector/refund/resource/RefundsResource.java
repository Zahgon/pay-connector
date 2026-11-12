package uk.gov.pay.connector.refund.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.refund.exception.RefundException;
import uk.gov.pay.connector.refund.model.RefundRequest;
import uk.gov.pay.connector.refund.model.RefundResponse;
import uk.gov.pay.connector.refund.service.ChargeRefundResponse;
import uk.gov.pay.connector.refund.service.RefundService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.pay.connector.web.MediaTypes;
import uk.gov.pay.connector.web.BaseUri;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;

import static uk.gov.pay.connector.charge.resource.ChargesApiResource.MAX_AMOUNT;
import static uk.gov.pay.connector.charge.resource.ChargesApiResource.MIN_AMOUNT;
import static uk.gov.pay.connector.refund.exception.RefundException.ErrorCode.NOT_SUFFICIENT_AMOUNT_AVAILABLE;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithChargeNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.responseWithRefundNotFound;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Refunds")
@RestController
public class RefundsResource {

    private final RefundService refundService;
    private final ChargeService chargeService;
    private final GatewayAccountService gatewayAccountService;
    private final ChargeDao chargeDao;

    @Inject
    public RefundsResource(RefundService refundService, ChargeService chargeService, GatewayAccountService gatewayAccountService, ChargeDao chargeDao) {
        this.refundService = refundService;
        this.chargeService = chargeService;
        this.gatewayAccountService = gatewayAccountService;
        this.chargeDao = chargeDao;
    }

    @PostMapping("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Refund a charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"amount\":3444," +
                                    "    \"created_date\":\"2016-10-05T14:15:34.096Z\"," +
                                    "    \"refund_id\":\"vijjk08adovg10gfqc46joem2l\"," +
                                    "    \"user_external_id\":\"AA213FD51B3801043FBC\"," +
                                    "    \"status\":\"success\"," +
                                    "    \"_links\":{" +
                                    "        \"self\":{\"href\":\"https://connector.example.com/v1/api/accounts/1/charges/2c6vtn9pth38ppbmnt20d57t49/refunds/vijjk08adovg10gfqc46joem2l\"}," +
                                    "        \"payment\":{\"href\":\"https://connector.example.com/v1/api/accounts/1/charges/2c6vtn9pth38ppbmnt20d57t49\"}" +
                                    "    }" +
                                    "}"))),
                    @ApiResponse(responseCode = "400", description = "Bad request - Invalid fields or not sufficient amount available for refund"),
                    @ApiResponse(responseCode = "404", description = "Not found - gateway account or charge not found"),
                    @ApiResponse(responseCode = "412", description = "Precondition failed - Refund amount available does not match"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> submitRefund(@Parameter(example = "1", description = "Gateway account ID")
                                 @PathVariable("accountId") Long accountId,
                                 @Parameter(example = "2c6vtn9pth38ppbmnt20d57t49", description = "Charge external ID")
                                 @PathVariable("chargeId") String chargeExternalId,
                                 @RequestBody(required = false) RefundRequest refundRequest, HttpServletRequest request) {
        validateRefundRequest(refundRequest.getAmount());
        
        GatewayAccountEntity account = gatewayAccountService.getGatewayAccount(accountId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(accountId));

        ChargeRefundResponse refundServiceResponse = chargeService.findCharge(chargeExternalId, accountId)
                .map(charge -> refundService.submitRefund(account, charge, refundRequest))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
        
        GatewayRefundResponse refundResponse = refundServiceResponse.getGatewayRefundResponse();
        if (refundResponse.isSuccessful()) {
            return ResponseEntity.accepted().body(RefundResponse.valueOf(refundServiceResponse.getRefundEntity(), accountId, BaseUri.from(request)).serialize());
        }
        return serviceErrorResponse(refundResponse.getError().map(GatewayError::getMessage).orElse("unknown error"));
    }



    @PostMapping("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}/refunds")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Refund a charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"amount\":3444," +
                                    "    \"created_date\":\"2016-10-05T14:15:34.096Z\"," +
                                    "    \"refund_id\":\"vijjk08adovg10gfqc46joem2l\"," +
                                    "    \"user_external_id\":\"AA213FD51B3801043FBC\"," +
                                    "    \"status\":\"success\"," +
                                    "    \"_links\":{" +
                                    "        \"self\":{\"href\":\"https://connector.example.com/v1/api/service/46eb1b601348499196c99de90482ee68/account/test/charges/2c6vtn9pth38ppbmnt20d57t49/refunds/vijjk08adovg10gfqc46joem2l\"}," +
                                    "        \"payment\":{\"href\":\"https://connector.example.com/v1/api/service/46eb1b601348499196c99de90482ee68/account/test/charges/2c6vtn9pth38ppbmnt20d57t49\"}" +
                                    "    }" +
                                    "}"))),
                    @ApiResponse(responseCode = "400", description = "Bad request - Invalid fields or not sufficient amount available for refund"),
                    @ApiResponse(responseCode = "404", description = "Not found - gateway account or charge not found"),
                    @ApiResponse(responseCode = "412", description = "Precondition failed - Refund amount available does not match"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> submitRefundByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service external ID") @PathVariable("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account Type") @PathVariable("accountType") GatewayAccountType accountType,
            @Parameter(example = "2c6vtn9pth38ppbmnt20d57t49", description = "Charge external ID") @PathVariable("chargeId") String chargeExternalId,
            @Valid @RequestBody(required = false) RefundRequest refundRequest, HttpServletRequest request
    ) {
        validateRefundRequest(refundRequest.getAmount());
        
        GatewayAccountEntity account = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));

        ChargeRefundResponse refundServiceResponse = chargeService.findCharge(chargeExternalId, account.getId())
                .map(charge -> refundService.submitRefund(account, charge, refundRequest))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
        
        GatewayRefundResponse gatewayRefundResponse = refundServiceResponse.getGatewayRefundResponse();
        
        if (gatewayRefundResponse.isSuccessful()) {
            return ResponseEntity.accepted().body(RefundResponse.valueOf(refundServiceResponse.getRefundEntity(), serviceId, accountType, BaseUri.from(request)).serialize());
        }
        return serviceErrorResponse(gatewayRefundResponse.getError().map(GatewayError::getMessage).orElse("unknown error"));
    }

    private void validateRefundRequest(long amount) {
        if (MAX_AMOUNT < amount) {
            throw RefundException.notAvailableForRefundException("Not sufficient amount available for refund", NOT_SUFFICIENT_AMOUNT_AVAILABLE);
        }
        if (MIN_AMOUNT > amount) {
            throw RefundException.notAvailableForRefundException("Validation error for amount. Minimum amount for a refund is " + MIN_AMOUNT, RefundException.ErrorCode.MINIMUM_AMOUNT);
        }
    }

    @GetMapping("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds/{refundId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get a refund",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"_links\": {" +
                                    "            \"payment\": {" +
                                    "                \"href\": \"https://connector.example.com/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn\"" +
                                    "            }," +
                                    "            \"self\": {" +
                                    "                \"href\": \"https://connector.example.com/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn/refunds/vijjk08adovg10gfqc46joem2l\"" +
                                    "            }" +
                                    "        }," +
                                    "    \"amount\": 3444," +
                                    "    \"fee\": 2," +
                                    "    \"net_amount\": -3446," +
                                    "    \"created_date\": \"2016-10-05T14:15:34.096Z\"," +
                                    "    \"refund_id\": \"vijjk08adovg10gfqc46joem2l\"," +
                                    "    \"user_external_id\": \"AA213FD51B3801043FBC\"," +
                                    "    \"status\": \"success\"" +
                                    "}"))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge or refund not found")
            }
    )
    public ResponseEntity<?> getRefund(@Parameter(example = "1", description = "Gateway account ID")
                              @PathVariable("accountId") Long accountId,
                              @Parameter(example = "uqu4s24383qkod35rsb06gv3cn", description = "Charge external ID")
                              @PathVariable("chargeId") String chargeId,
                              @Parameter(example = "vijjk08adovg10gfqc46joem2l", description = "Refund external ID")
                              @PathVariable("refundId") String refundId,
                              HttpServletRequest request) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .<ResponseEntity<?>>map(chargeEntity -> getRefundResponse(chargeEntity, refundId, accountId, BaseUri.from(request)))
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    @GetMapping("/v1/api/refunds/{refundExternalId}")
    @Operation(
            summary = "Check if a refund exists in Connector",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Refund exists in Connector"),
                    @ApiResponse(responseCode = "404", description = "Refund missing or expunged")
            }
    )
    public ResponseEntity<?> getRefundByExternalId(@PathVariable("refundExternalId") String refundExternalId) {
        return refundService.findRefundByExternalId(refundExternalId)
                .map(refundEntity -> ResponseEntity.ok().build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
 
    @GetMapping("/v1/api/service/{serviceId}/account/{accountType}/charges/{chargeId}/refunds/{refundId}")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get a refund",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "    \"_links\": {" +
                                    "            \"payment\": {" +
                                    "                \"href\": \"https://connector.example.com/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn\"" +
                                    "            }," +
                                    "            \"self\": {" +
                                    "                \"href\": \"https://connector.example.com/v1/api/accounts/2/charges/uqu4s24383qkod35rsb06gv3cn/refunds/vijjk08adovg10gfqc46joem2l\"" +
                                    "            }" +
                                    "        }," +
                                    "    \"amount\": 3444," +
                                    "    \"fee\": 2," +
                                    "    \"net_amount\": -3446," +
                                    "    \"created_date\": \"2016-10-05T14:15:34.096Z\"," +
                                    "    \"refund_id\": \"vijjk08adovg10gfqc46joem2l\"," +
                                    "    \"user_external_id\": \"AA213FD51B3801043FBC\"," +
                                    "    \"status\": \"success\"" +
                                    "}"))),
                    @ApiResponse(responseCode = "404", description = "Not found - charge or refund not found")
            }
    )
    public ResponseEntity<?> getRefund(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathVariable("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathVariable("accountType") GatewayAccountType accountType,
            @Parameter(example = "b02b63b370fd35418ad66b0101", description = "Charge external ID") @PathVariable("chargeId") String chargeId,
            @Parameter(example = "vijjk08adovg10gfqc46joem2l", description = "Refund external ID") @PathVariable("refundId") String refundId,
            HttpServletRequest request) {
        return chargeDao.findByExternalIdAndServiceIdAndAccountType(chargeId, serviceId, accountType)
                .<ResponseEntity<?>>map(chargeEntity -> 
                        getRefundResponse(chargeEntity, refundId, serviceId, accountType, BaseUri.from(request))
                )
                .orElseGet(() -> responseWithChargeNotFound(chargeId));
    }

    private ResponseEntity<?> getRefundResponse(ChargeEntity chargeEntity, String refundId, Long accountId, BaseUri uriInfo) {
        return refundService.findNotExpungedRefunds(chargeEntity.getExternalId()).stream()
                .filter(refundEntity -> refundEntity.getExternalId().equals(refundId))
                .findFirst()
                .<ResponseEntity<?>>map(refundEntity -> ResponseEntity.ok(RefundResponse.valueOf(refundEntity, accountId, uriInfo).serialize()))
                .orElseGet(() -> responseWithRefundNotFound(refundId));
    }

    private ResponseEntity<?> getRefundResponse(ChargeEntity chargeEntity, String refundId, String serviceId, GatewayAccountType accountType, BaseUri uriInfo) {
        return refundService.findNotExpungedRefunds(chargeEntity.getExternalId()).stream()
                .filter(refundEntity -> refundEntity.getExternalId().equals(refundId))
                .findFirst()
                .<ResponseEntity<?>>map(refundEntity -> ResponseEntity.ok(RefundResponse.valueOf(refundEntity, serviceId, accountType, uriInfo).serialize()))
                .orElseGet(() -> responseWithRefundNotFound(refundId));
    }
}
