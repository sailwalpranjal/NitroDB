package com.nitrodb.serialization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nitrodb.memtable.MemTableEntry;
import com.nitrodb.memtable.MemTableEntry.EntryType;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class RecordEncoderTest {

    private final RecordEncoder recordEncoder = new RecordEncoder();

    @Test
    void roundTripsPutRecord() {
        MemTableEntry entry = new MemTableEntry("key".getBytes(), "value".getBytes(), 11L, EntryType.PUT);
        ByteBuffer buffer = ByteBuffer.allocate(recordEncoder.encodedSize(entry));

        recordEncoder.encode(entry, buffer);
        buffer.flip();
        MemTableEntry decoded = recordEncoder.decode(buffer);

        assertArrayEquals(entry.key(), decoded.key());
        assertArrayEquals(entry.value(), decoded.value());
        assertEquals(entry.sequenceNumber(), decoded.sequenceNumber());
        assertEquals(entry.type(), decoded.type());
    }

    @Test
    void roundTripsDeleteRecord() {
        MemTableEntry entry = new MemTableEntry("key".getBytes(), null, 12L, EntryType.DELETE);
        ByteBuffer buffer = ByteBuffer.allocate(recordEncoder.encodedSize(entry));

        recordEncoder.encode(entry, buffer);
        buffer.flip();
        MemTableEntry decoded = recordEncoder.decode(buffer);

        assertArrayEquals(entry.key(), decoded.key());
        assertNull(decoded.value());
        assertEquals(entry.sequenceNumber(), decoded.sequenceNumber());
        assertEquals(entry.type(), decoded.type());
    }
}
