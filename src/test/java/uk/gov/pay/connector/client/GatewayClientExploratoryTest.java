package uk.gov.pay.connector.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.util.TestClientFactory.createClientWithApacheConnectorAndTimeout;
import static uk.gov.pay.connector.util.TestClientFactory.createHttpClient;

@Disabled //this should be run manually
public class GatewayClientExploratoryTest {

    @Test
    public void connectionToInvalidUrlUsingDefaultHttpClient() throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            String gatewayUrl = "http://invalidone.invalid";
            var ex = assertThrows(IOException.class, () -> postXMLRequestFor(client, gatewayUrl, "<request/>"));
            assertThat(UnknownHostException.class, is(ex.getClass()));
        }
    }

    @Test
    public void connectionToInvalidUrlUsingPooledHttpClient() throws IOException {
        try (CloseableHttpClient client = createHttpClient()) {
            String gatewayUrl = "http://invalidone.invalid";
            var ex = assertThrows(IOException.class, () -> postXMLRequestFor(client, gatewayUrl, "<request/>"));
            assertThat(UnknownHostException.class, is(ex.getClass()));
        }
    }

    @Test
    public void connectionReadTimeoutTest() throws IOException {
        WireMockConfiguration wireMockConfig = wireMockConfig().dynamicPort();
        WireMockServer wireMockServer = new WireMockServer(wireMockConfig);

        wireMockServer.start();
        int port = wireMockServer.port();

        WireMock.configureFor("localhost", port);
        int delay = 10000;
        stubFor(
                post(urlPathEqualTo("/pal/servlet/soap/Payment"))
                        .willReturn(
                                aResponse()
                                        .withFixedDelay(delay)
                        )
        );

        String gatewayUrl = "http://localhost:" + port + "/pal/servlet/soap/Payment";

        try (CloseableHttpClient client = createClientWithApacheConnectorAndTimeout(500)) {
            var ex = assertThrows(IOException.class, () -> postXMLRequestFor(client, gatewayUrl, "<request/>"));
            assertThat(SocketTimeoutException.class, is(ex.getClass()));
        }

        wireMockServer.stop();
    }

    public int postXMLRequestFor(CloseableHttpClient client, String gatewayUrl, String requestBody) throws IOException {
        HttpPost request = new HttpPost(gatewayUrl);
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_XML));
        return client.execute(request, response -> response.getCode());
    }
}
