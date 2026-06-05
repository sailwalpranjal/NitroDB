package com.nitrodb.memtable;

import java.util.concurrent.atomic.LongAdder;

public final class MemTableStats {

    private final LongAdder sizeBytes = new LongAdder();
    private final LongAdder entryCount = new LongAdder();

    public void add(long bytes) {
        sizeBytes.add(bytes);
        entryCount.increment();
    }

    public long sizeBytes() {
        return sizeBytes.sum();
    }

    public long entryCount() {
        return entryCount.sum();
    }
}
