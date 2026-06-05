package com.nitrodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DBConfigTest {

    @Test
    void defaultsUseBlueprintValues() {
        DBConfig config = DBConfig.defaults(Path.of("data"));

        assertTrue(config.dataDir().isAbsolute());
        assertEquals(64L * 1024L * 1024L, config.memTableSizeBytes());
        assertEquals(256L * 1024L * 1024L, config.blockCacheSizeBytes());
        assertEquals(4 * 1024, config.blockSizeBytes());
        assertEquals(7, config.maxLevels());
        assertEquals(10, config.levelSizeMultiplier());
        assertEquals(256L * 1024L * 1024L, config.l1MaxBytes());
        assertEquals(0.01d, config.bloomFalsePositiveRate());
        assertFalse(config.syncWrites());
        assertEquals(DBConfig.WalSyncMode.ASYNC, config.walSyncMode());
        assertEquals(DBConfig.WalCorruptionPolicy.LENIENT, config.walCorruptionPolicy());
        assertEquals(1_000L, config.compactionIntervalMs());
        assertEquals(4, config.maxImmutableCount());
        assertEquals(64L * 1024L * 1024L, config.targetSSTableSizeBytes());
        assertEquals(64L * 1024L * 1024L, config.walSegmentSizeBytes());
        assertEquals(16, config.numCacheShards());
        assertTrue(config.checksumVerification());
    }

    @Test
    void rejectsInvalidBloomRate() {
        assertThrows(IllegalArgumentException.class, () -> new DBConfig(
                Path.of("data"),
                1,
                1,
                1,
                1,
                1,
                1,
                1.0d,
                false,
                DBConfig.WalSyncMode.ASYNC,
                DBConfig.WalCorruptionPolicy.LENIENT,
                1,
                1,
                1,
                1,
                1,
                true));
    }

    @Test
    void rejectsSyncWritesWithAsyncWalMode() {
        assertThrows(IllegalArgumentException.class, () -> new DBConfig(
                Path.of("data"),
                1,
                1,
                1,
                1,
                1,
                1,
                0.01d,
                true,
                DBConfig.WalSyncMode.ASYNC,
                DBConfig.WalCorruptionPolicy.LENIENT,
                1,
                1,
                1,
                1,
                1,
                true));
    }

    @Test
    void rejectsNonPositiveThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new DBConfig(
                Path.of("data"),
                0,
                1,
                1,
                1,
                1,
                1,
                0.01d,
                false,
                DBConfig.WalSyncMode.ASYNC,
                DBConfig.WalCorruptionPolicy.LENIENT,
                1,
                1,
                1,
                1,
                1,
                true));
    }
}
