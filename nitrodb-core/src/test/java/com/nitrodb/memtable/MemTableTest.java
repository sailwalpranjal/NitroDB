package com.nitrodb.memtable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MemTableTest {

    @Test
    void getReturnsLatestVisibleVersion() {
        MemTable memTable = new MemTable();
        memTable.put("key".getBytes(), "one".getBytes(), 1L);
        memTable.put("key".getBytes(), "two".getBytes(), 2L);

        assertArrayEquals("two".getBytes(), memTable.get("key".getBytes(), 2L).orElseThrow().value());
        assertArrayEquals("one".getBytes(), memTable.get("key".getBytes(), 1L).orElseThrow().value());
    }

    @Test
    void tombstoneHidesOlderValue() {
        MemTable memTable = new MemTable();
        memTable.put("key".getBytes(), "value".getBytes(), 1L);
        memTable.putTombstone("key".getBytes(), 2L);

        assertTrue(memTable.get("key".getBytes(), 2L).orElseThrow().isTombstone());
    }

    @Test
    void freezeProducesReadableImmutableMemTable() {
        MemTable memTable = new MemTable();
        memTable.put("key".getBytes(), "value".getBytes(), 1L);

        ImmutableMemTable immutable = memTable.freeze();

        assertEquals(1L, immutable.maxSeq());
        assertArrayEquals("value".getBytes(), immutable.get("key".getBytes(), 1L).orElseThrow().value());
    }
}
