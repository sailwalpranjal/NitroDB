package com.nitrodb.mvcc;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Snapshot implements AutoCloseable {

    private final long sequenceNumber;
    private final long createdAt;
    private final Runnable onClose;
    private final AtomicBoolean closed;

    public Snapshot(long sequenceNumber) {
        this(sequenceNumber, System.currentTimeMillis(), () -> { });
    }

    public Snapshot(long sequenceNumber, long createdAt, Runnable onClose) {
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be >= 0");
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt must be >= 0");
        }
        this.sequenceNumber = sequenceNumber;
        this.createdAt = createdAt;
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        this.closed = new AtomicBoolean();
    }

    public long sequenceNumber() {
        return sequenceNumber;
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            onClose.run();
        }
    }
}
