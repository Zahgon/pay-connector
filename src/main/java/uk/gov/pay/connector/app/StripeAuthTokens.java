package uk.gov.pay.connector.app;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class StripeAuthTokens {

    @Valid
    @NotNull
    private String test;

    @Valid
    @NotNull
    private String live;

    public String getTest() {
        return test;
    }

    public String getLive() {
        return live;
    }
}
