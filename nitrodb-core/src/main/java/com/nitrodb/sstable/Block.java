package com.nitrodb.sstable;

import com.nitrodb.memtable.MemTableEntry;
import com.nitrodb.serialization.RecordEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class Block {

    private final List<MemTableEntry> entries;

    private Block(List<MemTableEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static Block decode(byte[] data) {
        return decode(ByteBuffer.wrap(data));
    }

    public static Block decode(ByteBuffer encodedBlock) {
        ByteBuffer buffer = encodedBlock.duplicate();
        int count = buffer.getInt();
        RecordEncoder encoder = new RecordEncoder();
        List<MemTableEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(encoder.decode(buffer));
        }
        return new Block(entries);
    }

    public Optional<MemTableEntry> search(byte[] key, long maxSeq) {
        for (MemTableEntry entry : entries) {
            if (!Arrays.equals(entry.key(), key)) {
                continue;
            }
            if (entry.sequenceNumber() <= maxSeq) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public List<MemTableEntry> entries() {
        return entries;
    }
}
