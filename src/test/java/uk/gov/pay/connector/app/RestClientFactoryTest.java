package uk.gov.pay.connector.app;


import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.app.config.RestClientConfig;
import uk.gov.pay.connector.util.HttpClientInspector;

import javax.net.ssl.SSLContext;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestClientFactoryTest {

    @Test
    void restClient_shouldUseSSLWhenSecureInternalCommunicationIsOn() throws Exception {
        //given
        RestClientConfig clientConfiguration = mock(RestClientConfig.class);
        when(clientConfiguration.isDisabledSecureConnection()).thenReturn(false);

        //when
        try (CloseableHttpClient client = RestClientFactory.buildClient(clientConfiguration, null)) {

            //then
            SSLContext sslContext = sslContextOf(client).orElseThrow();
            assertThat(sslContext.getProtocol(), is("TLSv1.3"));
        }
    }

    @Test
    void restClient_shouldNotUseSSLWhenSecureInternalCommunicationIsOff() throws Exception {
        //given
        RestClientConfig clientConfiguration = mock(RestClientConfig.class);
        when(clientConfiguration.isDisabledSecureConnection()).thenReturn(true);

        //when
        try (CloseableHttpClient client = RestClientFactory.buildClient(clientConfiguration, null)) {

            //then
            assertThat(sslContextOf(client).map(SSLContext::getProtocol).filter("TLSv1.3"::equals).isPresent(), is(false));
        }
    }

    private static Optional<SSLContext> sslContextOf(CloseableHttpClient client) {
        return HttpClientInspector.find(client, SSLContext.class);
    }
}
