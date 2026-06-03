package io.kunkun.tpsgenerator.core;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Drives a single WebSocket round-trip per call: open → send a text message → await one reply →
 * close. Used by the load engine when {@code protocol = websocket}. Returns whether the exchange
 * succeeded; never throws into the run.
 */
@Slf4j
public class WebSocketExecutor {

    private final HttpClient httpClient;
    private final URI uri;
    private final long replyTimeoutSeconds;

    public WebSocketExecutor(HttpClient httpClient, String targetServiceUrl) {
        this(httpClient, targetServiceUrl, 15);
    }

    public WebSocketExecutor(HttpClient httpClient, String targetServiceUrl, long replyTimeoutSeconds) {
        this.httpClient = httpClient;
        this.uri = toWebSocketUri(targetServiceUrl);
        this.replyTimeoutSeconds = replyTimeoutSeconds;
    }

    /** Converts an http(s) URL to ws(s); a ws(s) URL is returned unchanged. */
    static URI toWebSocketUri(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("targetServiceUrl is required for the websocket protocol");
        }
        if (url.startsWith("https://")) {
            return URI.create("wss://" + url.substring("https://".length()));
        }
        if (url.startsWith("http://")) {
            return URI.create("ws://" + url.substring("http://".length()));
        }
        return URI.create(url);
    }

    /**
     * Performs one exchange: connect, send {@code message}, wait for a single (possibly multi-frame)
     * text reply, then close.
     *
     * @return true if a reply was received; false on any connect/send/timeout error
     */
    public boolean exchange(String message) {
        CompletableFuture<String> reply = new CompletableFuture<>();
        WebSocket ws = null;
        try {
            ws = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, new WebSocket.Listener() {
                        private final StringBuilder sb = new StringBuilder();

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            sb.append(data);
                            if (last) {
                                reply.complete(sb.toString());
                            }
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            reply.completeExceptionally(error);
                        }
                    })
                    .get(replyTimeoutSeconds, TimeUnit.SECONDS);

            ws.sendText(message, true).get(replyTimeoutSeconds, TimeUnit.SECONDS);
            reply.get(replyTimeoutSeconds, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.debug("WebSocket exchange failed: {}", e.getMessage());
            return false;
        } finally {
            if (ws != null) {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                } catch (Exception ignored) {
                    // best-effort close
                }
            }
        }
    }
}
