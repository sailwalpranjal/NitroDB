package com.nitrodb.sstable;

import com.nitrodb.iter.InternalIterator;
import com.nitrodb.memtable.MemTableEntry;
import java.util.List;
import java.util.NoSuchElementException;

public final class SSTableIterator implements InternalIterator {

    private final SSTableReader reader;
    private final List<com.nitrodb.index.SparseIndexEntry> indexEntries;
    private int blockIndex;
    private int entryIndex;
    private long maxSequence;
    private List<MemTableEntry> currentEntries = List.of();
    private MemTableEntry next;

    public SSTableIterator(SSTableReader reader, byte[] fromKey, long maxSequence) {
        this.reader = reader;
        this.indexEntries = reader.sparseIndex().entries();
        seek(fromKey, maxSequence);
    }

    @Override
    public void seek(byte[] key, long maxSeq) {
        this.maxSequence = maxSeq;
        int foundIndex = reader.sparseIndex().findBlockIndex(key);
        this.blockIndex = Math.max(0, foundIndex);
        this.entryIndex = 0;
        this.currentEntries = List.of();
        this.next = null;
    }

    @Override
    public boolean hasNext() {
        if (next != null) {
            return true;
        }
        while (true) {
            if (entryIndex < currentEntries.size()) {
                MemTableEntry candidate = currentEntries.get(entryIndex++);
                if (candidate.sequenceNumber() <= maxSequence) {
                    next = candidate;
                    return true;
                }
                continue;
            }
            if (blockIndex >= indexEntries.size()) {
                return false;
            }
            com.nitrodb.index.SparseIndexEntry entry = indexEntries.get(blockIndex++);
            currentEntries = reader.readBlock(new BlockHandle(entry.blockOffset(), entry.blockLength())).entries();
            entryIndex = 0;
        }
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
        currentEntries = List.of();
    }
}
