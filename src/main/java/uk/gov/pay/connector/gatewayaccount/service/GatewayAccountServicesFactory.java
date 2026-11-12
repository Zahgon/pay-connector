package uk.gov.pay.connector.gatewayaccount.service;

import org.springframework.stereotype.Component;

@Component
public class GatewayAccountServicesFactory {

    private final GatewayAccountService gatewayAccountService;

    public GatewayAccountServicesFactory(GatewayAccountService gatewayAccountService) {
        this.gatewayAccountService = gatewayAccountService;
    }

    public GatewayAccountService getUpdateService() {
        return gatewayAccountService;
    }
}
