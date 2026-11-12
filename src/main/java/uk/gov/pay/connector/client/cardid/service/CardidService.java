package uk.gov.pay.connector.client.cardid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.client.HttpClientException;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.model.CardInformationRequest;

import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.hc.core5.http.HttpStatus.SC_OK;

@Service
public class CardidService {
    
    private static final String CARD_INFORMATION_PATH = "/v1/api/card";
    private static final String APPLICATION_JSON = "application/json";
    // Entities exchanged with cardid are written and read by a plain Jackson mapper, without the service's own settings.
    private static final ObjectMapper ENTITY_MAPPER = new ObjectMapper();

    private final CloseableHttpClient client;
    private final String cardidUrl;

    @Inject
    public CardidService(CloseableHttpClient client, ConnectorConfiguration configuration) {
        this.client = client;
        this.cardidUrl = configuration.getCardidBaseUrl();
    }
    
    public Optional<CardInformation> getCardInformation(String cardNumber) {
        var uri = UriComponentsBuilder.fromUriString(cardidUrl).path(CARD_INFORMATION_PATH).build().toUri();
        var cardInformationRequest = new CardInformationRequest(cardNumber);
        HttpPost request = new HttpPost(uri);
        request.addHeader(HttpHeaders.ACCEPT, APPLICATION_JSON);
        request.addHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
        try {
            request.setEntity(new ByteArrayEntity(ENTITY_MAPPER.writeValueAsBytes(cardInformationRequest),
                    ContentType.create(APPLICATION_JSON)));
            return client.execute(request, response -> {
                if (response.getCode() == SC_OK) {
                    String entity = EntityUtils.toString(response.getEntity(), UTF_8);
                    return Optional.of(ENTITY_MAPPER.readValue(entity, CardInformation.class));
                }
                return Optional.empty();
            });
        } catch (IOException e) {
            throw new HttpClientException(e);
        }
    }
}
