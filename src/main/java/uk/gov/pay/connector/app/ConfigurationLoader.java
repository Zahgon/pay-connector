package uk.gov.pay.connector.app;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import uk.gov.pay.connector.app.config.DurationDeserializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the service's settings: the YAML document {@code config/config.yaml} on the class path, in which every
 * {@code ${VARIABLE}} or {@code ${VARIABLE:-default}} is replaced by the environment variable's value (or the
 * default; a variable that is not set and has no default is left as written), bound to {@link ConnectorConfiguration}
 * and validated.
 */
public final class ConfigurationLoader {

    static final String CONFIGURATION_RESOURCE = "config/config.yaml";

    private ConfigurationLoader() {
    }

    public static ConnectorConfiguration load() {
        try (InputStream in = ConfigurationLoader.class.getClassLoader().getResourceAsStream(CONFIGURATION_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(CONFIGURATION_RESOURCE + " is missing from the class path");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ConnectorConfiguration parse(String yaml) throws IOException {
        StringSubstitutor substitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());
        substitutor.setEnableUndefinedVariableException(false);
        substitutor.setEnableSubstitutionInVariables(false);
        ConnectorConfiguration configuration = objectMapper()
                .readValue(substitutor.replace(yaml), ConnectorConfiguration.class);
        validate(configuration);
        return configuration;
    }

    private static ObjectMapper objectMapper() {
        SimpleModule durations = new SimpleModule().addDeserializer(Duration.class, new DurationDeserializer());
        return new ObjectMapper(new YAMLFactory())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule())
                .registerModule(durations)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private static void validate(ConnectorConfiguration configuration) {
        try (ValidatorFactory validatorFactory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()) {
            Set<ConstraintViolation<ConnectorConfiguration>> violations = validatorFactory.getValidator().validate(configuration);
            if (!violations.isEmpty()) {
                throw new IllegalStateException(CONFIGURATION_RESOURCE + " has an error:\n" + violations.stream()
                        .map(violation -> "  * " + violation.getPropertyPath() + " " + violation.getMessage())
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.joining("\n")));
            }
        }
    }
}
