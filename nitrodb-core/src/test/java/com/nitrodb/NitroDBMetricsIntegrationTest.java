package com.nitrodb;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nitrodb.metrics.MetricsSnapshot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NitroDBMetricsIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void runtimeOperationsAccumulateMetrics() {
        NitroDBImpl db = (NitroDBImpl) new NitroDBBuilder()
                .dataDir(tempDir)
                .memTableSize(128)
                .build();
        try {
            db.put(bytes("alpha"), bytes("one"));
            db.put(bytes("beta"), bytes("two"));
            db.delete(bytes("beta"));
            db.awaitBackgroundWorkForTesting();

            db.get(bytes("alpha"));
            db.get(bytes("alpha"));
        } finally {
            db.close();
        }

        MetricsSnapshot snapshot = db.metricsSnapshotForTesting();
        assertTrue(snapshot.counters().getOrDefault("writes.total", 0L) >= 3L);
        assertTrue(snapshot.counters().getOrDefault("deletes.total", 0L) >= 1L);
        assertTrue(snapshot.counters().getOrDefault("reads.total", 0L) >= 2L);
        assertTrue(snapshot.histograms().containsKey("writes.latency.us"));
        assertTrue(snapshot.histograms().containsKey("reads.latency.us"));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
