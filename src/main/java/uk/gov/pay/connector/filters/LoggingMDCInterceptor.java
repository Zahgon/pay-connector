package uk.gov.pay.connector.filters;

import org.slf4j.MDC;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.util.MDCUtils;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

import static uk.gov.service.payments.logging.LoggingKeys.AGREEMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.AUTHORISATION_MODE;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER_PAYMENT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.REFUND_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.SECURE_TOKEN;

@Component
/**
 * Adds the payment, gateway account, refund, token and agreement the request names to the logging context while the
 * request is handled, and removes them when it has been answered.
 */
public class LoggingMDCInterceptor implements HandlerInterceptor {

    private final ChargeService chargeService;
    private final GatewayAccountService gatewayAccountService;

    @Inject
    public LoggingMDCInterceptor(ChargeService chargeService, GatewayAccountService gatewayAccountService) {
        this.chargeService = chargeService;
        this.gatewayAccountService = gatewayAccountService;
    }

    @Override
    public boolean preHandle(HttpServletRequest requestContext, HttpServletResponse response, Object handler) {
        var chargeEntity = getChargeFromRequest(requestContext);

        // Fields are removed from the MDC when the API responds in LoggingMDCResponseFilter
        chargeEntity.ifPresentOrElse(MDCUtils::addChargeAndGatewayAccountDetailsToMDC,
                () -> getAccountFromRequest(requestContext).ifPresent(MDCUtils::addGatewayAccountDetailsToMDC));

        getPathParameterFromRequest("refundId", requestContext)
                .ifPresent(refund -> MDC.put(REFUND_EXTERNAL_ID, refund));

        getPathParameterFromRequest("chargeTokenId", requestContext)
                .ifPresent(token -> MDC.put(SECURE_TOKEN, token));

        getPathParameterFromRequest("gatewayTransactionId", requestContext)
                .ifPresent(gatewayTxId -> MDC.put(PROVIDER_PAYMENT_ID, gatewayTxId));
        
        getPathParameterFromRequest("agreementId", requestContext)
                .ifPresent(agreementId -> MDC.put(AGREEMENT_EXTERNAL_ID, agreementId));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        List.of(PAYMENT_EXTERNAL_ID, GATEWAY_ACCOUNT_ID, PROVIDER, GATEWAY_ACCOUNT_TYPE, REFUND_EXTERNAL_ID,
                SECURE_TOKEN, PROVIDER_PAYMENT_ID, AGREEMENT_EXTERNAL_ID, AUTHORISATION_MODE).forEach(MDC::remove);
    }

    private Optional<ChargeEntity> getChargeFromRequest(HttpServletRequest requestContext) {
        try {
            return getPathParameterFromRequest("chargeId", requestContext)
                    .map(chargeService::findChargeByExternalId);
        } catch (ChargeNotFoundRuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<GatewayAccountEntity> getAccountFromRequest(HttpServletRequest requestContext) {
        return getPathParameterFromRequest("accountId", requestContext)
                .flatMap(this::safelyConvertToLong)
                .flatMap(gatewayAccountService::getGatewayAccount);
    }

    private Optional<String> getPathParameterFromRequest(String parameterName, HttpServletRequest requestContext) {
        @SuppressWarnings("unchecked")
        Map<String, String> pathParameters = (Map<String, String>) requestContext
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return Optional.ofNullable(pathParameters).map(parameters -> parameters.get(parameterName));
    }

    private Optional<Long> safelyConvertToLong(String value) {
        try {
            return Optional.of(Long.valueOf(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
