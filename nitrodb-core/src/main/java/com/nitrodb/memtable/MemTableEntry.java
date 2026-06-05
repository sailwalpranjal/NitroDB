package com.nitrodb.memtable;

import java.util.Arrays;

public record MemTableEntry(byte[] key, byte[] value, long sequenceNumber, EntryType type) {

    public MemTableEntry {
        key = Arrays.copyOf(key, key.length);
        value = value == null ? null : Arrays.copyOf(value, value.length);
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be >= 0");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (type == EntryType.DELETE && value != null && value.length != 0) {
            throw new IllegalArgumentException("DELETE entries must not contain a value payload");
        }
    }

    @Override
    public byte[] key() {
        return Arrays.copyOf(key, key.length);
    }

    @Override
    public byte[] value() {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    public boolean isTombstone() {
        return type == EntryType.DELETE;
    }

    public enum EntryType {
        PUT((byte) 1),
        DELETE((byte) 2);

        private final byte persistentId;

        EntryType(byte persistentId) {
            this.persistentId = persistentId;
        }

        public byte persistentId() {
            return persistentId;
        }

        public static EntryType fromPersistentId(byte persistentId) {
            for (EntryType value : values()) {
                if (value.persistentId == persistentId) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown entry type id: " + persistentId);
        }
    }
}
