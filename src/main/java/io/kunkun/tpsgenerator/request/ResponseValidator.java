package io.kunkun.tpsgenerator.request;

import io.kunkun.tpsgenerator.metrics.ErrorAnalyzer;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates HTTP responses against configured criteria.
 * This class allows defining validation rules for responses and checking if responses meet these rules.
 */
@Slf4j
@RequiredArgsConstructor
public class ResponseValidator {

    /**
     * The error analyzer for recording validation failures.
     */
    private final ErrorAnalyzer errorAnalyzer;

    /**
     * The list of validation rules.
     */
    private final List<ValidationRule> validationRules = new ArrayList<>();

    /**
     * Adds a validation rule that checks if the status code is in the expected range.
     *
     * @param minStatusCode the minimum acceptable status code (inclusive)
     * @param maxStatusCode the maximum acceptable status code (inclusive)
     * @return this validator for chaining
     */
    public ResponseValidator withStatusCodeRange(int minStatusCode, int maxStatusCode) {
        ValidationRule rule = new ValidationRule(
                ValidationRuleType.STATUS_CODE_RANGE,
                String.format("Status code must be between %d and %d", minStatusCode, maxStatusCode),
                response -> {
                    int statusCode = response.statusCode();
                    return statusCode >= minStatusCode && statusCode <= maxStatusCode;
                }
        );
        validationRules.add(rule);
        return this;
    }

    /**
     * Adds a validation rule that checks if the response body contains the expected text.
     *
     * @param expectedText the text that should be present in the response body
     * @return this validator for chaining
     */
    public ResponseValidator withBodyContaining(String expectedText) {
        ValidationRule rule = new ValidationRule(
                ValidationRuleType.BODY_CONTAINS,
                String.format("Response body must contain '%s'", expectedText),
                response -> {
                    Object body = response.body();
                    if (body == null) {
                        return false;
                    }
                    return body.toString().contains(expectedText);
                }
        );
        validationRules.add(rule);
        return this;
    }

    /**
     * Adds a validation rule that checks if the response body matches the expected pattern.
     *
     * @param pattern the regex pattern that the response body should match
     * @return this validator for chaining
     */
    public ResponseValidator withBodyMatching(Pattern pattern) {
        ValidationRule rule = new ValidationRule(
                ValidationRuleType.BODY_PATTERN,
                String.format("Response body must match pattern '%s'", pattern.pattern()),
                response -> {
                    Object body = response.body();
                    if (body == null) {
                        return false;
                    }
                    return pattern.matcher(body.toString()).find();
                }
        );
        validationRules.add(rule);
        return this;
    }

    /**
     * Adds a validation rule that checks if the response header contains the expected value.
     *
     * @param headerName the name of the header
     * @param expectedValue the expected header value
     * @return this validator for chaining
     */
    public ResponseValidator withHeader(String headerName, String expectedValue) {
        ValidationRule rule = new ValidationRule(
                ValidationRuleType.HEADER_MATCH,
                String.format("Response must have header '%s' with value '%s'", headerName, expectedValue),
                response -> {
                    return response.headers()
                            .firstValue(headerName)
                            .map(value -> value.equals(expectedValue))
                            .orElse(false);
                }
        );
        validationRules.add(rule);
        return this;
    }

    /**
     * Adds a validation rule that checks if the response size is within the expected range.
     *
     * @param minBytes the minimum size in bytes
     * @param maxBytes the maximum size in bytes
     * @return this validator for chaining
     */
    public ResponseValidator withSizeRange(int minBytes, int maxBytes) {
        ValidationRule rule = new ValidationRule(
                ValidationRuleType.SIZE_RANGE,
                String.format("Response size must be between %d and %d bytes", minBytes, maxBytes),
                response -> {
                    Object body = response.body();
                    if (body == null) {
                        return minBytes == 0;
                    }
                    int size = body.toString().getBytes().length;
                    return size >= minBytes && size <= maxBytes;
                }
        );
        validationRules.add(rule);
        return this;
    }

    /**
     * Adds a custom validation rule.
     *
     * @param description the description of the rule
     * @param validator the validator function
     * @return this validator for chaining
     */
    public ResponseValidator withCustomRule(String description, ResponseValidatorFunction validator) {
        ValidationRule rule = new ValidationRule(
                ValidationRuleType.CUSTOM,
                description,
                validator
        );
        validationRules.add(rule);
        return this;
    }

    /**
     * Validates a response against all configured rules.
     *
     * @param response the HTTP response to validate
     * @return the validation result
     */
    public ValidationResult validate(HttpResponse<String> response) {
        List<ValidationFailure> failures = new ArrayList<>();

        for (ValidationRule rule : validationRules) {
            try {
                if (!rule.validator.validate(response)) {
                    ValidationFailure failure = new ValidationFailure(rule.type, rule.description);
                    failures.add(failure);
                }
            } catch (Exception e) {
                log.warn("Error validating rule '{}': {}", rule.description, e.getMessage());
                ValidationFailure failure = new ValidationFailure(
                        rule.type,
                        rule.description + " (validation error: " + e.getMessage() + ")"
                );
                failures.add(failure);
            }
        }

        boolean isValid = failures.isEmpty();

        // Record error response if validation failed
        if (!isValid) {
            errorAnalyzer.recordErrorResponse(response.statusCode(), response.body());
        }

        return new ValidationResult(isValid, failures);
    }

    /**
     * Types of validation rules.
     */
    public enum ValidationRuleType {
        STATUS_CODE_RANGE,
        BODY_CONTAINS,
        BODY_PATTERN,
        HEADER_MATCH,
        SIZE_RANGE,
        CUSTOM
    }

    /**
     * A validation rule for checking responses.
     */
    @Data
    private static class ValidationRule {
        private final ValidationRuleType type;
        private final String description;
        private final ResponseValidatorFunction validator;
    }

    /**
     * Result of validation.
     */
    @Data
    @Builder
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationFailure> failures;

        /**
         * Gets a description of all failures.
         *
         * @return a string with all failure descriptions
         */
        public String getFailureDescription() {
            if (failures.isEmpty()) {
                return "No validation failures";
            }

            StringBuilder sb = new StringBuilder("Validation failures:");
            for (ValidationFailure failure : failures) {
                sb.append("\n- ").append(failure.getDescription());
            }
            return sb.toString();
        }
    }

    /**
     * A validation failure.
     */
    @Data
    public static class ValidationFailure {
        private final ValidationRuleType type;
        private final String description;
    }

    /**
     * Functional interface for response validators.
     */
    @FunctionalInterface
    public interface ResponseValidatorFunction {
        boolean validate(HttpResponse<String> response);
    }
}