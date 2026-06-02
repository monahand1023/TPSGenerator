package io.kunkun.tpsgenerator.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kunkun.tpsgenerator.config.Constants;
import lombok.Data;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template for generating HTTP requests.
 * This class defines the structure of requests that will be sent during the test.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestTemplate {

    /**
     * Compiled pattern matching ${paramName} placeholders (single-pass substitution).
     */
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * The name of the template.
     */
    private String name;

    /**
     * The weight of this template when selecting randomly from multiple templates.
     */
    private int weight = 1;

    /**
     * The HTTP method (GET, POST, PUT, DELETE, etc.).
     */
    private String method;

    /**
     * The URL template with optional parameter placeholders.
     * Example: "http://example.com/users/${userId}"
     */
    private String urlTemplate;

    /**
     * The HTTP headers with optional parameter placeholders.
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * The request body template with optional parameter placeholders.
     * Example: "{\"name\":\"${name}\",\"age\":${age}}"
     */
    private String bodyTemplate;

    /**
     * Generates an HTTP request from this template with the given parameters.
     *
     * @param parameters the parameter values to substitute
     * @return the HTTP request
     */
    public HttpRequest generate(Map<String, String> parameters) {
        // Replace parameters in URL; percent-encode any remaining ${...} placeholders
        // so that URI.create() does not throw on illegal characters ({ and }).
        String url = encodeUnresolvedPlaceholders(replaceParameters(urlTemplate, parameters));

        // Build request. A per-request timeout makes the JDK client cancel the exchange and
        // release the connection on timeout (CompletableFuture.orTimeout does NOT — it just
        // completes the future while the socket leaks in the background).
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Constants.DEFAULT_REQUEST_TIMEOUT_SECONDS));

        // Set method and body if present
        String body = bodyTemplate != null ? replaceParameters(bodyTemplate, parameters) : "";

        switch (method.toUpperCase()) {
            case "GET":
                requestBuilder.GET();
                break;

            case "POST":
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
                break;

            case "PUT":
                requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body));
                break;

            case "DELETE":
                requestBuilder.DELETE();
                break;

            default:
                requestBuilder.method(method,
                        body.isEmpty()
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(body));
        }

        // Add headers
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String headerValue = replaceParameters(entry.getValue(), parameters);
            requestBuilder.header(entry.getKey(), headerValue);
        }

        return requestBuilder.build();
    }

    /**
     * Percent-encodes any remaining {@code ${...}} placeholders in a URL string so that
     * {@link URI#create(String)} does not throw on the illegal characters {@code {} and {@code }}.
     * {@code $} → {@code %24}, {@code {} → {@code %7B}, {@code }} → {@code %7D}.
     *
     * @param url the URL that may contain unresolved placeholders
     * @return the URL with any leftover placeholders percent-encoded
     */
    private String encodeUnresolvedPlaceholders(String url) {
        if (url == null || !url.contains("${")) {
            return url;
        }
        return url.replace("$", "%24").replace("{", "%7B").replace("}", "%7D");
    }

    /**
     * Replaces parameter placeholders in a template string using single-pass regex substitution.
     * Placeholders use the format ${paramName}. Unknown placeholders are left unchanged.
     *
     * @param template the template string
     * @param parameters the parameter values
     * @return the string with parameters replaced
     */
    private String replaceParameters(String template, Map<String, String> parameters) {
        if (template == null) {
            return "";
        }

        // Fast path: a template with no placeholders (the common case for static URLs/bodies)
        // skips the regex Matcher allocation entirely.
        if (template.indexOf("${") < 0) {
            return template;
        }

        // StringBuilder (not the synchronized StringBuffer) — appendReplacement/appendTail
        // accept StringBuilder since Java 9.
        Matcher matcher = PARAM_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = parameters.getOrDefault(key, matcher.group(0)); // keep original if not found
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}