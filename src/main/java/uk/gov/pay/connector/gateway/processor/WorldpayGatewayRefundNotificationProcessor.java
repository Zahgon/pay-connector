package uk.gov.pay.connector.gateway.processor;

import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import org.springframework.stereotype.Component;

import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;

@Component
public class WorldpayGatewayRefundNotificationProcessor implements GatewayRefundNotificationProcessor{

    @Override
    public boolean isRefundTransitionIllegal(RefundStatus currentStatus, RefundStatus newStatus) {
        return (currentStatus == REFUNDED && newStatus == REFUND_ERROR) || (currentStatus == REFUND_ERROR && newStatus == REFUNDED);
    }

}
