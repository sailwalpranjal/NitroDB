package com.nitrodb.mvcc;

import java.util.concurrent.atomic.AtomicLong;

public final class SequenceGenerator {

    private final AtomicLong sequence;

    public SequenceGenerator() {
        this(0L);
    }

    public SequenceGenerator(long initialValue) {
        if (initialValue < 0) {
            throw new IllegalArgumentException("initialValue must be >= 0");
        }
        this.sequence = new AtomicLong(initialValue);
    }

    public long next() {
        return sequence.incrementAndGet();
    }

    public long current() {
        return sequence.get();
    }

    public void setMinimum(long minValue) {
        if (minValue < 0) {
            throw new IllegalArgumentException("minValue must be >= 0");
        }
        sequence.updateAndGet(current -> Math.max(current, minValue));
    }
}
