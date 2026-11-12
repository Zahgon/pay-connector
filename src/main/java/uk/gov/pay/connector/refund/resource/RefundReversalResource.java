package uk.gov.pay.connector.refund.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.exception.RefundNotFoundForChargeException;
import uk.gov.pay.connector.refund.exception.RefundNotFoundRuntimeException;
import uk.gov.pay.connector.refund.model.domain.GithubAndZendeskCredential;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.service.RefundReversalService;

import jakarta.inject.Inject;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.pay.connector.web.MediaTypes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Refunds")
@RestController
public class RefundReversalResource {
    private final ChargeService chargeService;

    private final GatewayAccountDao gatewayAccountDao;

    private final RefundReversalService refundReversalService;

    @Inject
    public RefundReversalResource(ChargeService chargeService, GatewayAccountDao gatewayAccountDao, RefundReversalService refundReversalService) {
        this.chargeService = chargeService;
        this.gatewayAccountDao = gatewayAccountDao;
        this.refundReversalService = refundReversalService;
    }

    @PostMapping("/v1/api/accounts/{gatewayAccountId}/charges/{chargeId}/refunds/{refundId}/reverse-failed")
    @MediaTypes(produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Fix failed refund charge",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "‘Operation not available for Worldpay’"),
                    @ApiResponse(responseCode = "404", description = "Not found - gateway account or charge not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> reverseRefund(@Parameter(example = "1", description = "Gateway account ID")
                                  @PathVariable("gatewayAccountId") Long gatewayAccountId,
                                  @Parameter(example = "2c6vtn9pth38ppbmnt20d57t49", description = "Charge external ID")
                                  @PathVariable("chargeId") String chargeExternalId,
                                  @Parameter(example = "3c6vtn9pth38ppbmnt20d57t49", description = "Refund external ID")
                                  @PathVariable("refundId") String refundExternalId,
                                  HttpServletRequest request,
                                  @Parameter
                                  @RequestBody(required = false) GithubAndZendeskCredential githubAndZendeskCredential
    ) {

        Charge charge = chargeService.findCharge(chargeExternalId)
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));

        if (!PaymentGatewayName.STRIPE.getName().equals(charge.getPaymentGatewayName())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Operation only available for Stripe");
        }

        GatewayAccountEntity gatewayAccount = gatewayAccountDao.findById(gatewayAccountId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(gatewayAccountId));

        if (!charge.getGatewayAccountId().equals(gatewayAccount.getId())) {
            throw new RefundNotFoundForChargeException(refundExternalId, chargeExternalId, gatewayAccountId);
        }

        Refund refund = refundReversalService.findMaybeHistoricRefundByRefundId(refundExternalId)
                .orElseThrow(() -> new RefundNotFoundRuntimeException(refundExternalId));

        if (!refund.getChargeExternalId().equals(chargeExternalId)) {
            throw new RefundNotFoundForChargeException(refundExternalId, chargeExternalId, gatewayAccountId);
        }

        String githubUserId = githubAndZendeskCredential.githubUserId();
        String zendeskUserId = githubAndZendeskCredential.zendeskTicketId();
        return refundReversalService.reverseFailedRefund(gatewayAccount, refund, charge, githubUserId, zendeskUserId);

    }
}
