package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.apache.hc.core5.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_ERROR_RESPONSE;

public abstract class AdyenMockClient {

    private final WireMockServer wireMockServer;

    AdyenMockClient(WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
    }

    void setupPatchResponse(String responseBody, String path, int status) {
        wireMockServer.stubFor(patch(urlPathEqualTo(path))
                .withHeader(CONTENT_TYPE, matching(APPLICATION_JSON_VALUE))
                .willReturn(aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withStatus(status)
                        .withBody(responseBody)));
    }

    void setupPostResponse(String responseBody, String path, int status) {
        wireMockServer.stubFor(post(urlPathEqualTo(path))
                .withHeader(CONTENT_TYPE, matching(APPLICATION_JSON_VALUE))
                .willReturn(aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withStatus(status)
                        .withBody(responseBody)));
    }

    public void mockError(String path) {
        String responseBody = TestTemplateResourceLoader.load(ADYEN_ERROR_RESPONSE);
        setupPostResponse(responseBody, path, SC_INTERNAL_SERVER_ERROR);
    }
}
