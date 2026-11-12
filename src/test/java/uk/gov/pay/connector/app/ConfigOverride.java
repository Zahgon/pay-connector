package uk.gov.pay.connector.app;

/**
 * A setting that a test changes in the configuration file it starts the service with: a dotted path into the YAML
 * document (such as {@code sqsConfig.endpoint}) and the value to put there.
 */
public record ConfigOverride(String key, String value) {

    public static ConfigOverride config(String key, String value) {
        return new ConfigOverride(key, value);
    }
}
