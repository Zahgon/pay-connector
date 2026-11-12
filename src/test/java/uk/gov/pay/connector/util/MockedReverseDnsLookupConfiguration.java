package uk.gov.pay.connector.util;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Replaces the service's reverse DNS lookup (used to check where gateway notifications come from) with a mock the
 * test controls.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MockedReverseDnsLookupConfiguration {

    public static final ReverseDnsLookup reverseDnsLookup = mock(ReverseDnsLookup.class);

    @Bean
    @Primary
    public ReverseDnsLookup mockedReverseDnsLookup() {
        return reverseDnsLookup;
    }
}
