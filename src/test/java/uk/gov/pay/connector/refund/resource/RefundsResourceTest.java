package uk.gov.pay.connector.refund.resource;

import uk.gov.pay.connector.rules.ResourceTestExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.rules.ResourceTestResponse;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import uk.gov.pay.connector.refund.service.RefundService;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RefundsResourceTest {

    private static final RefundService mockRefundService = mock(RefundService.class);
    private static final ChargeService mockChargeService = mock(ChargeService.class);
    private static final GatewayAccountService mockGatewayAccount = mock(GatewayAccountService.class);
    private static final ChargeDao mockChargeDao = mock(ChargeDao.class);

    @RegisterExtension
    public static final ResourceTestExtension resources = ResourceTestExtension.withResources(new RefundsResource(mockRefundService, mockChargeService, mockGatewayAccount, mockChargeDao));

    @Test
    void shouldReturn200WhenRefundExists() {
        var externalRefundId = "a-refund-id";
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().build();

        when(mockRefundService.findRefundByExternalId(externalRefundId)).thenReturn(Optional.of(refundEntity));

        ResourceTestResponse response = resources
                .target("/v1/api/refunds/" + externalRefundId)
                .request()
                .get();

        assertThat(response.getStatus(), is(HttpStatus.OK.value()));
    }

    @Test
    void shouldReturn404WhenRefundDoesNotExist() {
        var externalRefundId = "a-refund-id";

        when(mockRefundService.findRefundByExternalId(externalRefundId)).thenReturn(Optional.empty());

        ResourceTestResponse response = resources
                .target("/v1/api/refunds/" + externalRefundId)
                .request()
                .get();

        assertThat(response.getStatus(), is(HttpStatus.NOT_FOUND.value()));
    }
}
