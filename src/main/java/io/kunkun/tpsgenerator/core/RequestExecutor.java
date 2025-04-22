package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.request.RequestGenerator;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes HTTP requests and collects metrics.
 */
@Slf4j
@RequiredArgsConstructor
public class RequestExecutor {

    private final HttpClient httpClient;
    private final RequestGenerator requestGenerator;
    private final RateLimiter rateLimiter;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    /**
     * Executes a single HTTP request.
     *
     * @param requestId the ID of the request
     * @param elapsedTimeMs the time elapsed since the start of the test
     */
    public void executeRequest(long requestId, long elapsedTimeMs) {
        try {
            // Acquire permit from rate limiter
            double waitTime = rateLimiter.acquire();

            // Record rate limiter wait time
            metricsCollector.recordRateLimiterWait(waitTime);

            // Check circuit breaker if enabled
            if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
                log.debug("Circuit breaker open, skipping request {}", requestId);
                metricsCollector.recordSkippedRequest(requestId);
                return;
            }

            // Generate request
            HttpRequest request = requestGenerator.generateRequest(requestId, elapsedTimeMs);
            if (request == null) {
                log.warn("Failed to generate request {}, skipping", requestId);
                metricsCollector.recordSkippedRequest(requestId);
                return;
            }

            // Record request start
            long startTime = System.currentTimeMillis();
            metricsCollector.recordRequestStart(requestId, request);

            // Execute request with timeout
            CompletableFuture<HttpResponse<String>> responseFuture = httpClient.sendAsync(
                    request, HttpResponse.BodyHandlers.ofString());

            // Add timeout to the request
            CompletableFuture<HttpResponse<String>> timeoutFuture = responseFuture.orTimeout(
                    30, TimeUnit.SECONDS);

            try {
                // Wait for the response
                HttpResponse<String> response = timeoutFuture.join();

                // Calculate response time
                long endTime = System.currentTimeMillis();
                long responseTime = endTime - startTime;

                // Record successful response
                boolean isSuccess = response.statusCode() >= 200 && response.statusCode() < 300;
                metricsCollector.recordResponse(requestId, response, responseTime);

                // Update circuit breaker
                if (circuitBreaker != null) {
                    circuitBreaker.recordResult(isSuccess);
                }

                if (log.isDebugEnabled()) {
                    log.debug("Request {} completed with status {}, took {} ms",
                            requestId, response.statusCode(), responseTime);
                }

            } catch (Exception e) {
                // Handle request failure
                handleRequestFailure(requestId, startTime, e);
            }

        } catch (Exception e) {
            log.error("Error executing request {}", requestId, e);
            metricsCollector.recordError(requestId, e);
        }
    }

    /**
     * Handles a request failure.
     *
     * @param requestId the ID of the failed request
     * @param startTime the start time of the request
     * @param e the exception that caused the failure
     */
    private void handleRequestFailure(long requestId, long startTime, Exception e) {
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        if (e instanceof TimeoutException) {
            log.warn("Request {} timed out after {} ms", requestId, responseTime);
            metricsCollector.recordTimeout(requestId, responseTime);
        } else {
            log.warn("Request {} failed: {}", requestId, e.getMessage());
            metricsCollector.recordError(requestId, e);
        }

        // Update circuit breaker
        if (circuitBreaker != null) {
            circuitBreaker.recordResult(false);
        }
    }
}