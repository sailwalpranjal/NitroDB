package com.nitrodb.metrics;

import java.util.concurrent.atomic.LongAdder;

public final class CounterMetric {

    private final String name;
    private final LongAdder value = new LongAdder();

    public CounterMetric(String name) {
        this.name = name;
    }

    public void increment() {
        value.increment();
    }

    public void increment(long delta) {
        value.add(delta);
    }

    public long get() {
        return value.sum();
    }

    public String name() {
        return name;
    }
}
