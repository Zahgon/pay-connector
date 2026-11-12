package uk.gov.pay.connector.tasks.resource;

import uk.gov.pay.connector.rules.ResourceTestExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.rules.ResourceTestResponse;
import uk.gov.pay.connector.rules.ResourceTestEntity;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import uk.gov.pay.connector.tasks.service.ParityCheckerService;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

class ParityCheckerResourceTest {
    
    private static final ParityCheckerService parityCheckerService = mock(ParityCheckerService.class);

    @RegisterExtension
    public static final ResourceTestExtension resources = ResourceTestExtension.withResources(new ParityCheckerResource(parityCheckerService));

    @Test
    void parityCheckCharge() {
        ResourceTestResponse response = resources
                .target("/v1/tasks/parity-checker")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("do_not_reprocess_valid_records", true)
                .queryParam("do_not_retry_emit_until", 1L)
                .queryParam("record_type", "CHARGE")
                .request()
                .post(ResourceTestEntity.json(""));

        assertThat(response.getStatus(), is(HttpStatus.OK.value()));
    }

    @Test
    void parityCheckRefunds() {
        ResourceTestResponse response = resources
                .target("/v1/tasks/parity-checker")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("do_not_reprocess_valid_records", true)
                .queryParam("do_not_retry_emit_until", 1L)
                .queryParam("record_type", "REFUND")
                .request()
                .post(ResourceTestEntity.json(""));

        assertThat(response.getStatus(), is(HttpStatus.OK.value()));
    }

    @Test
    void parityCheckRefundsWorksWithNoRecordType() {
        ResourceTestResponse response = resources
                .target("/v1/tasks/parity-checker")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("do_not_reprocess_valid_records", true)
                .queryParam("do_not_retry_emit_until", 1L)
                .request()
                .post(ResourceTestEntity.json(""));

        assertThat(response.getStatus(), is(HttpStatus.OK.value()));
    }


    @Test
    void parityCheckRefundsFailsWithNonStandardRecordType() {
        ResourceTestResponse response = resources
                .target("/v1/tasks/parity-checker")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("do_not_reprocess_valid_records", true)
                .queryParam("do_not_retry_emit_until", 1L)
                .queryParam("record_type", "SOMETHING_ELSE")
                .request()
                .post(ResourceTestEntity.json(""));

        assertThat(response.getStatus(), is(HttpStatus.NOT_FOUND.value()));
    }

}
