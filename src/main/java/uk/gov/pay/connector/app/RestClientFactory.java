package uk.gov.pay.connector.app;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.util.Timeout;
import uk.gov.pay.connector.app.config.RestClientConfig;
import uk.gov.pay.connector.client.RestClientLoggingInterceptor;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import static java.lang.String.format;

/**
 * Builds the HTTP clients for the other GOV.UK Pay services (ledger and cardid): without a read timeout, with the
 * given connect timeout (none if null), over TLS 1.3 unless internal HTTPS is disabled. Request entities are sent
 * with their length and answers are not asked to be compressed.
 */
public class RestClientFactory {
    private static final String TLSV1_3 = "TLSv1.3";

    public static CloseableHttpClient buildClient(RestClientConfig clientConfig, Duration connectTimeout) {
        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(connectTimeout == null ? Timeout.DISABLED : Timeout.ofMilliseconds(connectTimeout.toMillis()))
                        .setSocketTimeout(Timeout.DISABLED)
                        .build());

        if (!clientConfig.isDisabledSecureConnection()) {
            try {
                SSLContext sslContext = SSLContext.getInstance(TLSV1_3);
                sslContext.init(null, null, null);
                connectionManagerBuilder.setTlsSocketStrategy(new DefaultClientTlsStrategy(sslContext));
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException(format("Unable to find an SSL context for %s", TLSV1_3), e);
            }
        }

        return HttpClients.custom()
                .setConnectionManager(connectionManagerBuilder.build())
                .disableContentCompression()
                .disableCookieManagement()
                .addExecInterceptorFirst("request-logging", new RestClientLoggingInterceptor())
                .build();
    }

    private RestClientFactory() {
    }
}
