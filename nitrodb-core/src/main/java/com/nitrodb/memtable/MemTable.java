package com.nitrodb.memtable;

import com.nitrodb.mvcc.VersionedKey;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MemTable {

    private final ConcurrentSkipListMap<VersionedKey, MemTableEntry> entries = new ConcurrentSkipListMap<>();
    private final MemTableStats stats = new MemTableStats();
    private final AtomicLong minSequence = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxSequence = new AtomicLong(Long.MIN_VALUE);

    public void put(byte[] key, byte[] value, long seq) {
        putInternal(new MemTableEntry(key, value, seq, MemTableEntry.EntryType.PUT));
    }

    public void putTombstone(byte[] key, long seq) {
        putInternal(new MemTableEntry(key, null, seq, MemTableEntry.EntryType.DELETE));
    }

    public Optional<MemTableEntry> get(byte[] key, long maxSeq) {
        MemTableIterator iterator = iterator(key, maxSeq);
        try {
            while (iterator.hasNext()) {
                MemTableEntry entry = iterator.next();
                if (!Arrays.equals(entry.key(), key)) {
                    return Optional.empty();
                }
                return Optional.of(entry);
            }
            return Optional.empty();
        } finally {
            iterator.close();
        }
    }

    public MemTableIterator iterator(byte[] fromKey, long maxSeq) {
        return new MemTableIterator(entries, fromKey, maxSeq);
    }

    public MemTableIterator iterator(byte[] fromKey, long maxSeq, byte[] endKeyExclusive) {
        return new MemTableIterator(entries, fromKey, maxSeq, endKeyExclusive);
    }

    public ImmutableMemTable freeze() {
        ConcurrentSkipListMap<VersionedKey, MemTableEntry> snapshot = new ConcurrentSkipListMap<>(entries);
        long maxSeq = maxSequence.get() == Long.MIN_VALUE ? 0L : maxSequence.get();
        long minSeq = minSequence.get() == Long.MAX_VALUE ? 0L : minSequence.get();
        return new ImmutableMemTable(snapshot, stats.sizeBytes(), minSeq, maxSeq);
    }

    public long sizeBytes() {
        return stats.sizeBytes();
    }

    public long entryCount() {
        return stats.entryCount();
    }

    public ConcurrentNavigableMap<VersionedKey, MemTableEntry> rawEntries() {
        return entries;
    }

    private void putInternal(MemTableEntry entry) {
        entries.put(new VersionedKey(entry.key(), entry.sequenceNumber()), entry);
        stats.add(estimateEntrySize(entry.key(), entry.value()));
        minSequence.accumulateAndGet(entry.sequenceNumber(), Math::min);
        maxSequence.accumulateAndGet(entry.sequenceNumber(), Math::max);
    }

    private long estimateEntrySize(byte[] key, byte[] value) {
        return Long.BYTES + Integer.BYTES + key.length + Integer.BYTES + (value == null ? 0 : value.length) + 1L;
    }
}
