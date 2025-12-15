package io.kunkun.tpsgenerator.metrics;

import lombok.Getter;
import org.HdrHistogram.Histogram;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects metrics related to network traffic.
 */
public class NetworkMetrics {

    /**
     * Total bytes sent.
     */
    @Getter
    private final LongAdder totalBytesSent = new LongAdder();

    /**
     * Total bytes received.
     */
    @Getter
    private final LongAdder totalBytesReceived = new LongAdder();

    /**
     * Histogram of request sizes.
     */
    private final Histogram requestSizeHistogram = new Histogram(3);

    /**
     * Histogram of response sizes.
     */
    private final Histogram responseSizeHistogram = new Histogram(3);

    /**
     * Content type counts.
     */
    private final Map<String, LongAdder> contentTypeCounts = new ConcurrentHashMap<>();

    /**
     * Records a request.
     *
     * @param request the HTTP request
     * @param sizeBytes the size of the request in bytes
     */
    public void recordRequest(Object request, long sizeBytes) {
        totalBytesSent.add(sizeBytes);

        synchronized (requestSizeHistogram) {
            requestSizeHistogram.recordValue(sizeBytes);
        }
    }

    /**
     * Records a response.
     *
     * @param response the HTTP response
     * @param sizeBytes the size of the response in bytes
     */
    public void recordResponse(HttpResponse<?> response, long sizeBytes) {
        totalBytesReceived.add(sizeBytes);

        synchronized (responseSizeHistogram) {
            responseSizeHistogram.recordValue(sizeBytes);
        }

        // Record content type
        String contentType = getContentType(response);
        if (contentType != null) {
            contentTypeCounts.computeIfAbsent(contentType, k -> new LongAdder()).increment();
        }
    }

    /**
     * Gets the content type from a response.
     *
     * @param response the HTTP response
     * @return the content type or null if not present
     */
    private String getContentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse(null);
    }

    /**
     * Gets the total bytes sent.
     *
     * @return the total bytes sent
     */
    public long getTotalBytesSent() {
        return totalBytesSent.sum();
    }

    /**
     * Gets the total bytes received.
     *
     * @return the total bytes received
     */
    public long getTotalBytesReceived() {
        return totalBytesReceived.sum();
    }

    /**
     * Gets a percentile of request sizes.
     *
     * @param percentile the percentile (0-100)
     * @return the request size at the specified percentile
     */
    public long getRequestSizePercentile(double percentile) {
        synchronized (requestSizeHistogram) {
            return requestSizeHistogram.getValueAtPercentile(percentile);
        }
    }

    /**
     * Gets a percentile of response sizes.
     *
     * @param percentile the percentile (0-100)
     * @return the response size at the specified percentile
     */
    public long getResponseSizePercentile(double percentile) {
        synchronized (responseSizeHistogram) {
            return responseSizeHistogram.getValueAtPercentile(percentile);
        }
    }

    /**
     * Gets the content type counts.
     *
     * @return a map of content type to count
     */
    public Map<String, Long> getContentTypeCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();

        for (Map.Entry<String, LongAdder> entry : contentTypeCounts.entrySet()) {
            result.put(entry.getKey(), entry.getValue().sum());
        }

        return result;
    }

    /**
     * Gets the total network traffic (sent + received) in bytes.
     *
     * @return the total network traffic
     */
    public long getTotalTraffic() {
        return getTotalBytesSent() + getTotalBytesReceived();
    }

    /**
     * Gets the total network traffic in megabytes.
     *
     * @return the total network traffic in MB
     */
    public double getTotalTrafficMB() {
        return getTotalTraffic() / (1024.0 * 1024.0);
    }

    /**
     * Estimates the size of an HTTP response in bytes.
     * This includes the body size and header sizes.
     *
     * @param response the HTTP response
     * @return the estimated size in bytes
     */
    public static long estimateResponseSize(HttpResponse<String> response) {
        long size = 0;

        // Add body size
        String body = response.body();
        if (body != null) {
            size += body.getBytes().length;
        }

        // Add headers size (key + values)
        for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
            size += entry.getKey().getBytes().length;
            for (String value : entry.getValue()) {
                size += value.getBytes().length;
            }
        }

        return size;
    }
}