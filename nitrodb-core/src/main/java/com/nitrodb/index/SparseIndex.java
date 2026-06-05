package com.nitrodb.index;

import com.google.common.primitives.UnsignedBytes;
import com.nitrodb.sstable.BlockHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SparseIndex {

    private static final java.util.Comparator<byte[]> KEY_COMPARATOR = UnsignedBytes.lexicographicalComparator();

    private final List<SparseIndexEntry> entries;

    public SparseIndex(List<SparseIndexEntry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public BlockHandle findBlock(byte[] key) {
        int index = findBlockIndex(key);
        if (index < 0) {
            return null;
        }
        SparseIndexEntry entry = entries.get(index);
        return new BlockHandle(entry.blockOffset(), entry.blockLength());
    }

    public int findBlockIndex(byte[] key) {
        if (entries.isEmpty()) {
            return -1;
        }
        int low = 0;
        int high = entries.size() - 1;
        int result = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int comparison = KEY_COMPARATOR.compare(entries.get(mid).firstKey(), key);
            if (comparison <= 0) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    public int size() {
        return entries.size();
    }

    public byte[] firstKey() {
        return entries.isEmpty() ? new byte[0] : entries.get(0).firstKey().clone();
    }

    public byte[] lastKey() {
        return entries.isEmpty() ? new byte[0] : entries.get(entries.size() - 1).firstKey().clone();
    }

    public List<SparseIndexEntry> entries() {
        return entries;
    }

    public static SparseIndex deserialize(byte[] bytes) {
        if (bytes.length == 0) {
            return new SparseIndex(List.of());
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int count = buffer.getInt();
        List<SparseIndexEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int keyLength = buffer.getInt();
            byte[] key = new byte[keyLength];
            buffer.get(key);
            long offset = buffer.getLong();
            int length = buffer.getInt();
            entries.add(new SparseIndexEntry(key, offset, length));
        }
        return new SparseIndex(entries);
    }
}
