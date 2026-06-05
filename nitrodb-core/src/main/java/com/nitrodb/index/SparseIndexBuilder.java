package com.nitrodb.index;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class SparseIndexBuilder {

    private final List<SparseIndexEntry> entries = new ArrayList<>();

    public void add(byte[] firstKey, long offset, int length) {
        entries.add(new SparseIndexEntry(firstKey.clone(), offset, length));
    }

    public SparseIndex build() {
        return new SparseIndex(entries);
    }

    public byte[] serialize() {
        int size = Integer.BYTES;
        for (SparseIndexEntry entry : entries) {
            size += Integer.BYTES + entry.firstKey().length + Long.BYTES + Integer.BYTES;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.putInt(entries.size());
        for (SparseIndexEntry entry : entries) {
            buffer.putInt(entry.firstKey().length);
            buffer.put(entry.firstKey());
            buffer.putLong(entry.blockOffset());
            buffer.putInt(entry.blockLength());
        }
        return buffer.array();
    }
}
