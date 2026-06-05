package com.nitrodb.sstable;

import com.nitrodb.memtable.MemTableEntry;
import com.nitrodb.serialization.RecordEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class BlockBuilder {

    private final int targetSizeBytes;
    private final RecordEncoder recordEncoder = new RecordEncoder();
    private final List<MemTableEntry> entries = new ArrayList<>();
    private int currentSize = Integer.BYTES;

    public BlockBuilder(int targetSizeBytes) {
        this.targetSizeBytes = Math.min(targetSizeBytes, SSTableConstants.MAX_BLOCK_SIZE);
    }

    public void add(byte[] key, byte[] value, long seq, MemTableEntry.EntryType type) {
        MemTableEntry entry = new MemTableEntry(key, value, seq, type);
        entries.add(entry);
        currentSize += recordEncoder.encodedSize(entry);
    }

    public boolean isFull() {
        return currentSize >= targetSizeBytes;
    }

    public boolean hasEntries() {
        return !entries.isEmpty();
    }

    public byte[] finish() {
        ByteBuffer buffer = ByteBuffer.allocate(currentSize);
        buffer.putInt(entries.size());
        for (MemTableEntry entry : entries) {
            recordEncoder.encode(entry, buffer);
        }
        return buffer.array();
    }

    public byte[] firstKey() {
        return entries.isEmpty() ? new byte[0] : entries.get(0).key();
    }

    public byte[] lastKey() {
        return entries.isEmpty() ? new byte[0] : entries.get(entries.size() - 1).key();
    }

    public void reset() {
        entries.clear();
        currentSize = Integer.BYTES;
    }
}
