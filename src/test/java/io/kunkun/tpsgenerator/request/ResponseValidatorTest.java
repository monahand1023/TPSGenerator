package io.kunkun.tpsgenerator.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResponseValidator.
 */
class ResponseValidatorTest {

    private ResponseValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ResponseValidator();
    }

    // -------- status code range --------

    @Test
    @DisplayName("Status code in expected range -> validation passes")
    void statusCodeInRangePasses() {
        validator.withStatusCodeRange(200, 299);
        HttpResponse<String> response = buildResponse(200, "OK", Map.of());
        ResponseValidator.ValidationResult result = validator.validate(response);
        assertTrue(result.isValid());
        assertTrue(result.getFailures().isEmpty());
    }

    @Test
    @DisplayName("Status code at lower bound of range -> passes")
    void statusCodeAtLowerBoundPasses() {
        validator.withStatusCodeRange(200, 299);
        HttpResponse<String> response = buildResponse(200, "OK", Map.of());
        assertTrue(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("Status code at upper bound of range -> passes")
    void statusCodeAtUpperBoundPasses() {
        validator.withStatusCodeRange(200, 299);
        HttpResponse<String> response = buildResponse(299, "Almost", Map.of());
        assertTrue(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("Status code outside range -> validation fails")
    void statusCodeOutsideRangeFails() {
        validator.withStatusCodeRange(200, 299);
        HttpResponse<String> response = buildResponse(404, "Not Found", Map.of());
        ResponseValidator.ValidationResult result = validator.validate(response);
        assertFalse(result.isValid());
        assertEquals(1, result.getFailures().size());
    }

    @Test
    @DisplayName("Status code below lower bound -> fails")
    void statusCodeBelowLowerBoundFails() {
        validator.withStatusCodeRange(200, 299);
        HttpResponse<String> response = buildResponse(199, "Informational", Map.of());
        assertFalse(validator.validate(response).isValid());
    }

    // -------- body contains --------

    @Test
    @DisplayName("Body contains expected text -> passes")
    void bodyContainsExpectedTextPasses() {
        validator.withBodyContaining("hello");
        HttpResponse<String> response = buildResponse(200, "hello world", Map.of());
        assertTrue(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("Body does not contain expected text -> fails")
    void bodyMissingExpectedTextFails() {
        validator.withBodyContaining("goodbye");
        HttpResponse<String> response = buildResponse(200, "hello world", Map.of());
        assertFalse(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("Null body with body-contains rule -> fails")
    void nullBodyWithContainsRuleFails() {
        validator.withBodyContaining("text");
        HttpResponse<String> response = buildResponse(200, null, Map.of());
        assertFalse(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("Empty body with body-contains rule -> fails")
    void emptyBodyWithContainsRuleFails() {
        validator.withBodyContaining("text");
        HttpResponse<String> response = buildResponse(200, "", Map.of());
        assertFalse(validator.validate(response).isValid());
    }

    // -------- body pattern --------

    @Test
    @DisplayName("Body matches regex pattern -> passes")
    void bodyMatchesPatternPasses() {
        validator.withBodyMatching(Pattern.compile("\\d{3}"));
        HttpResponse<String> response = buildResponse(200, "code 404 found", Map.of());
        assertTrue(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("Body does not match pattern -> fails")
    void bodyDoesNotMatchPatternFails() {
        validator.withBodyMatching(Pattern.compile("^\\{"));
        HttpResponse<String> response = buildResponse(200, "plain text", Map.of());
        assertFalse(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("Null body with pattern rule -> fails")
    void nullBodyWithPatternFails() {
        validator.withBodyMatching(Pattern.compile(".*"));
        HttpResponse<String> response = buildResponse(200, null, Map.of());
        assertFalse(validator.validate(response).isValid());
    }

    // -------- header match --------

    @Test
    @DisplayName("Response has expected header -> passes")
    void responseHasExpectedHeaderPasses() {
        validator.withHeader("Content-Type", "application/json");
        HttpResponse<String> response = buildResponse(200, "{}",
                Map.of("Content-Type", List.of("application/json")));
        assertTrue(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("Response missing expected header -> fails")
    void responseMissingHeaderFails() {
        validator.withHeader("X-Custom", "value");
        HttpResponse<String> response = buildResponse(200, "", Map.of());
        assertFalse(validator.validate(response).isValid());
    }

    // -------- size range --------

    @Test
    @DisplayName("Response size in range -> passes")
    void responseSizeInRangePasses() {
        validator.withSizeRange(0, 1024);
        HttpResponse<String> response = buildResponse(200, "short body", Map.of());
        assertTrue(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("Response size exceeds max -> fails")
    void responseSizeExceedsMaxFails() {
        validator.withSizeRange(0, 3);
        HttpResponse<String> response = buildResponse(200, "this is too long", Map.of());
        assertFalse(validator.validate(response).isValid());
    }

    // -------- multiple rules --------

    @Test
    @DisplayName("Multiple rules all pass")
    void multipleRulesAllPass() {
        validator.withStatusCodeRange(200, 299)
                 .withBodyContaining("ok");
        HttpResponse<String> response = buildResponse(200, "ok response", Map.of());
        assertTrue(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("Multiple rules: one fails -> overall fails")
    void multipleRulesOneFailsOverallFails() {
        validator.withStatusCodeRange(200, 299)
                 .withBodyContaining("missing");
        HttpResponse<String> response = buildResponse(200, "ok", Map.of());
        assertFalse(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("getFailureDescription returns 'No validation failures' when valid")
    void failureDescriptionWhenValid() {
        validator.withStatusCodeRange(200, 299);
        HttpResponse<String> response = buildResponse(200, "", Map.of());
        String desc = validator.validate(response).getFailureDescription();
        assertEquals("No validation failures", desc);
    }

    @Test
    @DisplayName("getFailureDescription lists failures when invalid")
    void failureDescriptionWhenInvalid() {
        validator.withStatusCodeRange(200, 299);
        HttpResponse<String> response = buildResponse(500, "", Map.of());
        String desc = validator.validate(response).getFailureDescription();
        assertTrue(desc.contains("Validation failures"), "Should mention failures: " + desc);
    }

    // -------- custom rule --------

    @Test
    @DisplayName("Custom rule: returning true passes")
    void customRulePasses() {
        validator.withCustomRule("always pass", r -> true);
        HttpResponse<String> response = buildResponse(500, "", Map.of());
        assertTrue(validator.validate(response).isValid());
    }

    @Test
    @DisplayName("Custom rule: returning false fails")
    void customRuleFails() {
        validator.withCustomRule("always fail", r -> false);
        HttpResponse<String> response = buildResponse(200, "", Map.of());
        assertFalse(validator.validate(response).isValid());
    }

    // -------- helper --------

    private HttpResponse<String> buildResponse(int statusCode, String body,
                                                Map<String, List<String>> headerMap) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return statusCode; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(headerMap, (a, b) -> true); }
            @Override public String body() { return body; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("http://example.com"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
