package uk.gov.pay.connector.gatewayaccount.exception;

import uk.gov.pay.connector.util.ResponseUtil;

import uk.gov.pay.connector.common.exception.WebApplicationException;

public class NotSupportedGatewayAccountException extends WebApplicationException {
    public NotSupportedGatewayAccountException(Long accountId,
                                               String gatewayName,
                                               String path) {
        super(ResponseUtil.badRequestResponse(
                String.format("Gateway account %s is not %s gateway account for path [%s].",
                    accountId, gatewayName, path)));
    }
}
