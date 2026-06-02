package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.request.RequestGenerationException;
import io.kunkun.tpsgenerator.request.RequestGenerator;
import io.kunkun.tpsgenerator.request.ResponseValidator;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * Executes HTTP requests and collects metrics.
 */
@Slf4j
public class RequestExecutor {

    private final HttpClient httpClient;
    private final RequestGenerator requestGenerator;
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
            // Rate limiting and warm-up pacing happen on the submission loop
            // (see ExecutionController); this method runs on a virtual thread
            // and is responsible only for issuing the request and recording the
            // outcome.

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

            // Execute request. The per-request HttpRequest.timeout() (set in RequestTemplate)
            // bounds the whole exchange and, unlike CompletableFuture.orTimeout, actually
            // cancels it and releases the connection on timeout.
            CompletableFuture<HttpResponse<String>> responseFuture = httpClient.sendAsync(
                    request, HttpResponse.BodyHandlers.ofString());

            try {
                // Wait for the response
                HttpResponse<String> response = responseFuture.join();

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

                // Record response with the validation-aware verdict so a 2xx that fails
                // validation is counted once, as a failure.
                metricsCollector.recordResponse(requestId, response, responseTime, isSuccess);

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

        // join() wraps the real cause in a CompletionException — unwrap it so a JDK
        // HttpTimeoutException is classified as a timeout, not a generic error.
        Throwable cause = (e instanceof CompletionException && e.getCause() != null)
                ? e.getCause() : e;

        if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
            log.warn("Request {} timed out after {} ms", requestId, responseTime);
            metricsCollector.recordTimeout(requestId, responseTime);
        } else {
            log.warn("Request {} failed: {}", requestId, cause.getMessage());
            Exception toRecord = (cause instanceof Exception) ? (Exception) cause : e;
            metricsCollector.recordError(requestId, toRecord);
        }

        // Update circuit breaker
        if (circuitBreaker != null) {
            circuitBreaker.recordResult(false);
        }
    }
}