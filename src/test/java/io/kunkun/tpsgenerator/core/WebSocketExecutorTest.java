package io.kunkun.tpsgenerator.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketExecutorTest {

    @Test
    @DisplayName("toWebSocketUri converts http(s) to ws(s) and passes ws(s) through")
    void convertsSchemes() {
        assertEquals("ws://host:8080/ws", WebSocketExecutor.toWebSocketUri("http://host:8080/ws").toString());
        assertEquals("wss://host/ws", WebSocketExecutor.toWebSocketUri("https://host/ws").toString());
        assertEquals("ws://host/x", WebSocketExecutor.toWebSocketUri("ws://host/x").toString());
        assertEquals("wss://host/y", WebSocketExecutor.toWebSocketUri("wss://host/y").toString());
    }

    @Test
    @DisplayName("exchange against an unreachable target returns false (no throw)")
    void exchangeToUnreachableReturnsFalse() {
        WebSocketExecutor ex = new WebSocketExecutor(HttpClient.newHttpClient(), "http://localhost:1", 2);
        assertFalse(ex.exchange("ping"));
    }
}
