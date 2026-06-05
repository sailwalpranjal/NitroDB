package com.nitrodb.memtable;

import com.google.common.primitives.UnsignedBytes;
import com.nitrodb.iter.InternalIterator;
import com.nitrodb.mvcc.VersionedKey;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;

public final class MemTableIterator implements InternalIterator {

    private static final java.util.Comparator<byte[]> KEY_COMPARATOR = UnsignedBytes.lexicographicalComparator();

    private final NavigableMap<VersionedKey, MemTableEntry> source;
    private final byte[] endKeyExclusive;
    private Iterator<Map.Entry<VersionedKey, MemTableEntry>> iterator;
    private MemTableEntry next;
    private long maxSequence;

    public MemTableIterator(NavigableMap<VersionedKey, MemTableEntry> source, byte[] startKey, long maxSequence) {
        this(source, startKey, maxSequence, null);
    }

    public MemTableIterator(
            NavigableMap<VersionedKey, MemTableEntry> source,
            byte[] startKey,
            long maxSequence,
            byte[] endKeyExclusive) {
        this.source = source;
        this.endKeyExclusive = endKeyExclusive == null ? null : endKeyExclusive.clone();
        seek(startKey, maxSequence);
    }

    @Override
    public void seek(byte[] key, long maxSeq) {
        byte[] safeKey = key == null ? new byte[0] : key.clone();
        this.maxSequence = maxSeq;
        this.iterator = source.tailMap(new VersionedKey(safeKey, Long.MAX_VALUE), true).entrySet().iterator();
        this.next = null;
    }

    @Override
    public boolean hasNext() {
        if (next != null) {
            return true;
        }
        while (iterator.hasNext()) {
            MemTableEntry candidate = iterator.next().getValue();
            if (candidate.sequenceNumber() > maxSequence) {
                continue;
            }
            if (endKeyExclusive != null && KEY_COMPARATOR.compare(candidate.key(), endKeyExclusive) >= 0) {
                return false;
            }
            next = candidate;
            return true;
        }
        return false;
    }

    @Override
    public MemTableEntry next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        MemTableEntry current = next;
        next = null;
        return current;
    }

    @Override
    public void close() {
        next = null;
    }
}
