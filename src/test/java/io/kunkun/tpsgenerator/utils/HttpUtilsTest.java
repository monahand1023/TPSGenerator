package io.kunkun.tpsgenerator.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpUtilsTest {

    @Test
    @DisplayName("estimateRequestSize is positive and grows with headers")
    void estimateRequestSizePositiveAndGrows() {
        HttpRequest bare = HttpRequest.newBuilder(URI.create("http://example.com/api")).GET().build();
        HttpRequest withHeader = HttpRequest.newBuilder(URI.create("http://example.com/api"))
                .header("X-Custom", "some-value").GET().build();

        long bareSize = HttpUtils.estimateRequestSize(bare);
        assertTrue(bareSize > 0, "size should account for method + URL");
        assertTrue(HttpUtils.estimateRequestSize(withHeader) > bareSize, "adding a header should grow the estimate");
    }

    @Test
    @DisplayName("createUrl appends query parameters")
    void createUrlAppendsParams() {
        String url = HttpUtils.createUrl("http://host/path", Map.of("a", "1"));
        assertTrue(url.startsWith("http://host/path?"), url);
        assertTrue(url.contains("a=1"), url);
    }

    @Test
    @DisplayName("encodeQueryParam URL-encodes special characters")
    void encodeQueryParamEncodes() {
        assertEquals("a%26b", HttpUtils.encodeQueryParam("a&b"));
    }
}
