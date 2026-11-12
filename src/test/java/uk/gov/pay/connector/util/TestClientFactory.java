package uk.gov.pay.connector.util;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;

public class TestClientFactory {
    public static CloseableHttpClient createHttpClient() {
        return createClientWithApacheConnectorAndTimeout(-1);
    }

    public static CloseableHttpClient createClientWithApacheConnectorAndTimeout(int readTimeout) {
        ConnectionConfig.Builder connectionConfig = ConnectionConfig.custom();
        if (readTimeout > 0) {
            connectionConfig.setSocketTimeout(Timeout.ofMilliseconds(readTimeout));
        }
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfig.build())
                        .build())
                .build();
    }
}
