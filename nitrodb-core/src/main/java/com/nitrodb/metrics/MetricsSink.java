package com.nitrodb.metrics;

public interface MetricsSink {

    void publish(MetricsSnapshot snapshot);
}
