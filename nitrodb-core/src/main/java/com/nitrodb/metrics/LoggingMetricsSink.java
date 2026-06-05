package com.nitrodb.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingMetricsSink implements MetricsSink {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingMetricsSink.class);

    @Override
    public void publish(MetricsSnapshot snapshot) {
        LOGGER.info(
                "NitroDB metrics counters={} gauges={} histograms={}",
                snapshot.counters(),
                snapshot.gauges(),
                snapshot.histograms());
    }
}
