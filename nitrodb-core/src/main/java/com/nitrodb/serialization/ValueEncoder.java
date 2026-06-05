package com.nitrodb.serialization;

import com.nitrodb.memtable.MemTableEntry.EntryType;
import java.nio.ByteBuffer;

public final class ValueEncoder {

    public void encode(byte[] value, EntryType type, ByteBuffer buffer) {
        buffer.put(type.persistentId());
        if (type == EntryType.DELETE) {
            buffer.putInt(0);
            return;
        }
        byte[] safeValue = value == null ? new byte[0] : value;
        buffer.putInt(safeValue.length);
        buffer.put(safeValue);
    }

    public ValueDecodeResult decode(ByteBuffer buffer) {
        EntryType type = EntryType.fromPersistentId(buffer.get());
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid encoded value length: " + length);
        }
        if (type == EntryType.DELETE) {
            if (length != 0) {
                throw new IllegalArgumentException("DELETE values must have length 0");
            }
            return new ValueDecodeResult(type, null);
        }
        byte[] value = new byte[length];
        buffer.get(value);
        return new ValueDecodeResult(type, value);
    }

    public int encodedSize(byte[] value, EntryType type) {
        int valueLength = type == EntryType.DELETE || value == null ? 0 : value.length;
        return Byte.BYTES + Integer.BYTES + valueLength;
    }

    public record ValueDecodeResult(EntryType type, byte[] value) {
    }
}
