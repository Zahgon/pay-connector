package uk.gov.pay.connector.gatewayaccount.exception;

import uk.gov.pay.connector.util.ResponseUtil;

import uk.gov.pay.connector.common.exception.WebApplicationException;

public class DigitalWalletNotSupportedGatewayException extends WebApplicationException {
    public DigitalWalletNotSupportedGatewayException(String gatewayName){
        super(ResponseUtil.badRequestResponse((String.format("Gateway %s does not support digital wallets.", gatewayName))));
    }
}
