package uk.gov.pay.connector.app;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.adyen.util.HMACValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Named;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.charge.util.JwtGenerator;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClientFactory;
import uk.gov.pay.connector.gateway.stripe.StripeSdkWrapper;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionQueue;
import uk.gov.pay.connector.refund.service.DefaultRefundEntityFactory;
import uk.gov.pay.connector.refund.service.RefundEntityFactory;
import uk.gov.pay.connector.refund.service.WorldpayRefundEntityFactory;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.pay.connector.util.CidrUtils;
import uk.gov.pay.connector.util.IpAddressMatcher;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.util.ObjectMapperFactory;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.util.ReverseDnsLookup;
import uk.gov.pay.connector.web.JsonMessageConverter;
import uk.gov.pay.connector.web.TextEntities;

import java.net.URI;
import java.time.InstantSource;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.gateway.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.gateway.GatewayOperation.DELETE_STORED_PAYMENT_DETAILS;
import static uk.gov.pay.connector.gateway.GatewayOperation.QUERY;
import static uk.gov.pay.connector.gateway.GatewayOperation.REFUND;
import static uk.gov.pay.connector.gateway.GatewayOperation.VALIDATE_CREDENTIALS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;

/**
 * The beans of the connector that are built from its settings rather than found by component scanning: the JSON
 * mapper, metrics, the gateway clients for each Worldpay operation, the SQS client, the IP allow lists for
 * notifications and the factories of the third-party clients.
 */
@Configuration(proxyBeanMethods = false)
public class ConnectorServiceConfiguration {

    @Bean
    public InstantSource instantSource() {
        return InstantSource.system();
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return ObjectMapperFactory.newObjectMapper();
    }

    /**
     * Reads and writes the API's JSON entities the way the service always has (see {@link JsonMessageConverter}).
     */
    @Bean
    public JsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new JsonMessageConverter(objectMapper);
    }

    @Bean
    public TextEntities.TextMessageConverter textMessageConverter() {
        return new TextEntities.TextMessageConverter();
    }

    @Bean
    public MetricRegistry metricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    public HealthCheckRegistry healthCheckRegistry() {
        return new HealthCheckRegistry();
    }

    @Bean
    public InetAddressValidator inetAddressValidator() {
        return InetAddressValidator.getInstance();
    }

    @Bean
    public HMACValidator hmacValidator() {
        return new HMACValidator();
    }

    @Bean
    public ReverseDnsLookup reverseDnsLookup() {
        return new ReverseDnsLookup();
    }

    @Bean
    public StripeGatewayConfig stripeGatewayConfig(ConnectorConfiguration connectorConfiguration) {
        return connectorConfiguration.getStripeConfig();
    }

    @Bean
    public AdyenGatewayConfig adyenGatewayConfig(ConnectorConfiguration connectorConfiguration) {
        return connectorConfiguration.getAdyenGatewayConfig();
    }

    @Bean
    public WorldpayConfig worldpayConfig(ConnectorConfiguration connectorConfiguration) {
        return connectorConfiguration.getWorldpayConfig();
    }

    @Bean
    @Named("WorldpayGatewayUrlMap")
    public Map<String, URI> worldpayGatewayUrlMap(ConnectorConfiguration connectorConfiguration) {
        return connectorConfiguration.getGatewayConfigFor(WORLDPAY).getUrls().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, v -> URI.create(v.getValue())));
    }

    @Bean
    @Named("WorldpayCaptureGatewayClient")
    public GatewayClient worldpayCaptureGatewayClient(GatewayClientFactory gatewayClientFactory, MetricRegistry metricRegistry) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, CAPTURE, metricRegistry);
    }

    @Bean
    @Named("WorldpayRefundGatewayClient")
    public GatewayClient worldpayRefundGatewayClient(GatewayClientFactory gatewayClientFactory, MetricRegistry metricRegistry) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, REFUND, metricRegistry);
    }

    @Bean
    @Named("WorldpayAuthoriseGatewayClient")
    public GatewayClient worldpayAuthoriseGatewayClient(GatewayClientFactory gatewayClientFactory, MetricRegistry metricRegistry) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, AUTHORISE, metricRegistry);
    }

    @Bean
    @Named("WorldpayCancelGatewayClient")
    public GatewayClient worldpayCancelGatewayClient(GatewayClientFactory gatewayClientFactory, MetricRegistry metricRegistry) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, CANCEL, metricRegistry);
    }

    @Bean
    @Named("WorldpayInquiryGatewayClient")
    public GatewayClient worldpayInquiryGatewayClient(GatewayClientFactory gatewayClientFactory, MetricRegistry metricRegistry) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, QUERY, metricRegistry);
    }

    @Bean
    @Named("WorldpayDeleteTokenGatewayClient")
    public GatewayClient worldpayDeleteTokenGatewayClient(GatewayClientFactory gatewayClientFactory, MetricRegistry metricRegistry) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, DELETE_STORED_PAYMENT_DETAILS, metricRegistry);
    }

    @Bean
    @Named("WorldpayValidateCredentialsGatewayClient")
    public GatewayClient worldpayValidateCredentialsGatewayClient(GatewayClientFactory gatewayClientFactory, MetricRegistry metricRegistry) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, VALIDATE_CREDENTIALS, metricRegistry);
    }

    @Bean
    @Named("DefaultRefundEntityFactory")
    public RefundEntityFactory defaultRefundEntityFactory() {
        return new DefaultRefundEntityFactory();
    }

    @Bean
    @Named("WorldpayRefundEntityFactory")
    public RefundEntityFactory worldpayRefundEntityFactory() {
        return new WorldpayRefundEntityFactory();
    }

    @Bean
    @Named("AllowedEpdqIpAddresses")
    public Set<String> allowedEpdqIpAddresses() {
        return Set.of();
    }

    @Bean
    @Named("AllowedSandboxIpAddresses")
    public Set<String> allowedSandboxIpAddresses(ConnectorConfiguration config) {
        return CidrUtils.getIpAddresses(config.getSandboxConfig().getAllowedCidrs());
    }

    @Bean
    @Named("sandboxAuthToken")
    public String sandboxAuthToken(ConnectorConfiguration config) {
        return config.getSandboxConfig().getSandboxAuthToken();
    }

    @Bean
    @Named("AllowedStripeIpAddresses")
    public Set<String> allowedStripeIpAddresses(StripeGatewayConfig config) {
        return CidrUtils.getIpAddresses(config.getAllowedCidrs());
    }

    @Bean
    public IpAddressMatcher ipAddressMatcher(InetAddressValidator inetAddressValidator) {
        return new IpAddressMatcher(inetAddressValidator);
    }

    @Bean
    public Worldpay3dsFlexJwtService worldpay3dsFlexJwtService(ConnectorConfiguration configuration) {
        return new Worldpay3dsFlexJwtService(new JwtGenerator(), configuration);
    }

    @Bean
    public JsonObjectMapper jsonObjectMapper(ObjectMapper objectMapper) {
        return new JsonObjectMapper(objectMapper);
    }

    @Bean(destroyMethod = "close")
    @Primary
    public CloseableHttpClient restClient(ConnectorConfiguration configuration) {
        return RestClientFactory.buildClient(configuration.getRestClientConfig(), null);
    }

    @Bean(destroyMethod = "close")
    @Named("ledgerClient")
    public CloseableHttpClient ledgerClient(ConnectorConfiguration configuration) {
        return RestClientFactory.buildClient(configuration.getRestClientConfig(), configuration.getLedgerPostEventTimeout());
    }

    @Bean
    public NotifyClientFactory notifyClientFactory(ConnectorConfiguration connectorConfiguration) {
        return new NotifyClientFactory(connectorConfiguration);
    }

    @Bean
    public StripeSdkClientFactory stripeSdkClientFactory(ConnectorConfiguration connectorConfiguration) {
        return new StripeSdkClientFactory(connectorConfiguration, new StripeSdkWrapper());
    }

    @Bean
    public RandomIdGenerator randomIdGenerator() {
        return new RandomIdGenerator();
    }

    @Bean
    public StateTransitionQueue stateTransitionQueue() {
        return new StateTransitionQueue();
    }

    @Bean(destroyMethod = "close")
    public SqsClient sqsClient(ConnectorConfiguration connectorConfiguration) {
        SqsClientBuilder clientBuilder = SqsClient
                .builder()
                .httpClient(Apache5HttpClient.create());

        if (connectorConfiguration.getSqsConfig().isNonStandardServiceEndpoint()) {

            AwsBasicCredentials basicAWSCredentials = AwsBasicCredentials.create(connectorConfiguration.getSqsConfig().getAccessKey(), connectorConfiguration.getSqsConfig().getSecretKey());

            clientBuilder
                    .credentialsProvider(StaticCredentialsProvider.create(basicAWSCredentials))
                    .endpointOverride(URI.create(connectorConfiguration.getSqsConfig().getEndpoint()))
                    .region(Region.of(connectorConfiguration.getSqsConfig().getRegion()));
        } else {
            // uses AWS SDK's DefaultAWSCredentialsProviderChain to obtain credentials
            clientBuilder.region(Region.of(connectorConfiguration.getSqsConfig().getRegion()));
        }

        return clientBuilder.build();
    }

    @Bean
    public SqsQueueService sqsQueueService(SqsClient amazonSQS, ConnectorConfiguration connectorConfiguration) {
        return new SqsQueueService(
                amazonSQS,
                connectorConfiguration.getSqsConfig().getMessageMaximumWaitTimeInSeconds(),
                connectorConfiguration.getSqsConfig().getMessageMaximumBatchSize());
    }
}
