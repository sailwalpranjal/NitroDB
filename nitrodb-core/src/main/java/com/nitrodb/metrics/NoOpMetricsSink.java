package com.nitrodb.metrics;

public final class NoOpMetricsSink implements MetricsSink {

    public static final NoOpMetricsSink INSTANCE = new NoOpMetricsSink();

    private NoOpMetricsSink() {
    }

    @Override
    public void publish(MetricsSnapshot snapshot) {
        // Intentionally empty.
    }
}
