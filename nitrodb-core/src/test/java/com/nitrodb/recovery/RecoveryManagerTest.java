package com.nitrodb.recovery;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nitrodb.DBConfig;
import com.nitrodb.api.DBException.CorruptionException;
import com.nitrodb.io.FileManager;
import com.nitrodb.manifest.ManifestManager;
import com.nitrodb.mvcc.SequenceGenerator;
import com.nitrodb.wal.WalManager;
import com.nitrodb.wal.WalConstants;
import com.nitrodb.wal.WalRecord;
import com.nitrodb.wal.WalWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoveryManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void replaysWalIntoMemtable() {
        DBConfig config = DBConfig.defaults(tempDir);
        ManifestManager manifest = new ManifestManager();
        manifest.open(tempDir);
        WalManager walManager = new WalManager(tempDir, 1024 * 1024, new FileManager());
        try (WalWriter writer = new WalWriter(walManager)) {
            writer.append(new WalRecord(WalRecord.RecordType.PUT, "key".getBytes(), "value".getBytes(), 1L));
            writer.sync();
        }

        SequenceGenerator sequenceGenerator = new SequenceGenerator();
        RecoveryManager recoveryManager = new RecoveryManager(manifest, walManager, sequenceGenerator);
        RecoveryManager.RecoveryResult result = recoveryManager.recover(tempDir, config);

        assertArrayEquals("value".getBytes(), result.recoveredMemTable().get("key".getBytes(), 1L).orElseThrow().value());
        assertEquals(1L, sequenceGenerator.current());
    }

    @Test
    void replaysCompletedBatchAtomically() {
        DBConfig config = DBConfig.defaults(tempDir);
        ManifestManager manifest = new ManifestManager();
        manifest.open(tempDir);
        WalManager walManager = new WalManager(tempDir, 1024 * 1024, new FileManager());
        try (WalWriter writer = new WalWriter(walManager)) {
            writer.append(new WalRecord(WalRecord.RecordType.BATCH_START, null, null, 1L));
            writer.append(new WalRecord(WalRecord.RecordType.PUT, "key".getBytes(), "value".getBytes(), 2L));
            writer.append(new WalRecord(WalRecord.RecordType.PUT, "key2".getBytes(), "value2".getBytes(), 3L));
            writer.append(new WalRecord(WalRecord.RecordType.BATCH_END, null, null, 4L));
            writer.sync();
        }

        SequenceGenerator sequenceGenerator = new SequenceGenerator();
        RecoveryManager recoveryManager = new RecoveryManager(manifest, walManager, sequenceGenerator);
        RecoveryManager.RecoveryResult result = recoveryManager.recover(tempDir, config);

        assertArrayEquals("value".getBytes(), result.recoveredMemTable().get("key".getBytes(), 10L).orElseThrow().value());
        assertArrayEquals(
                "value2".getBytes(),
                result.recoveredMemTable().get("key2".getBytes(), 10L).orElseThrow().value());
        assertEquals(3L, sequenceGenerator.current());
    }

    @Test
    void incompleteBatchIsNotAppliedInLenientMode() {
        DBConfig config = new DBConfig(
                tempDir,
                DBConfig.DEFAULT_MEMTABLE_SIZE_BYTES,
                DBConfig.DEFAULT_BLOCK_CACHE_SIZE_BYTES,
                DBConfig.DEFAULT_BLOCK_SIZE_BYTES,
                DBConfig.DEFAULT_MAX_LEVELS,
                DBConfig.DEFAULT_LEVEL_SIZE_MULTIPLIER,
                DBConfig.DEFAULT_L1_MAX_BYTES,
                DBConfig.DEFAULT_BLOOM_FALSE_POSITIVE_RATE,
                DBConfig.DEFAULT_SYNC_WRITES,
                DBConfig.DEFAULT_WAL_SYNC_MODE,
                DBConfig.WalCorruptionPolicy.LENIENT,
                DBConfig.DEFAULT_COMPACTION_INTERVAL_MS,
                DBConfig.DEFAULT_MAX_IMMUTABLE_COUNT,
                DBConfig.DEFAULT_TARGET_SSTABLE_SIZE_BYTES,
                DBConfig.DEFAULT_WAL_SEGMENT_SIZE_BYTES,
                DBConfig.DEFAULT_NUM_CACHE_SHARDS,
                DBConfig.DEFAULT_CHECKSUM_VERIFICATION);
        ManifestManager manifest = new ManifestManager();
        manifest.open(tempDir);
        WalManager walManager = new WalManager(tempDir, 1024 * 1024, new FileManager());
        try (WalWriter writer = new WalWriter(walManager)) {
            writer.append(new WalRecord(WalRecord.RecordType.BATCH_START, null, null, 1L));
            writer.append(new WalRecord(WalRecord.RecordType.PUT, "key".getBytes(), "value".getBytes(), 2L));
            writer.sync();
        }

        SequenceGenerator sequenceGenerator = new SequenceGenerator();
        RecoveryManager recoveryManager = new RecoveryManager(manifest, walManager, sequenceGenerator);
        RecoveryManager.RecoveryResult result = recoveryManager.recover(tempDir, config);

        assertFalse(result.recoveredMemTable().get("key".getBytes(), 10L).isPresent());
        assertEquals(0L, sequenceGenerator.current());
    }

    @Test
    void strictModeFailsRecoveryOnCorruption() throws IOException {
        DBConfig config = new DBConfig(
                tempDir,
                DBConfig.DEFAULT_MEMTABLE_SIZE_BYTES,
                DBConfig.DEFAULT_BLOCK_CACHE_SIZE_BYTES,
                DBConfig.DEFAULT_BLOCK_SIZE_BYTES,
                DBConfig.DEFAULT_MAX_LEVELS,
                DBConfig.DEFAULT_LEVEL_SIZE_MULTIPLIER,
                DBConfig.DEFAULT_L1_MAX_BYTES,
                DBConfig.DEFAULT_BLOOM_FALSE_POSITIVE_RATE,
                DBConfig.DEFAULT_SYNC_WRITES,
                DBConfig.DEFAULT_WAL_SYNC_MODE,
                DBConfig.WalCorruptionPolicy.STRICT,
                DBConfig.DEFAULT_COMPACTION_INTERVAL_MS,
                DBConfig.DEFAULT_MAX_IMMUTABLE_COUNT,
                DBConfig.DEFAULT_TARGET_SSTABLE_SIZE_BYTES,
                DBConfig.DEFAULT_WAL_SEGMENT_SIZE_BYTES,
                DBConfig.DEFAULT_NUM_CACHE_SHARDS,
                DBConfig.DEFAULT_CHECKSUM_VERIFICATION);
        ManifestManager manifest = new ManifestManager();
        manifest.open(tempDir);
        WalManager walManager = new WalManager(tempDir, 1024 * 1024, new FileManager());
        try (WalWriter writer = new WalWriter(walManager)) {
            writer.append(new WalRecord(WalRecord.RecordType.PUT, "key".getBytes(), "value".getBytes(), 1L));
            writer.sync();
        }
        corruptFirstFrameCrc(walManager.listSegments().getFirst(), WalConstants.HEADER_SIZE);

        SequenceGenerator sequenceGenerator = new SequenceGenerator();
        RecoveryManager recoveryManager = new RecoveryManager(manifest, walManager, sequenceGenerator);
        assertThrows(CorruptionException.class, () -> recoveryManager.recover(tempDir, config));
    }

    private static void corruptFirstFrameCrc(Path walPath, long offset) throws IOException {
        try (FileChannel channel = FileChannel.open(walPath, StandardOpenOption.WRITE)) {
            ByteBuffer crc = ByteBuffer.allocate(Integer.BYTES);
            crc.putInt(0);
            crc.flip();
            channel.write(crc, offset);
        }
    }
}
