package uk.gov.pay.connector.gateway.worldpay;

import com.codahale.metrics.MetricRegistry;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import uk.gov.pay.connector.client.HttpClientException;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.worldpay.exception.NotAWorldpayGatewayAccountException;
import uk.gov.pay.connector.gateway.worldpay.exception.ThreeDsFlexDdcServiceUnavailableException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;

import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

import static org.apache.hc.core5.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;

@Service
public class Worldpay3dsFlexCredentialsValidationService {

    private static final List<Integer> ACCEPTABLE_RESPONSE_CODES_FROM_3DS_FLEX_URL = List.of(SC_OK, SC_BAD_REQUEST);

    private static final ContentType FORM = ContentType.create("application/x-www-form-urlencoded");

    private final CloseableHttpClient client;
    private final Worldpay3dsFlexJwtService worldpay3dsFlexJwtService;
    private final Map<String, String> threeDsFlexDdcUrls;

    @Inject
    public Worldpay3dsFlexCredentialsValidationService(ClientFactory clientFactory,
                                                       MetricRegistry metricRegistry,
                                                       Worldpay3dsFlexJwtService worldpay3dsFlexJwtService,
                                                       ConnectorConfiguration connectorConfiguration) {
        this.worldpay3dsFlexJwtService = worldpay3dsFlexJwtService;
        client = clientFactory.createHttpClient(WORLDPAY, metricRegistry);
        threeDsFlexDdcUrls = connectorConfiguration.getWorldpayConfig().getThreeDsFlexDdcUrls();
    }

    public boolean validateCredentials(GatewayAccountEntity gatewayAccountEntity, Worldpay3dsFlexCredentials flexCredentials) {
        // validation of 3ds flex creds for a non-worldpay gateway account is allowed in the context of a service switching to worldpay 
        if (!gatewayAccountEntity.getGatewayName().equals(WORLDPAY.getName())  
                && !gatewayAccountEntity.hasPendingWorldpayCredential()) {
            throw new NotAWorldpayGatewayAccountException(gatewayAccountEntity.getId());
        }
        
        String ddcToken = worldpay3dsFlexJwtService.generateDdcToken(GatewayAccount.valueOf(gatewayAccountEntity),
                flexCredentials, Instant.now(), WORLDPAY.getName());

        byte[] formData = ("JWT=" + URLEncoder.encode(ddcToken, UTF_8)).getBytes(UTF_8);

        String url = threeDsFlexDdcUrls.get(gatewayAccountEntity.getType());

        HttpPost request = new HttpPost(url);
        request.setEntity(new ByteArrayEntity(formData, FORM, true));
        int status;
        try {
            status = client.execute(request, response -> response.getCode());
        } catch (IOException e) {
            throw new ThreeDsFlexDdcServiceUnavailableException(new HttpClientException(e));
        }

        if (!ACCEPTABLE_RESPONSE_CODES_FROM_3DS_FLEX_URL.contains(status)) {
            throw new ThreeDsFlexDdcServiceUnavailableException();
        }

        return status == SC_OK;
    }
}
