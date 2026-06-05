package com.nitrodb.metrics;

public final class DefaultMetricsSink {

    private DefaultMetricsSink() {
    }

    public static MetricsSink create() {
        return NoOpMetricsSink.INSTANCE;
    }
}
