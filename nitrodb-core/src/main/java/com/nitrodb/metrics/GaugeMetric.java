package com.nitrodb.metrics;

import java.util.function.Supplier;

public record GaugeMetric(String name, Supplier<Long> valueSupplier) {

    public long get() {
        return valueSupplier.get();
    }
}
