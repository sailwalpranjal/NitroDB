package com.nitrodb.api;

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanResult implements Iterable<Entry>, AutoCloseable {

    private final Iterator<Entry> iterator;
    private final Runnable onClose;
    private final AtomicBoolean iterated;
    private final AtomicBoolean closed;

    public ScanResult(Iterator<Entry> iterator) {
        this(iterator, () -> { });
    }

    public ScanResult(Iterator<Entry> iterator, Runnable onClose) {
        this.iterator = Objects.requireNonNull(iterator, "iterator");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        this.iterated = new AtomicBoolean();
        this.closed = new AtomicBoolean();
    }

    public static ScanResult empty() {
        return new ScanResult(Collections.emptyIterator());
    }

    @Override
    public Iterator<Entry> iterator() {
        if (!iterated.compareAndSet(false, true)) {
            throw new IllegalStateException("ScanResult supports a single iterator consumer");
        }
        return iterator;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            onClose.run();
        }
    }
}
