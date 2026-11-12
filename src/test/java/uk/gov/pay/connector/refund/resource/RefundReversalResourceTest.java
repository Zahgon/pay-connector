package uk.gov.pay.connector.refund.resource;

import uk.gov.pay.connector.rules.ResourceTestExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.rules.ResourceTestResponse;
import uk.gov.pay.connector.rules.ResourceTestEntity;
import org.springframework.http.HttpStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundReversalService;


import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

class RefundReversalResourceTest {
    private static final ChargeService mockChargeService = mock(ChargeService.class);
    private static final GatewayAccountDao mockGatewayAccountDao = mock(GatewayAccountDao.class);
    private static final RefundReversalService mockRefundReversalService = mock(RefundReversalService.class);

    Long gatewayAccountId = 1234L;
    String externalChargeId = "a-charge-id";
    String refundExternalId = "a-refund-id";

    @RegisterExtension
    public static final ResourceTestExtension resources = ResourceTestExtension.withResources(new RefundReversalResource(mockChargeService, mockGatewayAccountDao, mockRefundReversalService));
    

    @Test
    void shouldReturn400ErrorWhenPaymentProviderIsNotStripe() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withId(gatewayAccountId).build();
        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(gatewayAccountEntity));

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.WORLDPAY.getName())
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withExternalId("a-charge-id")
                .build();
        Charge charge = Charge.from(chargeEntity);
        when(mockChargeService.findCharge("a-charge-id")).thenReturn(Optional.of(charge));

        ResourceTestResponse response = resources
                .target("/v1/api/accounts/123/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(ResourceTestEntity.json(""));

        String responseBody = response.readEntity(String.class);

        assertThat(response.getStatus(), is(HttpStatus.BAD_REQUEST.value()));
        assertThat(responseBody, is("Operation only available for Stripe"));
    }

    @Test
    void shouldReturn404WhenRefundDoesNotExist() {
        var wrongRefundExternalId = "a-wrong-refund";
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withId(gatewayAccountId).build();
        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(gatewayAccountEntity));
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withExternalId(externalChargeId)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        Charge charge = Charge.from(chargeEntity);
        when(mockChargeService.findCharge(externalChargeId)).thenReturn(Optional.of(charge));

        when(mockRefundReversalService.findMaybeHistoricRefundByRefundId(wrongRefundExternalId)).thenReturn(Optional.empty());

        ResourceTestResponse response = resources
                .target("/v1/api/accounts/1234/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(ResourceTestEntity.json(Map.of("zendesk_ticket_id", "1223333343", "github_user_id", "Idris")));

        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(response.getStatus(), is(HttpStatus.NOT_FOUND.value()));
        assertThat(errorResponse.messages(), hasItem("Refund with id [a-refund-id] not found."));
    }

    @Test
    void shouldReturn404WhenRefundDoesNotBelongToCharge() {
        var wrongChargeId = "a-wrong-charge-id";
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withId(gatewayAccountId).build();
        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(gatewayAccountEntity));
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withExternalId(externalChargeId)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        Charge charge = Charge.from(chargeEntity);
        when(mockChargeService.findCharge(externalChargeId)).thenReturn(Optional.of(charge));

        RefundEntity refundEntity = aValidRefundEntity()
                .withChargeExternalId(wrongChargeId)
                .build();
        Refund refund = Refund.from(refundEntity);
        when(mockRefundReversalService.findMaybeHistoricRefundByRefundId(refundExternalId)).thenReturn(Optional.of(refund));


        ResourceTestResponse response = resources
                .target("/v1/api/accounts/1234/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(ResourceTestEntity.json(Map.of("zendesk_ticket_id", "1223333343", "github_user_id", "Idris")));

        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.messages(), hasItem("Refund with id [a-refund-id] not found for Charge wih id [a-charge-id] and gateway account with id [1234]."));
        assertThat(response.getStatus(), is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void shouldReturn404WhenWrongGatewayAccountIsProvided() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .build();
        Charge charge = Charge.from(chargeEntity);
        when(mockChargeService.findCharge(externalChargeId)).thenReturn(Optional.of(charge));

        ResourceTestResponse response = resources
                .target("/v1/api/accounts/0/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(ResourceTestEntity.json(Map.of("zendesk_ticket_id", "1223333343", "github_user_id", "87")));

        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(response.getStatus(), is(HttpStatus.NOT_FOUND.value()));
        assertThat(errorResponse.messages(), hasItem("Gateway Account with id [0] not found."));

    }

    @Test
    void shouldReturn404WhenChargeDoesNotBelongToGatewayAccount() {
        var wrongGatewayAccountId = 998L;

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withId(gatewayAccountId)
                .build();

        GatewayAccountEntity wrongGatewayAccountEntity = aGatewayAccountEntity()
                .withId(wrongGatewayAccountId)
                .build();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withExternalId(externalChargeId)
                .withGatewayAccountEntity(wrongGatewayAccountEntity)
                .withTransactionId(externalChargeId)
                .build();
        Charge charge = Charge.from(chargeEntity);

        when(mockGatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findCharge(externalChargeId)).thenReturn(Optional.of(charge));

        ResourceTestResponse response = resources
                .target("/v1/api/accounts/1234/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(ResourceTestEntity.json(Map.of("zendesk_ticket_id", "1223333343", "github_user_id", "87")));

        assertThat(response.getStatus(), is(HttpStatus.NOT_FOUND.value()));
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.messages(), hasItem("Refund with id [a-refund-id] not found for Charge wih id [a-charge-id] and gateway account with id [1234]."));
    }

    @Test
    void shouldReturn404WhenChargeIsNotPresent() {

        var externalChargeId = "a-charge-id";

        when(mockChargeService.findCharge(externalChargeId)).thenReturn(Optional.empty());

        ResourceTestResponse response = resources
                .target("/v1/api/accounts/129/charges/a-charge-id/refunds/a-refund-id/reverse-failed")
                .request()
                .post(ResourceTestEntity.json(Map.of("zendesk_ticket_id", "1223333343", "github_user_id", "87")));

        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);

        assertThat(response.getStatus(), is(HttpStatus.NOT_FOUND.value()));
        assertThat(errorResponse.messages(), hasItem("Charge with id [a-charge-id] not found."));
    }
}
