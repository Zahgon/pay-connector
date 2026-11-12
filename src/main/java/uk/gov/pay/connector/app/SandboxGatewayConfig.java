package uk.gov.pay.connector.app;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class SandboxGatewayConfig {
    @Valid
    @NotNull
    private List<String> allowedCidrs;

    private String sandboxAuthToken;

    public List<String> getAllowedCidrs() {
        return allowedCidrs;
    }

    public String getSandboxAuthToken() {
        return sandboxAuthToken;
    }
}
