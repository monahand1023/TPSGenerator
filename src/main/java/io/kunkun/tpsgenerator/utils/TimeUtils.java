package com.example.tpsgenerator.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for time and duration operations.
 */
public class TimeUtils {

    /**
     * Pattern for parsing duration strings (e.g., "1h30m15s").
     */
    private static final Pattern DURATION_PATTERN =
            Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?(?:(\\d+)ms)?");

    /**
     * Default date-time formatter.
     */
    private static final DateTimeFormatter DEFAULT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Private constructor to prevent instantiation.
     */
    private TimeUtils() {
        // Utility class should not be instantiated
    }

    /**
     * Parses a duration string to a Duration object.
     * Supported formats: "1h30m15s", "30m", "15s", "500ms", combinations of these.
     *
     * @param durationString the duration string to parse
     * @return the parsed Duration
     * @throws IllegalArgumentException if the string cannot be parsed
     */
    public static Duration parseDuration(String durationString) {
        if (durationString == null || durationString.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration string cannot be empty");
        }

        Matcher matcher = DURATION_PATTERN.matcher(durationString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid duration format. Expected format like '1h30m15s', '30m', '15s', or '500ms'");
        }

        long hours = parseGroupAsLong(matcher, 1);
        long minutes = parseGroupAsLong(matcher, 2);
        long seconds = parseGroupAsLong(matcher, 3);
        long milliseconds = parseGroupAsLong(matcher, 4);

        return Duration.ofHours(hours)
                .plusMinutes(minutes)
                .plusSeconds(seconds)
                .plusMillis(milliseconds);
    }

    /**
     * Parses a group from a regex matcher as a long.
     *
     * @param matcher the regex matcher
     * @param group the group index
     * @return the parsed long value or 0 if the group is not present
     */
    private static long parseGroupAsLong(Matcher matcher, int group) {
        String value = matcher.group(group);
        return (value != null) ? Long.parseLong(value) : 0;
    }

    /**
     * Formats a duration as a human-readable string.
     *
     * @param duration the duration to format
     * @return the formatted duration string
     */
    public static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        long millis = duration.toMillisPart();

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append("m");
        }
        if (remainingSeconds > 0 || (hours == 0 && minutes == 0 && millis == 0)) {
            sb.append(remainingSeconds).append("s");
        }
        if (millis > 0 && hours == 0 && minutes == 0) {
            sb.append(millis).append("ms");
        }

        return sb.toString();
    }

    /**
     * Formats a timestamp in milliseconds as a date-time string.
     *
     * @param timestampMs the timestamp in milliseconds
     * @return the formatted date-time string
     */
    public static String formatTimestamp(long timestampMs) {
        return formatTimestamp(timestampMs, DEFAULT_FORMATTER);
    }

    /**
     * Formats a timestamp in milliseconds as a date-time string with the specified formatter.
     *
     * @param timestampMs the timestamp in milliseconds
     * @param formatter the date-time formatter
     * @return the formatted date-time string
     */
    public static String formatTimestamp(long timestampMs, DateTimeFormatter formatter) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault());
        return formatter.format(dateTime);
    }

    /**
     * Calculates the elapsed time from a start time to now in milliseconds.
     *
     * @param startTimeMs the start time in milliseconds
     * @return the elapsed time in milliseconds
     */
    public static long getElapsedTimeMs(long startTimeMs) {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Formats an elapsed time in milliseconds as a human-readable string.
     *
     * @param elapsedTimeMs the elapsed time in milliseconds
     * @return the formatted elapsed time string
     */
    public static String formatElapsedTime(long elapsedTimeMs) {
        return formatDuration(Duration.ofMillis(elapsedTimeMs));
    }

    /**
     * Checks if a duration has elapsed since a start time.
     *
     * @param startTimeMs the start time in milliseconds
     * @param durationMs the duration to check in milliseconds
     * @return true if the duration has elapsed
     */
    public static boolean hasDurationElapsed(long startTimeMs, long durationMs) {
        return getElapsedTimeMs(startTimeMs) >= durationMs;
    }

    /**
     * Waits for the specified duration.
     *
     * @param durationMs the duration to wait in milliseconds
     */
    public static void waitFor(long durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}