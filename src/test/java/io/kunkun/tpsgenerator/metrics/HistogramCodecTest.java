package io.kunkun.tpsgenerator.metrics;

import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HistogramCodecTest {

    @Test
    void roundTripPreservesData() {
        Histogram h = new Histogram(3);
        for (int i = 1; i <= 1000; i++) {
            h.recordValue(i);
        }

        Histogram decoded = HistogramCodec.decode(HistogramCodec.encode(h));

        assertEquals(h.getTotalCount(), decoded.getTotalCount());
        assertEquals(h.getValueAtPercentile(50), decoded.getValueAtPercentile(50));
        assertEquals(h.getValueAtPercentile(99), decoded.getValueAtPercentile(99));
        assertEquals(h.getMaxValue(), decoded.getMaxValue());
    }
}
