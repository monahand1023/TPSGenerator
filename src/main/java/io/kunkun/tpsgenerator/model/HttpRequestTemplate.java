package com.example.tpsgenerator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Template for HTTP requests with parameter placeholders.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HttpRequestTemplate {

    /**
     * The HTTP method (GET, POST, PUT, DELETE, etc.).
     */
    private String method;

    /**
     * The URL template with parameter placeholders.
     * Example: "http://example.com/api/users/${userId}"
     */
    private String urlTemplate;

    /**
     * The path parameters map with parameter placeholders.
     */
    private Map<String, String> pathParams = new HashMap<>();

    /**
     * The query parameters map with parameter placeholders.
     */
    private Map<String, String> queryParams = new HashMap<>();

    /**
     * The header parameters map with parameter placeholders.
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * The request body template with parameter placeholders.
     * Example: {"name":"${name}","email":"${email}"}
     */
    private String bodyTemplate;

    /**
     * The content type of the request body.
     */
    private String contentType;

    /**
     * The expected response content type.
     */
    private String acceptType;

    /**
     * The timeout in milliseconds.
     */
    private long timeoutMs = 30000;

    /**
     * Whether to follow redirects.
     */
    private boolean followRedirects = true;

    /**
     * Validates the template.
     *
     * @return true if the template is valid
     */
    public boolean isValid() {
        // Method and URL are required
        return method != null && !method.isEmpty() && urlTemplate != null && !urlTemplate.isEmpty();
    }

    /**
     * Gets the content type, defaulting to JSON if not specified.
     *
     * @return the content type
     */
    public String getContentType() {
        return contentType != null ? contentType : "application/json";
    }

    /**
     * Gets the accept type, defaulting to the content type if not specified.
     *
     * @return the accept type
     */
    public String getAcceptType() {
        return acceptType != null ? acceptType : getContentType();
    }

    /**
     * Creates a copy of this template.
     *
     * @return a copy of this template
     */
    public HttpRequestTemplate copy() {
        HttpRequestTemplate copy = new HttpRequestTemplate();
        copy.setMethod(method);
        copy.setUrlTemplate(urlTemplate);
        copy.setPathParams(new HashMap<>(pathParams));
        copy.setQueryParams(new HashMap<>(queryParams));
        copy.setHeaders(new HashMap<>(headers));
        copy.setBodyTemplate(bodyTemplate);
        copy.setContentType(contentType);
        copy.setAcceptType(acceptType);
        copy.setTimeoutMs(timeoutMs);
        copy.setFollowRedirects(followRedirects);
        return copy;
    }

    /**
     * Merges this template with another template.
     * Values from the other template will override values in this template if present.
     *
     * @param other the other template to merge with
     * @return the merged template
     */
    public HttpRequestTemplate merge(HttpRequestTemplate other) {
        if (other == null) {
            return this;
        }

        HttpRequestTemplate merged = this.copy();

        if (other.getMethod() != null) {
            merged.setMethod(other.getMethod());
        }

        if (other.getUrlTemplate() != null) {
            merged.setUrlTemplate(other.getUrlTemplate());
        }

        merged.getPathParams().putAll(other.getPathParams());
        merged.getQueryParams().putAll(other.getQueryParams());
        merged.getHeaders().putAll(other.getHeaders());

        if (other.getBodyTemplate() != null) {
            merged.setBodyTemplate(other.getBodyTemplate());
        }

        if (other.getContentType() != null) {
            merged.setContentType(other.getContentType());
        }

        if (other.getAcceptType() != null) {
            merged.setAcceptType(other.getAcceptType());
        }

        if (other.getTimeoutMs() > 0) {
            merged.setTimeoutMs(other.getTimeoutMs());
        }

        merged.setFollowRedirects(other.isFollowRedirects());

        return merged;
    }
}