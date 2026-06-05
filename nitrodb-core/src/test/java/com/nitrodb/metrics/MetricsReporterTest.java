package com.nitrodb.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MetricsReporterTest {

    @Test
    void publishesSnapshotsToConfiguredSink() {
        NitroDBMetrics metrics = new NitroDBMetrics();
        metrics.counter("writes.total").increment();
        metrics.gauge("cache.bytes", () -> 42L);
        metrics.histogram("reads.latency.us").record(17L);

        AtomicReference<MetricsSnapshot> published = new AtomicReference<>();
        MetricsReporter reporter = new MetricsReporter(metrics, published::set);
        reporter.start();
        waitForSnapshot(published);
        reporter.stop();

        MetricsSnapshot snapshot = published.get();
        assertEquals(1L, snapshot.counters().get("writes.total"));
        assertEquals(42L, snapshot.gauges().get("cache.bytes"));
        assertFalse(snapshot.histograms().isEmpty());
    }

    private static void waitForSnapshot(AtomicReference<MetricsSnapshot> published) {
        long deadline = System.nanoTime() + 1_000_000_000L;
        while (published.get() == null && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
