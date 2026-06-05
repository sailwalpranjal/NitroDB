package com.nitrodb.wal;

import java.util.Arrays;
import java.util.Objects;

public record WalRecord(RecordType type, byte[] key, byte[] value, long sequenceNumber) {

    public WalRecord {
        type = Objects.requireNonNull(type, "type");
        key = key == null ? null : Arrays.copyOf(key, key.length);
        value = value == null ? null : Arrays.copyOf(value, value.length);
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be >= 0");
        }
        if (type.requiresKey() && (key == null || key.length == 0)) {
            throw new IllegalArgumentException("key is required for " + type);
        }
        if (!type.allowsValue() && value != null) {
            throw new IllegalArgumentException("value is not allowed for " + type);
        }
        if (type == RecordType.PUT && value == null) {
            throw new IllegalArgumentException("value is required for PUT");
        }
    }

    @Override
    public byte[] key() {
        return key == null ? null : Arrays.copyOf(key, key.length);
    }

    @Override
    public byte[] value() {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    public enum RecordType {
        PUT,
        DELETE,
        BATCH_START,
        BATCH_END;

        boolean requiresKey() {
            return this == PUT || this == DELETE;
        }

        boolean allowsValue() {
            return this == PUT;
        }
    }
}
