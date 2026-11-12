package uk.gov.pay.connector.client.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.client.ledger.exception.GetRefundsForPaymentException;
import uk.gov.pay.connector.client.ledger.exception.LedgerException;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.agreement.AgreementCreated;
import uk.gov.pay.connector.events.model.agreement.AgreementInactivated;
import uk.gov.pay.connector.events.model.agreement.AgreementSetUp;
import uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.apache.hc.core5.http.HttpStatus.SC_ACCEPTED;
import static org.apache.hc.core5.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.hc.core5.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.hc.core5.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static uk.gov.pay.connector.agreement.model.AgreementEntity.AgreementEntityBuilder.anAgreementEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity.PaymentInstrumentEntityBuilder.aPaymentInstrumentEntity;

@ExtendWith(MockitoExtension.class)
public class LedgerServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WireMockServer ledger = new WireMockServer(wireMockConfig().dynamicPort());
    private final CloseableHttpClient client = HttpClients.createDefault();

    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;

    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledger.start();
        when(mockConnectorConfiguration.getLedgerBaseUrl()).thenReturn("http://localhost:" + ledger.port());

        ledgerService = new LedgerService(client, client, mockConnectorConfiguration);
    }

    @AfterEach
    void tearDown() throws IOException {
        ledger.stop();
        client.close();
    }

    private void ledgerAcceptsEventsWith(int status) {
        ledger.stubFor(post(urlPathEqualTo("/v1/event")).willReturn(aResponse().withStatus(status)));
    }

    private void ledgerAnswersGetWith(int status, String body) {
        ledger.stubFor(get(urlPathMatching("/v1/transaction/.*"))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", APPLICATION_JSON_VALUE).withBody(body)));
    }

    @Test
    void getRefundsFromLedgerShouldThrowExceptionForNon2xxResponse() {
        ledgerAnswersGetWith(SC_NOT_FOUND, "{}");

        assertThrows(GetRefundsForPaymentException.class, () -> ledgerService.getRefundsForPayment(152L, "external-id"));
    }

    @Test
    void getRefundsFromLedgerShouldThrowExceptionIfResponseCannotBeProcessed() {
        ledgerAnswersGetWith(SC_OK, "not json");

        assertThrows(LedgerException.class, () -> ledgerService.getRefundsForPayment(152L, "external-id"));
    }

    @Test
    void serialiseAndSendEvent() throws Exception {
        ledgerAcceptsEventsWith(SC_ACCEPTED);
        var event = new AgreementCreated("service-id", false, "resource-id", null, Instant.now());
        ledgerService.postEvent(event);
        ledger.verify(postRequestedFor(urlPathEqualTo("/v1/event"))
                .withHeader("Content-Type", equalTo(APPLICATION_JSON_VALUE))
                .withRequestBody(equalToJson(OBJECT_MAPPER.writeValueAsString(List.of(event)))));
    }

    @Test
    void sendEventShouldThrowExceptionIfResponseIsNotAccepted() {
        ledgerAcceptsEventsWith(SC_BAD_REQUEST);
        var event = new AgreementCreated("service-id", false, "resource-id", null, Instant.now());
        assertThrows(LedgerException.class, () -> ledgerService.postEvent(event));
    }

    @Test
    void serialiseAndSendMultipleEvents() throws Exception {
        ledgerAcceptsEventsWith(SC_ACCEPTED);
        var eventOne = new AgreementCreated("service-id", false, "resource-id", null, Instant.now());
        var eventTwo = new AgreementSetUp("service-id", false, "resource-id", null, Instant.now());
        PaymentInstrumentEntity paymentInstrumentEntity = aPaymentInstrumentEntity(Instant.now())
                .withStatus(PaymentInstrumentStatus.ACTIVE)
                .build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        AgreementEntity agreementEntity = anAgreementEntity(Instant.now())
                .withReference("This is the reference")
                .withDescription("This is a description")
                .withUserIdentifier("This is the user identifier")
                .withPaymentInstrument(paymentInstrumentEntity)
                .withGatewayAccount(gatewayAccountEntity)
                .build();
        var eventThree = AgreementInactivated.from(agreementEntity, MappedAuthorisationRejectedReason.EXPIRED_CARD.name(), Instant.now());

        List<Event> list = List.of(eventOne, eventTwo, eventThree);
        ledgerService.postEvent(list);
        ledger.verify(postRequestedFor(urlPathEqualTo("/v1/event"))
                .withRequestBody(equalToJson(OBJECT_MAPPER.writeValueAsString(list))));
    }

    @Test
    void shouldReturnTransactionWhenLedgerReturnsSuccessResponse() throws Exception {
        LedgerTransaction ledgerTransaction = aValidLedgerTransaction().build();
        ledgerAnswersGetWith(SC_OK, OBJECT_MAPPER.writeValueAsString(ledgerTransaction));

        Optional<LedgerTransaction> maybeTransaction = ledgerService.getTransaction("transaction-id");
        assertThat(maybeTransaction.isPresent(), is(true));
        assertThat(maybeTransaction.get().getTransactionId(), is(ledgerTransaction.getTransactionId()));
        assertThat(maybeTransaction.get().getReference(), is(ledgerTransaction.getReference()));
    }

    @Test
    void getTransaction_shouldReturnEmptyOptionalWhenLedgerReturns404() {
        ledgerAnswersGetWith(SC_NOT_FOUND, "{}");

        assertThat(ledgerService.getTransaction("transaction-id").isEmpty(), is(true));
    }

    @Test
    void getTransaction_shouldThrowExceptionWhenLedgerReturns500() {
        ledgerAnswersGetWith(SC_INTERNAL_SERVER_ERROR, "{}");

        assertThrows(LedgerException.class, () -> ledgerService.getTransaction("transaction-id"));
    }
}
