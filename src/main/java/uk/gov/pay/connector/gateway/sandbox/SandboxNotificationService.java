package uk.gov.pay.connector.gateway.sandbox;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.annotation.Nullable;
import uk.gov.pay.connector.util.IpAddressMatcher;

import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SandboxNotificationService {
    private final IpAddressMatcher ipAddressMatcher;
    private final Set<String> allowedSandboxIpAddresses;
    private final String sandboxAuthToken;

    @Inject
    public SandboxNotificationService(IpAddressMatcher ipAddressMatcher,
                                      @Named("AllowedSandboxIpAddresses") Set<String> allowedSandboxIpAddresses,
                                      @Named("sandboxAuthToken") @Nullable String sandboxAuthToken) {
        this.ipAddressMatcher = ipAddressMatcher;
        this.allowedSandboxIpAddresses = allowedSandboxIpAddresses;
        this.sandboxAuthToken = sandboxAuthToken;
    }

    public boolean handleNotificationFor(String forwardedIpAddresses, String authToken) {

        if (sandboxAuthToken != null && sandboxAuthToken.equals(authToken)) {
            return true;
        }

        if (!ipAddressMatcher.isMatch(forwardedIpAddresses, allowedSandboxIpAddresses)) {
            return false;
        }

        return true;
    }
}
