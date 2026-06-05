package com.nitrodb.memtable;

import com.nitrodb.mvcc.VersionedKey;
import java.util.NavigableMap;
import java.util.Optional;

public final class ImmutableMemTable {

    private final NavigableMap<VersionedKey, MemTableEntry> entries;
    private final long sizeBytes;
    private final long minSeq;
    private final long maxSeq;

    public ImmutableMemTable(NavigableMap<VersionedKey, MemTableEntry> entries, long sizeBytes, long minSeq, long maxSeq) {
        this.entries = java.util.Collections.unmodifiableNavigableMap(entries);
        this.sizeBytes = sizeBytes;
        this.minSeq = minSeq;
        this.maxSeq = maxSeq;
    }

    public Optional<MemTableEntry> get(byte[] key, long maxSequence) {
        MemTableIterator iterator = new MemTableIterator(entries, key, maxSequence);
        try {
            while (iterator.hasNext()) {
                MemTableEntry entry = iterator.next();
                if (!java.util.Arrays.equals(entry.key(), key)) {
                    return Optional.empty();
                }
                return Optional.of(entry);
            }
            return Optional.empty();
        } finally {
            iterator.close();
        }
    }

    public MemTableIterator iterator() {
        return new MemTableIterator(entries, new byte[0], Long.MAX_VALUE);
    }

    public MemTableIterator iterator(byte[] startKey, long maxSequence, byte[] endKeyExclusive) {
        return new MemTableIterator(entries, startKey, maxSequence, endKeyExclusive);
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public long minSeq() {
        return minSeq;
    }

    public long maxSeq() {
        return maxSeq;
    }
}
