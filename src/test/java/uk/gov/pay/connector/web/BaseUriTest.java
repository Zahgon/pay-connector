package uk.gov.pay.connector.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BaseUriTest {

    @Test
    void from_shouldRewriteSchemeOfTheAddressTheRequestWasSentToToHttps() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hey/you");
        request.setScheme("tcp");
        request.setServerName("pay.gov.uk");
        request.setServerPort(80);

        BaseUri baseUri = BaseUri.from(request);

        assertThat(baseUri.toString(), is("https://pay.gov.uk/"));
        assertThat(baseUri.getBaseUriBuilder().path("hey/you").build().toUri(), is(URI.create("https://pay.gov.uk/hey/you")));
    }

    @Test
    void from_shouldKeepTheHostAndPortTheRequestNames() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/api/accounts/1");
        request.setServerName("localhost");
        request.setServerPort(9300);

        assertThat(BaseUri.from(request).toString(), is("https://localhost:9300/"));
    }
}
