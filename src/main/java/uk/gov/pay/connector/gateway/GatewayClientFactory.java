package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;

import jakarta.inject.Inject;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.stereotype.Component;

@Component
public class GatewayClientFactory {

    private final ClientFactory clientFactory;

    @Inject
    public GatewayClientFactory(ClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public GatewayClient createGatewayClient(PaymentGatewayName gateway,
                                             GatewayOperation operation,
                                             MetricRegistry metricRegistry) {
        CloseableHttpClient client = clientFactory.createHttpClient(gateway, operation, metricRegistry);
        return new GatewayClient(client, metricRegistry);
    }

    public GatewayClient createGatewayClient(PaymentGatewayName gateway,
                                             MetricRegistry metricRegistry) {
        CloseableHttpClient client = clientFactory.createHttpClient(gateway, metricRegistry);
        return new GatewayClient(client, metricRegistry);
    }
}
