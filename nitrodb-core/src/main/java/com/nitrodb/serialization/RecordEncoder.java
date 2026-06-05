package com.nitrodb.serialization;

import com.nitrodb.memtable.MemTableEntry;
import java.nio.ByteBuffer;

public final class RecordEncoder {

    public void encode(MemTableEntry entry, ByteBuffer buffer) {
        buffer.putLong(entry.sequenceNumber());
        buffer.put(entry.type().persistentId());
        buffer.putInt(entry.key().length);
        buffer.put(entry.key());
        byte[] value = entry.value();
        int valueLength = value == null ? 0 : value.length;
        buffer.putInt(valueLength);
        if (valueLength > 0) {
            buffer.put(value);
        }
    }

    public MemTableEntry decode(ByteBuffer buffer) {
        long sequenceNumber = buffer.getLong();
        var type = MemTableEntry.EntryType.fromPersistentId(buffer.get());
        int keyLength = buffer.getInt();
        if (keyLength < 0 || keyLength > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid encoded key length: " + keyLength);
        }
        byte[] key = new byte[keyLength];
        buffer.get(key);
        int valueLength = buffer.getInt();
        if (valueLength < 0 || valueLength > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid encoded value length: " + valueLength);
        }
        byte[] value = valueLength == 0 ? null : new byte[valueLength];
        if (value != null) {
            buffer.get(value);
        }
        return new MemTableEntry(key, value, sequenceNumber, type);
    }

    public int encodedSize(MemTableEntry entry) {
        int valueLength = entry.value() == null ? 0 : entry.value().length;
        return BinaryFormat.encodedRecordSize(entry.key().length, valueLength);
    }
}
