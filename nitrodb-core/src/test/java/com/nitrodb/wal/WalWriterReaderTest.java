package com.nitrodb.wal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nitrodb.DBConfig;
import com.nitrodb.api.DBException.CorruptionException;
import com.nitrodb.io.FileManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WalWriterReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsPutAndDeleteRecords() {
        WalManager walManager = new WalManager(tempDir, 1024 * 1024, new FileManager());
        try (WalWriter writer = new WalWriter(walManager)) {
            writer.append(new WalRecord(WalRecord.RecordType.PUT, "a".getBytes(), "one".getBytes(), 1L));
            writer.append(new WalRecord(WalRecord.RecordType.DELETE, "a".getBytes(), null, 2L));
            writer.sync();
        }

        try (WalReader reader = new WalReader(walManager.listSegments())) {
            WalRecord first = reader.readNext().orElseThrow();
            WalRecord second = reader.readNext().orElseThrow();

            assertEquals(WalRecord.RecordType.PUT, first.type());
            assertEquals("one", new String(first.value()));
            assertEquals(WalRecord.RecordType.DELETE, second.type());
            assertEquals(2L, second.sequenceNumber());
        }
    }

    @Test
    void writesAndReadsBatchMarkers() {
        WalManager walManager = new WalManager(tempDir, 1024 * 1024, new FileManager());
        try (WalWriter writer = new WalWriter(walManager)) {
            writer.append(new WalRecord(WalRecord.RecordType.BATCH_START, null, null, 1L));
            writer.append(new WalRecord(WalRecord.RecordType.PUT, "a".getBytes(), "one".getBytes(), 2L));
            writer.append(new WalRecord(WalRecord.RecordType.BATCH_END, null, null, 3L));
            writer.sync();
        }

        try (WalReader reader = new WalReader(walManager.listSegments())) {
            assertEquals(WalRecord.RecordType.BATCH_START, reader.readNext().orElseThrow().type());
            assertEquals(WalRecord.RecordType.PUT, reader.readNext().orElseThrow().type());
            assertEquals(WalRecord.RecordType.BATCH_END, reader.readNext().orElseThrow().type());
            assertFalse(reader.hasNext());
        }
    }

    @Test
    void lenientModeStopsAtCorruption() throws IOException {
        WalManager walManager = new WalManager(tempDir, 1024 * 1024, new FileManager());
        try (WalWriter writer = new WalWriter(walManager)) {
            writer.append(new WalRecord(WalRecord.RecordType.PUT, "a".getBytes(), "one".getBytes(), 1L));
            writer.sync();
        }
        corruptFirstFrameCrc(walManager.listSegments().getFirst());

        try (WalReader reader = new WalReader(walManager.listSegments(), DBConfig.WalCorruptionPolicy.LENIENT)) {
            assertFalse(reader.hasNext());
        }
    }

    @Test
    void strictModeThrowsOnCorruption() throws IOException {
        WalManager walManager = new WalManager(tempDir, 1024 * 1024, new FileManager());
        try (WalWriter writer = new WalWriter(walManager)) {
            writer.append(new WalRecord(WalRecord.RecordType.PUT, "a".getBytes(), "one".getBytes(), 1L));
            writer.sync();
        }
        corruptFirstFrameCrc(walManager.listSegments().getFirst());

        try (WalReader reader = new WalReader(walManager.listSegments(), DBConfig.WalCorruptionPolicy.STRICT)) {
            assertThrows(CorruptionException.class, reader::hasNext);
        }
    }

    private static void corruptFirstFrameCrc(Path walPath) throws IOException {
        try (FileChannel channel = FileChannel.open(walPath, StandardOpenOption.WRITE)) {
            ByteBuffer crc = ByteBuffer.allocate(Integer.BYTES);
            crc.putInt(0);
            crc.flip();
            channel.write(crc, WalConstants.HEADER_SIZE);
        }
    }
}
