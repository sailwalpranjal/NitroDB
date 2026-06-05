package com.nitrodb.serialization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nitrodb.memtable.MemTableEntry.EntryType;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ValueEncoderTest {

    private final ValueEncoder valueEncoder = new ValueEncoder();

    @Test
    void roundTripsPutValue() {
        byte[] value = "nitro-value".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(valueEncoder.encodedSize(value, EntryType.PUT));

        valueEncoder.encode(value, EntryType.PUT, buffer);
        buffer.flip();
        ValueEncoder.ValueDecodeResult decoded = valueEncoder.decode(buffer);

        assertEquals(EntryType.PUT, decoded.type());
        assertArrayEquals(value, decoded.value());
    }

    @Test
    void roundTripsDeleteMarker() {
        ByteBuffer buffer = ByteBuffer.allocate(valueEncoder.encodedSize(null, EntryType.DELETE));

        valueEncoder.encode(null, EntryType.DELETE, buffer);
        buffer.flip();
        ValueEncoder.ValueDecodeResult decoded = valueEncoder.decode(buffer);

        assertEquals(EntryType.DELETE, decoded.type());
        assertNull(decoded.value());
    }
}
