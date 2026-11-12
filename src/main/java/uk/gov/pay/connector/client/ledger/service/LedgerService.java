package uk.gov.pay.connector.client.ledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Named;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.client.HttpClientException;
import uk.gov.pay.connector.client.ledger.exception.GetRefundsForPaymentException;
import uk.gov.pay.connector.client.ledger.exception.LedgerException;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.RefundTransactionsForPayment;
import uk.gov.pay.connector.events.model.Event;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.hc.core5.http.HttpStatus.SC_ACCEPTED;
import static org.apache.hc.core5.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.HTTP_STATUS;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.URL;

@Service
public class LedgerService {

    private static final String APPLICATION_JSON = "application/json";
    // Entities exchanged with ledger are written and read by a plain Jackson mapper, without the service's own settings.
    private static final ObjectMapper ENTITY_MAPPER = new ObjectMapper();

    private final Logger logger = LoggerFactory.getLogger(LedgerService.class);

    private final CloseableHttpClient client;
    private final CloseableHttpClient postEventClient;
    private final String ledgerUrl;
    private final URI eventUri;

    @Inject
    public LedgerService(CloseableHttpClient client, @Named("ledgerClient") CloseableHttpClient ledgerClient, ConnectorConfiguration configuration) {
        this.ledgerUrl = configuration.getLedgerBaseUrl();
        this.eventUri = UriComponentsBuilder.fromUriString(this.ledgerUrl).path("/v1/event").build().toUri();
        this.client = client;
        this.postEventClient = ledgerClient;
    }

    public Optional<LedgerTransaction> getTransaction(String id) {
        var uri = UriComponentsBuilder
                .fromUriString(ledgerUrl)
                .path(format("/v1/transaction/%s", id))
                .queryParam("override_account_id_restriction", "true");

        return getTransactionFromLedger(uri);
    }

    public Optional<LedgerTransaction> getTransactionForProviderAndGatewayTransactionId(String paymentGatewayName,
                                                                                        String gatewayTransactionId) {
        var uri = UriComponentsBuilder
                .fromUriString(ledgerUrl)
                .path("/v1/transaction/gateway-transaction")
                .queryParam("gateway_transaction_id", gatewayTransactionId)
                .queryParam("payment_provider", paymentGatewayName);
        
        return getTransactionFromLedger(uri);
    }

    public Optional<LedgerTransaction> getTransactionForGatewayAccount(String id, Long gatewayAccountId) {
        var uri = UriComponentsBuilder
                .fromUriString(ledgerUrl)
                .path(format("/v1/transaction/%s", id))
                .queryParam("account_id", gatewayAccountId);

        return getTransactionFromLedger(uri);
    }

    public RefundTransactionsForPayment getRefundsForPayment(Long gatewayAccountId, String paymentExternalId) {
        var uri = UriComponentsBuilder
                .fromUriString(ledgerUrl)
                .path(format("/v1/transaction/%s/transaction", paymentExternalId))
                .queryParam("gateway_account_id", gatewayAccountId)
                .queryParam("transaction_type", "REFUND");

        LedgerResponse response = getResponse(uri);

        if (response.status() == SC_OK) {
            try {
                return ENTITY_MAPPER.readValue(response.entity(), RefundTransactionsForPayment.class);
            } catch (JsonProcessingException exception) {
                logger.error("Error processing response from ledger for payment refunds: {} {}",
                        kv(GATEWAY_ACCOUNT_ID, gatewayAccountId),
                        kv(PAYMENT_EXTERNAL_ID, paymentExternalId));
                throw new LedgerException(exception);
            }
        } else {
            logger.error("Received non-success status code for get refunds for payment from Ledger: {}, {}",
                    kv(GATEWAY_ACCOUNT_ID, gatewayAccountId),
                    kv(PAYMENT_EXTERNAL_ID, paymentExternalId));
            throw new GetRefundsForPaymentException(response.status(), response.describe());
        }
    }

    public void postEvent(Event event) {
        postEvents(List.of(event));
    }
    
    public void postEvent(List<Event> events) {
        postEvents(events);
    }
    
    private void postEvents(List<Event> events) {
        String eventsList = events.stream().map(Event::getEventType).collect(Collectors.joining(", "));
        logger.info("Making POST request to send events to ledger for: [" + eventsList + "]", 
                kv("events", events.stream().map(Event::toString).collect(Collectors.toList())));
        byte[] entity;
        try {
            entity = ENTITY_MAPPER.writeValueAsBytes(events);
        } catch (JsonProcessingException e) {
            throw new HttpClientException(e);
        }
        HttpPost request = new HttpPost(eventUri);
        request.addHeader(HttpHeaders.ACCEPT, APPLICATION_JSON);
        request.addHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
        request.setEntity(new ByteArrayEntity(entity, ContentType.create(APPLICATION_JSON)));
        LedgerResponse response = execute(postEventClient, request);

        if (response.status() != SC_ACCEPTED) {
            throw new LedgerException(response.status(), response.describe());
        }
    }

    private Optional<LedgerTransaction> getTransactionFromLedger(UriComponentsBuilder uri) {
        LedgerResponse response = getResponse(uri);

        if (response.status() == SC_OK) {
            try {
                return Optional.of(ENTITY_MAPPER.readValue(response.entity(), LedgerTransaction.class));
            } catch (JsonProcessingException e) {
                throw new HttpClientException(e);
            }
        } 
        if (response.status() == SC_NOT_FOUND) {
            return Optional.empty();
        }
        logger.error("Received error status code for GET transaction from ledger.", 
                kv(URL, uri.toUriString()),
                kv(HTTP_STATUS, response.status()));
        throw new LedgerException(response.status(), response.describe());
    }

    private LedgerResponse getResponse(UriComponentsBuilder uri) {
        HttpGet request = new HttpGet(uri.encode().build().toUri());
        // a request that names no media type accepts any, as the service's requests to ledger always have
        request.addHeader(HttpHeaders.ACCEPT, "*/*");
        return execute(client, request);
    }

    private static LedgerResponse execute(CloseableHttpClient httpClient, HttpUriRequestBase request) {
        try {
            return httpClient.execute(request, response -> new LedgerResponse(request.getMethod(), request.getRequestUri(),
                    response.getCode(), response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), UTF_8)));
        } catch (IOException e) {
            throw new HttpClientException(e);
        }
    }

    private record LedgerResponse(String method, String uri, int status, String entity) {
        String describe() {
            return format("%s %s answered with status %d", method, uri, status);
        }
    }
}
