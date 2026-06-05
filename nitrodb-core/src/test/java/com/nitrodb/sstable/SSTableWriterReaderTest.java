package com.nitrodb.sstable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nitrodb.DBConfig;
import com.nitrodb.cache.BlockCache;
import com.nitrodb.memtable.MemTableEntry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SSTableWriterReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripsEntriesThroughSstable() throws Exception {
        DBConfig config = DBConfig.defaults(tempDir);
        SSTableMetadata metadata;
        try (SSTableWriter writer = SSTableWriter.open(tempDir, 0, config)) {
            writer.add("alpha".getBytes(), "one".getBytes(), 1L, MemTableEntry.EntryType.PUT);
            writer.add("beta".getBytes(), null, 2L, MemTableEntry.EntryType.DELETE);
            metadata = writer.finish();
        }

        assertTrue(Files.exists(metadata.filePath()));

        try (SSTableReader reader = SSTableReader.open(metadata.filePath(), config)) {
            assertArrayEquals("one".getBytes(), reader.get("alpha".getBytes(), 10L).orElseThrow());
            assertTrue(reader.getEntry("beta".getBytes(), 10L).orElseThrow().isTombstone());
            assertFalse(reader.get("gamma".getBytes(), 10L).isPresent());
        }
    }

    @Test
    void bloomNegativeLookupSkipsBlockReads() throws Exception {
        DBConfig config = DBConfig.defaults(tempDir);
        SSTableMetadata metadata;
        try (SSTableWriter writer = SSTableWriter.open(tempDir, 0, config)) {
            writer.add("alpha".getBytes(), "one".getBytes(), 1L, MemTableEntry.EntryType.PUT);
            writer.add("delta".getBytes(), "two".getBytes(), 2L, MemTableEntry.EntryType.PUT);
            writer.add("omega".getBytes(), "three".getBytes(), 3L, MemTableEntry.EntryType.PUT);
            metadata = writer.finish();
        }

        try (BlockCache cache = new BlockCache(1024 * 1024, 1);
                SSTableReader reader = SSTableReader.open(metadata.filePath(), config, cache)) {
            byte[] absent = findBloomNegativeWithinTableRange(reader);
            assertNotNull(absent);
            assertFalse(reader.get(absent, 10L).isPresent());
            assertTrue(reader.bloomFilter().mightContain("alpha".getBytes()));
            assertEquals(0L, cache.requestCount());
            assertEquals(0L, cache.sizeBytes());
        }
    }

    private static byte[] findBloomNegativeWithinTableRange(SSTableReader reader) {
        String[] candidates = {
            "beta", "charlie", "echo", "foxtrot", "hotel", "kilo", "lambda", "sigma", "theta", "upsilon"
        };
        for (String candidate : candidates) {
            byte[] bytes = candidate.getBytes(StandardCharsets.UTF_8);
            if (!reader.bloomFilter().mightContain(bytes)) {
                return bytes;
            }
        }
        return null;
    }
}
