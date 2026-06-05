package com.nitrodb.metrics;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

public final class HistogramMetric {

    private final String name;
    private final Recorder recorder = new Recorder(3);

    public HistogramMetric(String name) {
        this.name = name;
    }

    public void record(long valueUs) {
        recorder.recordValue(Math.max(0L, valueUs));
    }

    public HistogramSnapshot snapshot() {
        Histogram histogram = recorder.getIntervalHistogram();
        return new HistogramSnapshot(
                histogram.getTotalCount(),
                histogram.getValueAtPercentile(50.0),
                histogram.getValueAtPercentile(95.0),
                histogram.getValueAtPercentile(99.0),
                histogram.getMaxValue());
    }

    public String name() {
        return name;
    }

    public record HistogramSnapshot(long count, long p50, long p95, long p99, long max) {
    }
}
