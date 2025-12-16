package io.kunkun.tpsgenerator.metrics;

import java.net.http.HttpRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active HTTP requests during test execution.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class RequestTracker {

    /**
     * Information about an active request.
     */
    public static class RequestInfo {
        private final HttpRequest request;
        private final long startTime;

        public RequestInfo(HttpRequest request, long startTime) {
            this.request = request;
            this.startTime = startTime;
        }

        public HttpRequest getRequest() {
            return request;
        }

        public long getStartTime() {
            return startTime;
        }
    }

    private final Map<Long, RequestInfo> activeRequests = new ConcurrentHashMap<>();

    /**
     * Starts tracking a request.
     *
     * @param requestId the request ID
     * @param request the HTTP request
     * @return the RequestInfo for the tracked request
     */
    public RequestInfo startTracking(long requestId, HttpRequest request) {
        RequestInfo info = new RequestInfo(request, System.currentTimeMillis());
        activeRequests.put(requestId, info);
        return info;
    }

    /**
     * Stops tracking a request and returns its info.
     *
     * @param requestId the request ID
     * @return the RequestInfo, or null if not found
     */
    public RequestInfo stopTracking(long requestId) {
        return activeRequests.remove(requestId);
    }

    /**
     * Gets the number of active requests.
     *
     * @return the number of active requests
     */
    public int getActiveCount() {
        return activeRequests.size();
    }

    /**
     * Checks if a request is being tracked.
     *
     * @param requestId the request ID
     * @return true if the request is being tracked
     */
    public boolean isTracking(long requestId) {
        return activeRequests.containsKey(requestId);
    }

    /**
     * Clears all tracked requests.
     */
    public void clear() {
        activeRequests.clear();
    }
}
