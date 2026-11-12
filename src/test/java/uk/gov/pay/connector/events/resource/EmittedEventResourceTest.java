package uk.gov.pay.connector.events.resource;

import uk.gov.pay.connector.rules.ResourceTestExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.rules.ResourceTestResponse;
import uk.gov.pay.connector.rules.ResourceTestEntity;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.events.EmittedEventsBackfillService;
import uk.gov.pay.connector.events.HistoricalEventEmitterService;

import java.time.ZonedDateTime;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

class EmittedEventResourceTest {
    private static final EmittedEventsBackfillService emittedEventsBackfillService = mock(EmittedEventsBackfillService.class);
    private static final HistoricalEventEmitterService historicalEventEmitterService = mock(HistoricalEventEmitterService.class);

    @RegisterExtension
    public static final ResourceTestExtension resources = ResourceTestExtension.withResources(new EmittedEventResource(emittedEventsBackfillService, historicalEventEmitterService));
    
    @Test
    void shouldReturn200() {
        ResourceTestResponse response = resources
                .target("/v1/tasks/emitted-events-sweep")
                .request()
                .post(ResourceTestEntity.json(""));

        assertThat(response.getStatus(), is(HttpStatus.OK.value()));
    }

    @Test
    void shouldReturn200onHistoricEventById() {
        ResourceTestResponse response = resources
                .target("/v1/tasks/historical-event-emitter")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("record_type", "charge")
                .queryParam("do_not_retry_emit_until_duration", 1L)
                .request()
                .post(ResourceTestEntity.json(""));

        assertThat(response.getStatus(), is(HttpStatus.OK.value()));
    }

    @Test
    void shouldReturn200onHistoricEventByIdWithNoRecordType() {
        ResourceTestResponse response = resources
                .target("/v1/tasks/historical-event-emitter")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("do_not_retry_emit_until_duration", 1L)
                .request()
                .post(ResourceTestEntity.json(""));

        assertThat(response.getStatus(), is(HttpStatus.OK.value()));
    }

    @Test
    void shouldReturn400onNonStandardRecordTypes() {
        ResourceTestResponse response = resources
                .target("/v1/tasks/historical-event-emitter")
                .queryParam("start_id", 1L)
                .queryParam("max_id", 1L)
                .queryParam("record_type", "SOMETHING_ELSE")
                .queryParam("do_not_retry_emit_until_duration", 1L)
                .request()
                .post(ResourceTestEntity.json(""));

        assertThat(response.getStatus(), is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void shouldReturn200OnHistoricEventByDate() {
        ResourceTestResponse response = resources
                .target("/v1/tasks/historical-event-emitter-by-date")
                .queryParam("start_date",         ZonedDateTime.now().minusSeconds(2).toString())
                .queryParam("end_date", ZonedDateTime.now().toString())
                .queryParam("do_not_retry_emit_until_duration", 1L)
                .request()
                .post(ResourceTestEntity.json(""));

        assertThat(response.getStatus(), is(HttpStatus.OK.value()));
    }

}
