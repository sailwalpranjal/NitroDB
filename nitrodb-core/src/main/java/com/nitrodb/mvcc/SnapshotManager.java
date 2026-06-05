package com.nitrodb.mvcc;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class SnapshotManager {

    private final ConcurrentSkipListMap<Long, AtomicInteger> activeSnapshots = new ConcurrentSkipListMap<>();
    private final AtomicInteger activeCount = new AtomicInteger();
    private final AtomicLong oldestActive = new AtomicLong(Long.MAX_VALUE);

    public Snapshot create(long sequenceNumber) {
        activeSnapshots.compute(sequenceNumber, (ignored, count) -> {
            if (count == null) {
                count = new AtomicInteger();
            }
            count.incrementAndGet();
            return count;
        });
        activeCount.incrementAndGet();
        oldestActive.accumulateAndGet(sequenceNumber, Math::min);
        return new Snapshot(sequenceNumber, System.currentTimeMillis(), () -> releaseSequence(sequenceNumber));
    }

    public void release(Snapshot snapshot) {
        snapshot.close();
    }

    public long oldestActiveSequence() {
        return oldestActive.get();
    }

    public int activeSnapshotCount() {
        return activeCount.get();
    }

    private void releaseSequence(long sequenceNumber) {
        activeSnapshots.computeIfPresent(sequenceNumber, (ignored, count) -> {
            return count.decrementAndGet() <= 0 ? null : count;
        });
        activeCount.decrementAndGet();
        Long first = activeSnapshots.isEmpty() ? null : activeSnapshots.firstKey();
        oldestActive.set(first == null ? Long.MAX_VALUE : first);
    }
}
