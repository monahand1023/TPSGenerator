package io.kunkun.tpsgenerator.metrics;

import io.kunkun.tpsgenerator.model.ResourceSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResourceMonitor.
 */
class ResourceMonitorTest {

    @Test
    @DisplayName("start + stop: getSnapshots returns at least one snapshot")
    void startAndStopCollectsSnapshots() throws InterruptedException {
        ResourceMonitor monitor = new ResourceMonitor();
        monitor.start(Duration.ofMillis(50));

        // Let the monitor run for a short period so it can capture at least one snapshot
        Thread.sleep(200);

        monitor.stop();

        List<ResourceSnapshot> snapshots = monitor.getSnapshots();
        assertFalse(snapshots.isEmpty(), "Expected at least one snapshot after start+sleep+stop");
    }

    @Test
    @DisplayName("CPU and memory values are non-negative")
    void cpuAndMemoryAreNonNegative() throws InterruptedException {
        ResourceMonitor monitor = new ResourceMonitor();
        monitor.start(Duration.ofMillis(50));
        Thread.sleep(200);
        monitor.stop();

        List<ResourceSnapshot> snapshots = monitor.getSnapshots();
        assertFalse(snapshots.isEmpty());

        for (ResourceSnapshot snapshot : snapshots) {
            assertTrue(snapshot.getCpuPercentage() >= 0,
                    "CPU usage should be non-negative, was: " + snapshot.getCpuPercentage());
            assertTrue(snapshot.getHeapUsedBytes() >= 0,
                    "Heap bytes should be non-negative");
            assertTrue(snapshot.getNonHeapUsedBytes() >= 0,
                    "Non-heap bytes should be non-negative");
            assertTrue(snapshot.getTotalMemoryUsed() >= 0,
                    "Total memory used should be non-negative");
        }
    }

    @Test
    @DisplayName("stop: getSnapshots after stop is stable (does not grow)")
    void snapshotsStableAfterStop() throws InterruptedException {
        ResourceMonitor monitor = new ResourceMonitor();
        monitor.start(Duration.ofMillis(50));
        Thread.sleep(150);
        monitor.stop();

        int sizeAfterStop = monitor.getSnapshots().size();
        Thread.sleep(100); // wait to confirm no more snapshots are added
        int sizeAgain = monitor.getSnapshots().size();

        assertEquals(sizeAfterStop, sizeAgain, "Snapshot count should not grow after stop()");
    }

    @Test
    @DisplayName("maxCpuUsage and maxMemoryUsage are non-negative after run")
    void maxValuesAreNonNegative() throws InterruptedException {
        ResourceMonitor monitor = new ResourceMonitor();
        monitor.start(Duration.ofMillis(50));
        Thread.sleep(150);
        monitor.stop();

        assertTrue(monitor.getMaxCpuUsage() >= 0);
        assertTrue(monitor.getMaxMemoryUsage() >= 0);
    }

    @Test
    @DisplayName("snapshot timestamps are monotonically non-decreasing")
    void snapshotTimestampsAreOrdered() throws InterruptedException {
        ResourceMonitor monitor = new ResourceMonitor();
        monitor.start(Duration.ofMillis(50));
        Thread.sleep(250);
        monitor.stop();

        List<ResourceSnapshot> snapshots = monitor.getSnapshots();
        for (int i = 1; i < snapshots.size(); i++) {
            assertTrue(snapshots.get(i).getTimestampMs() >= snapshots.get(i - 1).getTimestampMs(),
                    "Timestamps should be non-decreasing");
        }
    }

    @Test
    @DisplayName("close() delegates to stop() without throwing")
    void closeDoesNotThrow() throws InterruptedException {
        ResourceMonitor monitor = new ResourceMonitor();
        monitor.start(Duration.ofMillis(50));
        Thread.sleep(50);
        assertDoesNotThrow(monitor::close);
    }
}
