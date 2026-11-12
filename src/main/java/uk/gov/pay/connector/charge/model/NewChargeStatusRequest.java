package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;


public class NewChargeStatusRequest {
    @NotEmpty
    @Schema(example = "ENTERING CARD DETAILS", description = "Only `ENTERING CARD DETAILS` is allowed")
    private final String newStatus;

    NewChargeStatusRequest(@JsonProperty("new_status") String newStatus) {
        this.newStatus = newStatus;
    }

    @JsonProperty("new_status")
    public String getNewStatus() {
        return newStatus;
    }

    @AssertTrue(message = "invalid new status")
    @JsonIgnore
    public boolean isValidNewStatus() {
        return ChargeStatus.ENTERING_CARD_DETAILS.toString().equals(newStatus);
    }
}
