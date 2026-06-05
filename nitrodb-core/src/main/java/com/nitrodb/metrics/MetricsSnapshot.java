package com.nitrodb.metrics;

import java.util.Map;

public record MetricsSnapshot(
        Map<String, Long> counters,
        Map<String, Long> gauges,
        Map<String, HistogramMetric.HistogramSnapshot> histograms) {
}
