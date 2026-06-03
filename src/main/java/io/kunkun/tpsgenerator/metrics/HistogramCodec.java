package io.kunkun.tpsgenerator.metrics;

import org.HdrHistogram.Histogram;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.DataFormatException;

/**
 * Encodes/decodes an HdrHistogram to/from a base64 string (compressed). Used to export a run's
 * latency histogram in the JSON result so multiple independent runs can be merged exactly (merging
 * histograms is correct; averaging percentiles is not).
 */
public final class HistogramCodec {

    private HistogramCodec() {
    }

    public static String encode(Histogram histogram) {
        ByteBuffer buffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
        int length = histogram.encodeIntoCompressedByteBuffer(buffer);
        byte[] bytes = Arrays.copyOf(buffer.array(), length);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static Histogram decode(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        try {
            return Histogram.decodeFromCompressedByteBuffer(ByteBuffer.wrap(bytes), 0);
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Invalid encoded histogram", e);
        }
    }
}
