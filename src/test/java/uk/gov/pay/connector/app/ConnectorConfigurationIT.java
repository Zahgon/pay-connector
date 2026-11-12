package uk.gov.pay.connector.app;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.BaseUrls;

import java.time.Duration;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;

class ConnectorConfigurationIT {

    private final ConnectorConfiguration configuration =
            ConnectorTestApplication.loadConfiguration("config/test-config.yaml", List.of());

    @Test
    public void shouldParseConfiguration() {
        JerseyClientOverrides jerseyClientOverrides = configuration.getWorldpayConfig().getJerseyClientOverrides().get();

        Duration authReadTimeout = jerseyClientOverrides.getAuth().getReadTimeout();
        assertThat(authReadTimeout, is(Duration.ofMillis(222)));

        CaptureProcessConfig captureProcessConfig = configuration.getCaptureProcessConfig();
        assertThat(captureProcessConfig.getChargesConsideredOverdueForCaptureAfter(), is(60));
        assertThat(captureProcessConfig.getMaximumRetries(), is(26));

        assertThat(configuration.getLedgerBaseUrl(), is(not(emptyString())));
        assertThat(configuration.getRestClientConfig().isDisabledSecureConnection(), is(true));
        assertThat(configuration.getEmittedEventSweepConfig().getNotEmittedEventMaxAgeInSeconds(), is(1800));
    }

    @Test
    public void shouldParseAdyenConfiguration() {
        AdyenGatewayConfig adyenGatewayConfig = configuration.getAdyenGatewayConfig();
        BaseUrls baseUrls = adyenGatewayConfig.getBaseUrls();

        assertThat(baseUrls.checkout().test(), is("https://checkout-test.adyen.com/someVersion"));
        assertThat(baseUrls.checkout().live(), is("https://checkout-live.adyen.com/someVersion"));

        assertThat(adyenGatewayConfig.getMerchantAccountIds().live(), is("adyen-live-merchant-account-id"));
        assertThat(adyenGatewayConfig.getMerchantAccountIds().test(), is("adyen-test-merchant-account-id"));
        assertThat(adyenGatewayConfig.getBalancePlatformIds().live(), is("adyen-live-balance-platform-id"));
        assertThat(adyenGatewayConfig.getBalancePlatformIds().test(), is("adyen-test-balance-platform-id"));
        assertThat(adyenGatewayConfig.getSplitConfigurationIds().test(), is("adyen-test-split-config-id"));

        assertThat(adyenGatewayConfig.getApiKeys().companyAccount().live(), is("adyen-live-company-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().companyAccount().test(), is("adyen-test-company-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().balancePlatform().live(), is("adyen-live-balance-platform-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().balancePlatform().test(), is("adyen-test-balance-platform-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().legalEntityManagement().live(), is("adyen-live-legal-entity-management-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().legalEntityManagement().test(), is("adyen-test-legal-entity-management-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().balancePlatformReport().test(), is("adyen-test-balance-platform-report-api-key"));
        assertThat(adyenGatewayConfig.getApiKeys().balancePlatformReport().live(), is("adyen-live-balance-platform-report-api-key"));

        assertThat(adyenGatewayConfig.getPaymentOrRefundGatewayFeeInPence(),is(10));
        assertThat(adyenGatewayConfig.getFraudAvoidanceFeeInPence(),is(20));
        assertThat(adyenGatewayConfig.getDisputeFeeInPence(),is(25));

        assertThat(adyenGatewayConfig.getNotificationDomain(), is(".adyen.com"));

        var paymentTestPrimary = adyenGatewayConfig.getHmacKeys().payments().test().getPrimary();
        assertThat(paymentTestPrimary.isPresent(), is(true));
        assertThat(paymentTestPrimary.get(), is("adyen-test-payments-hmac-primary"));

        var paymentLivePrimary = adyenGatewayConfig.getHmacKeys().payments().live().getPrimary();
        assertThat(paymentLivePrimary.isPresent(), is(true));
        assertThat(paymentLivePrimary.get(), is("adyen-live-payments-hmac-primary"));

        var tokenTestPrimary = adyenGatewayConfig.getHmacKeys().tokens().test().getPrimary();
        assertThat(tokenTestPrimary.isPresent(), is(true));
        assertThat(tokenTestPrimary.get(), is("adyen-test-tokens-hmac-primary"));

        var tokenLivePrimary = adyenGatewayConfig.getHmacKeys().tokens().live().getPrimary();
        assertThat(tokenLivePrimary.isPresent(), is(true));
        assertThat(tokenLivePrimary.get(), is("adyen-live-tokens-hmac-primary"));

        var transferTestPrimary = adyenGatewayConfig.getHmacKeys().transfer().test().getPrimary();
        assertThat(transferTestPrimary.isPresent(), is(true));
        assertThat(transferTestPrimary.get(), is("adyen-test-transfer-hmac-primary"));

        var transferLivePrimary = adyenGatewayConfig.getHmacKeys().transfer().live().getPrimary();
        assertThat(transferLivePrimary.isPresent(), is(true));
        assertThat(transferLivePrimary.get(), is("adyen-live-transfer-hmac-primary"));

        var balancePlatformTestKeys = adyenGatewayConfig.getHmacKeys().balancePlatformReport().test();
        assertThat(balancePlatformTestKeys.getPrimary().isPresent(), is(true));
        assertThat(balancePlatformTestKeys.getPrimary().get(), is("adyen-test-balance-platform-report-hmac-primary"));
        assertThat(balancePlatformTestKeys.getSecondary().isPresent(), is(true));
        assertThat(balancePlatformTestKeys.getSecondary().get(), is("adyen-test-balance-platform-report-hmac-secondary"));

        var balancePlatformLiveKeys = adyenGatewayConfig.getHmacKeys().balancePlatformReport().live();
        assertThat(balancePlatformLiveKeys.getPrimary().isPresent(), is(true));
        assertThat(balancePlatformLiveKeys.getPrimary().get(), is("adyen-live-balance-platform-report-hmac-primary"));
        assertThat(balancePlatformLiveKeys.getSecondary().isPresent(), is(true));
        assertThat(balancePlatformLiveKeys.getSecondary().get(), is("adyen-live-balance-platform-report-hmac-secondary"));
        
        var configurationTestKeys = adyenGatewayConfig.getHmacKeys().configuration().test();
        assertThat(configurationTestKeys.getPrimary().isPresent(), is(true));
        assertThat(configurationTestKeys.getPrimary().get(), is("adyen-test-configuration-hmac-primary"));

        var configurationLiveKeys = adyenGatewayConfig.getHmacKeys().configuration().live();
        assertThat(configurationLiveKeys.getPrimary().isPresent(), is(true));
        assertThat(configurationLiveKeys.getPrimary().get(), is("adyen-live-configuration-hmac-primary"));
    }

}
