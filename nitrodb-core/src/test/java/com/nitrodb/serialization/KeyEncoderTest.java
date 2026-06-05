package com.nitrodb.serialization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class KeyEncoderTest {

    private final KeyEncoder keyEncoder = new KeyEncoder();

    @Test
    void roundTripsKey() {
        byte[] key = "nitro-key".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(keyEncoder.encodedSize(key));

        keyEncoder.encode(key, buffer);
        buffer.flip();

        assertArrayEquals(key, keyEncoder.decode(buffer));
    }

    @Test
    void supportsEmptyKey() {
        byte[] key = new byte[0];
        ByteBuffer buffer = ByteBuffer.allocate(keyEncoder.encodedSize(key));

        keyEncoder.encode(key, buffer);
        buffer.flip();

        assertArrayEquals(key, keyEncoder.decode(buffer));
        assertEquals(Integer.BYTES, keyEncoder.encodedSize(key));
    }
}
