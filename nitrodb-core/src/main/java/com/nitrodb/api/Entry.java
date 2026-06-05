package com.nitrodb.api;

import java.util.Arrays;

public record Entry(byte[] key, byte[] value, long sequenceNumber) {

    public Entry {
        key = Arrays.copyOf(key, key.length);
        value = Arrays.copyOf(value, value.length);
    }

    @Override
    public byte[] key() {
        return Arrays.copyOf(key, key.length);
    }

    @Override
    public byte[] value() {
        return Arrays.copyOf(value, value.length);
    }
}
