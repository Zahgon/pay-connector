package uk.gov.pay.connector.app.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * Reads durations the way the configuration has always written them: a whole number and a unit, such as
 * {@code 25500ms}, {@code 1h}, {@code 60s} or {@code 1 minute}.
 */
public class DurationDeserializer extends StdScalarDeserializer<Duration> {

    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*(\\S+)");
    private static final Map<String, ChronoUnit> UNITS = Map.ofEntries(
            entry("ns", ChronoUnit.NANOS), entry("nanosecond", ChronoUnit.NANOS), entry("nanoseconds", ChronoUnit.NANOS),
            entry("us", ChronoUnit.MICROS), entry("microsecond", ChronoUnit.MICROS), entry("microseconds", ChronoUnit.MICROS),
            entry("ms", ChronoUnit.MILLIS), entry("millisecond", ChronoUnit.MILLIS), entry("milliseconds", ChronoUnit.MILLIS),
            entry("s", ChronoUnit.SECONDS), entry("second", ChronoUnit.SECONDS), entry("seconds", ChronoUnit.SECONDS),
            entry("m", ChronoUnit.MINUTES), entry("min", ChronoUnit.MINUTES), entry("mins", ChronoUnit.MINUTES),
            entry("minute", ChronoUnit.MINUTES), entry("minutes", ChronoUnit.MINUTES),
            entry("h", ChronoUnit.HOURS), entry("hour", ChronoUnit.HOURS), entry("hours", ChronoUnit.HOURS),
            entry("d", ChronoUnit.DAYS), entry("day", ChronoUnit.DAYS), entry("days", ChronoUnit.DAYS));

    public DurationDeserializer() {
        super(Duration.class);
    }

    @Override
    public Duration deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String text = parser.getValueAsString();
        if (text == null) {
            return (Duration) context.handleUnexpectedToken(Duration.class, parser);
        }
        return parse(text);
    }

    public static Duration parse(String text) {
        Matcher matcher = DURATION.matcher(text.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration: " + text);
        }
        ChronoUnit unit = UNITS.get(matcher.group(2).toLowerCase(Locale.ENGLISH));
        if (unit == null) {
            throw new IllegalArgumentException("Invalid duration: " + text + ". Wrong time unit");
        }
        return Duration.of(Long.parseLong(matcher.group(1)), unit);
    }
}
