package uk.gov.pay.connector.it.contract;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.util.JwtGenerator;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
class ValidateWorldpay3dsFlexCredentialsTest {

    // ORGANISATIONAL_UNIT_ID, ISSUER and JWT_MAC_KEY can be found in the Worldpay MAI. Login, go to Integration -> 3DS Flex
    private final static String ORGANISATIONAL_UNIT_ID = "";
    private final static String ISSUER = "";
    private final static String JWT_MAC_KEY = "";
    
    private final static String DDC_URL = "https://centinelapi.cardinalcommerce.com/V1/Cruise/Collect";
    private final static CloseableHttpClient client = HttpClients.createDefault();

    @AfterAll
    static void closeClient() throws IOException {
        client.close();
    }

    @Test
    void should_process_server_side_ddc_change_with_correct_values() {
        assertEquals(200, invokeDDCRequest(ORGANISATIONAL_UNIT_ID, ISSUER, JWT_MAC_KEY));
    }
    
    @Test
    void should_not_process_server_side_ddc_change_with_incorrect_org_unit_id() {
        assertEquals(400, invokeDDCRequest("xxxx", ISSUER, JWT_MAC_KEY));
    }
    
    @Test
    void should_not_process_server_side_ddc_change_with_incorrect_issuer() {
        assertEquals(400, invokeDDCRequest(ORGANISATIONAL_UNIT_ID, "xxxx", JWT_MAC_KEY));
    }
    
    @Test
    void should_not_process_server_side_ddc_change_with_incorrect_jwt_mac_key() {
        assertEquals(400, invokeDDCRequest(ORGANISATIONAL_UNIT_ID, ISSUER, "b41d22ba-260d-43f2-b771-db771a7d46ee"));
    }

    private int invokeDDCRequest(String organisationalUnitId, String issuer, String jwtMacKey){
        long expiryEpochSecond = Instant.now().plus(20, MINUTES).getEpochSecond();

        Map<String, Object> claims = Map.of(
                "jti", RandomIdGenerator.newId(),
                "iat", Instant.now().getEpochSecond(),
                "iss", issuer,
                "OrgUnitId", organisationalUnitId,
                "exp", expiryEpochSecond);

        String jwt = new JwtGenerator().createJwt(claims, jwtMacKey);

        HttpPost request = new HttpPost(DDC_URL);
        request.setEntity(new UrlEncodedFormEntity(List.of(new BasicNameValuePair("JWT", jwt))));
        try {
            return client.execute(request, response -> response.getCode());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
