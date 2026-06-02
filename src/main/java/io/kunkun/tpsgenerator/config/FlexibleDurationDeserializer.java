package io.kunkun.tpsgenerator.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lenient Jackson deserializer for {@link Duration} that accepts both
 * human-friendly and ISO-8601 strings.
 *
 * <p>The README and the bundled sample configs document durations like
 * {@code "10m"}, {@code "60s"}, or {@code "1h30m"}, but Jackson's default
 * {@code JavaTimeModule} only understands ISO-8601 ({@code "PT10M"}), so the
 * documented quickstart would throw on a fresh clone. This deserializer accepts:
 *
 * <ul>
 *   <li>Human-friendly tokens: {@code ms}, {@code s}, {@code m}, {@code h},
 *       {@code d} — single ({@code "30s"}) or compound ({@code "1h30m"}).</li>
 *   <li>ISO-8601 (anything starting with {@code P}/{@code p}), delegated to
 *       {@link Duration#parse(CharSequence)} for full backward compatibility.</li>
 * </ul>
 */
public class FlexibleDurationDeserializer extends JsonDeserializer<Duration> {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*(ms|s|m|h|d)");

    @Override
    public Duration deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        String text = parser.getValueAsString();
        if (text == null) {
            return null;
        }
        text = text.trim();
        if (text.isEmpty()) {
            return Duration.ZERO;
        }

        // ISO-8601 (e.g. "PT10M", "P1DT2H") — preserve full backward compatibility.
        char first = text.charAt(0);
        if (first == 'P' || first == 'p') {
            return Duration.parse(text);
        }

        Matcher matcher = TOKEN.matcher(text.toLowerCase());
        Duration total = Duration.ZERO;
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            long value = Long.parseLong(matcher.group(1));
            total = switch (matcher.group(2)) {
                case "ms" -> total.plusMillis(value);
                case "s" -> total.plusSeconds(value);
                case "m" -> total.plusMinutes(value);
                case "h" -> total.plusHours(value);
                case "d" -> total.plusDays(value);
                default -> total;
            };
        }

        if (!matched) {
            // Not a recognised human format — defer to ISO-8601 so the caller
            // gets a clear, standard parse error instead of a silent zero.
            return Duration.parse(text);
        }
        return total;
    }
}
