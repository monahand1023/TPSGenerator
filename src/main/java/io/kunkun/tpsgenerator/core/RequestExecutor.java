package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.request.RequestGenerationException;
import io.kunkun.tpsgenerator.request.RequestGenerator;
import io.kunkun.tpsgenerator.request.ResponseValidator;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes HTTP requests and collects metrics.
 */
@Slf4j
public class RequestExecutor {

    private final HttpClient httpClient;
    private final RequestGenerator requestGenerator;
    private final RateLimiter rateLimiter;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;
    private final ResponseValidator responseValidator;

    /**
     * Creates a new RequestExecutor using the builder.
     *
     * @param builder the builder
     */
    private RequestExecutor(Builder builder) {
        this.httpClient = Objects.requireNonNull(builder.httpClient, "httpClient is required");
        this.requestGenerator = Objects.requireNonNull(builder.requestGenerator, "requestGenerator is required");
        this.rateLimiter = Objects.requireNonNull(builder.rateLimiter, "rateLimiter is required");
        this.metricsCollector = Objects.requireNonNull(builder.metricsCollector, "metricsCollector is required");
        this.circuitBreaker = builder.circuitBreaker; // Optional, can be null
        this.responseValidator = builder.responseValidator; // Optional, can be null
    }

    /**
     * Creates a new builder for RequestExecutor.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for RequestExecutor.
     */
    public static class Builder {
        private HttpClient httpClient;
        private RequestGenerator requestGenerator;
        private RateLimiter rateLimiter;
        private MetricsCollector metricsCollector;
        private CircuitBreaker circuitBreaker;
        private ResponseValidator responseValidator;

        /**
         * Sets the HTTP client.
         *
         * @param httpClient the HTTP client
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Sets the request generator.
         *
         * @param requestGenerator the request generator
         * @return this builder
         */
        public Builder requestGenerator(RequestGenerator requestGenerator) {
            this.requestGenerator = requestGenerator;
            return this;
        }

        /**
         * Sets the rate limiter.
         *
         * @param rateLimiter the rate limiter
         * @return this builder
         */
        public Builder rateLimiter(RateLimiter rateLimiter) {
            this.rateLimiter = rateLimiter;
            return this;
        }

        /**
         * Sets the metrics collector.
         *
         * @param metricsCollector the metrics collector
         * @return this builder
         */
        public Builder metricsCollector(MetricsCollector metricsCollector) {
            this.metricsCollector = metricsCollector;
            return this;
        }

        /**
         * Sets the circuit breaker (optional).
         *
         * @param circuitBreaker the circuit breaker, or null to disable
         * @return this builder
         */
        public Builder circuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        /**
         * Sets the response validator (optional).
         *
         * @param responseValidator the response validator, or null to skip validation
         * @return this builder
         */
        public Builder responseValidator(ResponseValidator responseValidator) {
            this.responseValidator = responseValidator;
            return this;
        }

        /**
         * Builds the RequestExecutor.
         *
         * @return the RequestExecutor
         * @throws NullPointerException if required fields are not set
         */
        public RequestExecutor build() {
            return new RequestExecutor(this);
        }
    }

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
            HttpRequest request;
            try {
                request = requestGenerator.generateRequest(requestId, elapsedTimeMs);
            } catch (RequestGenerationException e) {
                log.warn("Failed to generate request {}: {}", requestId, e.getMessage());
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

                // Determine success based on status code
                boolean isSuccess = response.statusCode() >= 200 && response.statusCode() < 300;

                // Apply response validation if configured
                if (isSuccess && responseValidator != null) {
                    ResponseValidator.ValidationResult validationResult = responseValidator.validate(response);
                    if (!validationResult.isValid()) {
                        isSuccess = false;
                        log.debug("Request {} failed validation: {}", requestId, validationResult.getFailureDescription());
                    }
                }

                // Record response
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