package io.kunkun.tpsgenerator.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Template for generating HTTP requests.
 * This class defines the structure of requests that will be sent during the test.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestTemplate {

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
        // Replace parameters in URL
        String url = replaceParameters(urlTemplate, parameters);

        // Build request
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url));

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
     * Replaces parameter placeholders in a template string.
     *
     * @param template the template string
     * @param parameters the parameter values
     * @return the string with parameters replaced
     */
    private String replaceParameters(String template, Map<String, String> parameters) {
        if (template == null) {
            return "";
        }

        String result = template;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue());
        }

        return result;
    }
}