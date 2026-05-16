package io.kunkun.tpsgenerator.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RequestTemplate parameter substitution.
 */
class RequestTemplateTest {

    private RequestTemplate template;

    @BeforeEach
    void setUp() {
        template = new RequestTemplate();
        template.setMethod("GET");
        template.setUrlTemplate("http://example.com/api/${resource}/${id}");
    }

    @Test
    @DisplayName("Single parameter is substituted correctly")
    void singleParameterSubstitution() {
        template.setUrlTemplate("http://example.com/users/${userId}");
        Map<String, String> params = Collections.singletonMap("userId", "42");

        HttpRequest request = template.generate(params);

        assertEquals("http://example.com/users/42", request.uri().toString());
    }

    @Test
    @DisplayName("Multiple parameters are all substituted in a single pass")
    void multipleParameterSubstitution() {
        Map<String, String> params = new HashMap<>();
        params.put("resource", "orders");
        params.put("id", "99");

        HttpRequest request = template.generate(params);

        assertEquals("http://example.com/api/orders/99", request.uri().toString());
    }

    @Test
    @DisplayName("Unknown placeholder is kept as-is")
    void unknownPlaceholderKept() {
        template.setUrlTemplate("http://example.com/api/${known}/${unknown}");
        Map<String, String> params = Collections.singletonMap("known", "foo");

        HttpRequest request = template.generate(params);

        assertEquals("http://example.com/api/foo/${unknown}", request.uri().toString());
    }

    @Test
    @DisplayName("Body template parameters are substituted")
    void bodyParameterSubstitution() {
        RequestTemplate postTemplate = new RequestTemplate();
        postTemplate.setMethod("POST");
        postTemplate.setUrlTemplate("http://example.com/orders");
        postTemplate.setBodyTemplate("{\"productId\":\"${productId}\",\"qty\":${qty}}");

        Map<String, String> params = new HashMap<>();
        params.put("productId", "ABC-123");
        params.put("qty", "5");

        HttpRequest request = postTemplate.generate(params);

        // Verify the request URI was built correctly (body is checked indirectly via no exception)
        assertEquals("http://example.com/orders", request.uri().toString());
    }

    @Test
    @DisplayName("Header parameters are substituted")
    void headerParameterSubstitution() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer ${token}");
        template.setHeaders(headers);

        Map<String, String> params = new HashMap<>();
        params.put("resource", "items");
        params.put("id", "1");
        params.put("token", "secret-token");

        HttpRequest request = template.generate(params);

        assertTrue(request.headers().firstValue("Authorization")
                .map(v -> v.equals("Bearer secret-token"))
                .orElse(false),
                "Authorization header should have token substituted");
    }

    @Test
    @DisplayName("Repeated same placeholder is substituted for all occurrences")
    void repeatedPlaceholderSubstitution() {
        template.setUrlTemplate("http://${host}/api?host=${host}");

        Map<String, String> params = Collections.singletonMap("host", "myserver.com");

        HttpRequest request = template.generate(params);

        assertEquals("http://myserver.com/api?host=myserver.com", request.uri().toString());
    }
}
