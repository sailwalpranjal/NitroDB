package com.nitrodb.serialization;

import java.nio.ByteBuffer;

public final class KeyEncoder {

    public void encode(byte[] key, ByteBuffer buffer) {
        buffer.putInt(key.length);
        buffer.put(key);
    }

    public byte[] decode(ByteBuffer buffer) {
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid encoded key length: " + length);
        }
        byte[] key = new byte[length];
        buffer.get(key);
        return key;
    }

    public int encodedSize(byte[] key) {
        return Integer.BYTES + key.length;
    }
}
