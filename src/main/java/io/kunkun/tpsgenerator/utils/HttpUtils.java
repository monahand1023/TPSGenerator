package io.kunkun.tpsgenerator.utils;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility methods for HTTP operations.
 */
@Slf4j
public class HttpUtils {

    /**
     * Maximum size of request/response body to log.
     */
    private static final int MAX_BODY_LOG_SIZE = 1024;

    /**
     * Private constructor to prevent instantiation.
     */
    private HttpUtils() {
        // Utility class should not be instantiated
    }

    /**
     * Creates a URL with query parameters.
     *
     * @param baseUrl the base URL
     * @param queryParams the query parameters
     * @return the complete URL
     */
    public static String createUrl(String baseUrl, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return baseUrl;
        }

        StringBuilder urlBuilder = new StringBuilder(baseUrl);

        if (!baseUrl.contains("?")) {
            urlBuilder.append("?");
        } else if (!baseUrl.endsWith("&") && !baseUrl.endsWith("?")) {
            urlBuilder.append("&");
        }

        String queryString = queryParams.entrySet().stream()
                .map(entry -> encodeQueryParam(entry.getKey()) + "=" + encodeQueryParam(entry.getValue()))
                .collect(Collectors.joining("&"));

        urlBuilder.append(queryString);
        return urlBuilder.toString();
    }

    /**
     * URL encodes a query parameter.
     *
     * @param param the parameter to encode
     * @return the encoded parameter
     */
    public static String encodeQueryParam(String param) {
        return URLEncoder.encode(param, StandardCharsets.UTF_8);
    }

    /**
     * Estimates the size of a request in bytes.
     *
     * @param request the HTTP request
     * @return the estimated size in bytes
     */
    public static long estimateRequestSize(HttpRequest request) {
        long size = 0;

        // Method and URL
        size += request.method().length();
        URI uri = request.uri();
        size += uri.toString().length();

        // Headers
        for (Map.Entry<String, List<String>> entry : request.headers().map().entrySet()) {
            size += entry.getKey().length();
            for (String value : entry.getValue()) {
                size += value.length();
            }
        }

        // We can't access the request body directly, so this is just an estimate
        // based on the content-length header if present
        final AtomicLong contentSize = new AtomicLong(0);
        request.headers().firstValue("Content-Length")
                .ifPresent(contentLength -> contentSize.set(Long.parseLong(contentLength)));
        size += contentSize.get();

        return size;
    }

    /**
     * Logs an HTTP request.
     *
     * @param request the HTTP request
     * @param requestId the request ID
     * @param includeBody whether to include the request body in the log
     */
    public static void logRequest(HttpRequest request, long requestId, boolean includeBody) {
        if (!log.isDebugEnabled()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\nRequest [").append(requestId).append("]: ")
                .append(request.method()).append(" ")
                .append(request.uri());

        // Log headers
        request.headers().map().forEach((name, values) -> {
            values.forEach(value -> {
                sb.append("\n  ").append(name).append(": ").append(value);
            });
        });

        // Log body if available and required
        if (includeBody) {
            request.bodyPublisher().ifPresent(publisher -> {
                // We can't directly access the body content, so just indicate if it's present
                sb.append("\n  [Body present - size unknown]");
            });
        }

        log.debug(sb.toString());
    }

    /**
     * Logs an HTTP response.
     *
     * @param response the HTTP response
     * @param requestId the request ID
     * @param responseTimeMs the response time in milliseconds
     * @param includeBody whether to include the response body in the log
     */
    public static void logResponse(HttpResponse<String> response, long requestId,
                                   long responseTimeMs, boolean includeBody) {
        if (!log.isDebugEnabled()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\nResponse [").append(requestId).append("]: ")
                .append(response.statusCode())
                .append(" (").append(responseTimeMs).append(" ms)");

        // Log headers
        response.headers().map().forEach((name, values) -> {
            values.forEach(value -> {
                sb.append("\n  ").append(name).append(": ").append(value);
            });
        });

        // Log body if available and required
        if (includeBody && response.body() != null) {
            String body = response.body();
            if (body.length() > MAX_BODY_LOG_SIZE) {
                body = body.substring(0, MAX_BODY_LOG_SIZE) + "... [truncated]";
            }
            sb.append("\n  Body: ").append(body);
        }

        log.debug(sb.toString());
    }

    /**
     * Creates a form data body from parameters.
     *
     * @param formParams the form parameters
     * @return the form data body
     */
    public static String createFormDataBody(Map<String, String> formParams) {
        return formParams.entrySet().stream()
                .map(entry -> encodeQueryParam(entry.getKey()) + "=" + encodeQueryParam(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    /**
     * Gets a user-friendly status code description.
     *
     * @param statusCode the HTTP status code
     * @return the status code description
     */
    public static String getStatusCodeDescription(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 408: return "Request Timeout";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            default:
                if (statusCode >= 200 && statusCode < 300) {
                    return "Success";
                } else if (statusCode >= 300 && statusCode < 400) {
                    return "Redirection";
                } else if (statusCode >= 400 && statusCode < 500) {
                    return "Client Error";
                } else if (statusCode >= 500 && statusCode < 600) {
                    return "Server Error";
                } else {
                    return "Unknown";
                }
        }
    }
}