package uk.gov.pay.connector.expunge.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import uk.gov.pay.connector.expunge.service.ExpungeService;

import jakarta.inject.Inject;
import java.util.UUID;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import uk.gov.pay.connector.web.QueryParam;
import uk.gov.pay.connector.web.MediaTypes;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY;
import static uk.gov.service.payments.logging.LoggingKeys.MDC_REQUEST_ID_KEY;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Tasks")
@RestController
public class ExpungeResource {

    private ExpungeService expungeService;

    @Inject
    public ExpungeResource(ExpungeService expungeService) {
        this.expungeService = expungeService;
    }

    @PostMapping("/v1/tasks/expunge")
    @MediaTypes(produces = APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Expunge charges and refunds in terminal state",
            description = "Task to expunge charges and refunds on terminal or expungeable state from connector.<br>" +
                    "This task checks parity of charge/refund with ledger transaction and expunges only if the fields matches. If parity check fails, new events are emitted for charge/refunds and the record is marked with latest parity check status.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<?> expunge(
            @Parameter(in = QUERY, example = "100",
                    description = "Number of charges to expunge. Defaults to EXPUNGE_NO_OF_CHARGES_PER_TASK_RUN environment variable or configuration default")
            @QueryParam("number_of_charges_to_expunge") Integer noOfChargesToExpunge,
            @Parameter(in = QUERY, example = "100",
                    description = "Number of refunds to expunge. Defaults to EXPUNGE_NO_OF_REFUNDS_PER_TASK_RUN environment variable or configuration default")
            @QueryParam("number_of_refunds_to_expunge") Integer noOfRefundsToExpunge) {
        String correlationId = MDC.get(MDC_REQUEST_ID_KEY) == null ? "ExpungeResource-" + UUID.randomUUID() : MDC.get(MDC_REQUEST_ID_KEY);
        MDC.put(MDC_REQUEST_ID_KEY, correlationId);
        expungeService.expunge(noOfChargesToExpunge, noOfRefundsToExpunge);
        MDC.remove(MDC_REQUEST_ID_KEY);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
