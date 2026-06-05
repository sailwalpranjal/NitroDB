package com.nitrodb.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NitroDBMetrics {

    private final Map<String, CounterMetric> counters = new ConcurrentHashMap<>();
    private final Map<String, GaugeMetric> gauges = new ConcurrentHashMap<>();
    private final Map<String, HistogramMetric> histograms = new ConcurrentHashMap<>();

    public CounterMetric counter(String name) {
        return counters.computeIfAbsent(name, CounterMetric::new);
    }

    public GaugeMetric gauge(String name, java.util.function.Supplier<Long> supplier) {
        return gauges.computeIfAbsent(name, ignored -> new GaugeMetric(name, supplier));
    }

    public HistogramMetric histogram(String name) {
        return histograms.computeIfAbsent(name, HistogramMetric::new);
    }

    public Map<String, Long> counterSnapshot() {
        return counters.values().stream().collect(java.util.stream.Collectors.toMap(CounterMetric::name, CounterMetric::get));
    }

    public Map<String, Long> gaugeSnapshot() {
        return gauges.values().stream().collect(java.util.stream.Collectors.toMap(GaugeMetric::name, GaugeMetric::get));
    }

    public Map<String, HistogramMetric.HistogramSnapshot> histogramSnapshot() {
        return histograms.values().stream()
                .collect(java.util.stream.Collectors.toMap(HistogramMetric::name, HistogramMetric::snapshot));
    }

    public MetricsSnapshot snapshot() {
        return new MetricsSnapshot(counterSnapshot(), gaugeSnapshot(), histogramSnapshot());
    }
}
