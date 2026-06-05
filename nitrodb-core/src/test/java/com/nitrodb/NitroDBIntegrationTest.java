package com.nitrodb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nitrodb.api.Entry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NitroDBIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void putGetDeleteAndScanWorkEndToEnd() {
        try (NitroDB db = new NitroDBBuilder().dataDir(tempDir).memTableSize(256).build()) {
            db.put(bytes("alpha"), bytes("one"));
            db.put(bytes("beta"), bytes("two"));
            db.put(bytes("gamma"), bytes("three"));
            ((NitroDBImpl) db).awaitBackgroundWorkForTesting();

            assertArrayEquals(bytes("one"), db.get(bytes("alpha")).orElseThrow());

            db.delete(bytes("beta"));
            assertFalse(db.get(bytes("beta")).isPresent());

            List<String> keys = new ArrayList<>();
            try (var scan = db.scan(bytes("alpha"), bytes("omega"))) {
                for (Entry entry : scan) {
                    keys.add(new String(entry.key(), StandardCharsets.UTF_8));
                }
            }
            assertEquals(List.of("alpha", "gamma"), keys);
        }
    }

    @Test
    void snapshotsSeeOldValue() {
        try (NitroDB db = new NitroDBBuilder().dataDir(tempDir).build()) {
            db.put(bytes("key"), bytes("old"));
            try (var snapshot = db.getSnapshot()) {
                db.put(bytes("key"), bytes("new"));
                assertArrayEquals(bytes("old"), db.get(bytes("key"), new com.nitrodb.api.ReadOptions(snapshot)).orElseThrow());
            }
            assertArrayEquals(bytes("new"), db.get(bytes("key")).orElseThrow());
        }
    }

    @Test
    void reopenRetainsData() {
        try (NitroDB db = new NitroDBBuilder().dataDir(tempDir).memTableSize(128).build()) {
            db.put(bytes("persist"), bytes("value"));
        }

        try (NitroDB db = new NitroDBBuilder().dataDir(tempDir).build()) {
            assertArrayEquals(bytes("value"), db.get(bytes("persist")).orElseThrow());
        }
    }

    @Test
    void crashRecoveryReplaysWal() {
        NitroDBImpl db = (NitroDBImpl) new NitroDBBuilder().dataDir(tempDir).memTableSize(1024 * 1024).build();
        db.put(bytes("crash"), bytes("value"));
        db.simulateCrashForTesting();

        try (NitroDB reopened = new NitroDBBuilder().dataDir(tempDir).build()) {
            assertTrue(reopened.get(bytes("crash")).isPresent());
            assertArrayEquals(bytes("value"), reopened.get(bytes("crash")).orElseThrow());
        }
    }

    @Test
    void manualCompactionPreservesData() {
        DBConfig config = new DBConfig(
                tempDir,
                128,
                DBConfig.DEFAULT_BLOCK_CACHE_SIZE_BYTES,
                DBConfig.DEFAULT_BLOCK_SIZE_BYTES,
                DBConfig.DEFAULT_MAX_LEVELS,
                DBConfig.DEFAULT_LEVEL_SIZE_MULTIPLIER,
                DBConfig.DEFAULT_L1_MAX_BYTES,
                DBConfig.DEFAULT_BLOOM_FALSE_POSITIVE_RATE,
                false,
                DBConfig.WalSyncMode.ASYNC,
                DBConfig.WalCorruptionPolicy.LENIENT,
                50L,
                DBConfig.DEFAULT_MAX_IMMUTABLE_COUNT,
                256,
                DBConfig.DEFAULT_WAL_SEGMENT_SIZE_BYTES,
                DBConfig.DEFAULT_NUM_CACHE_SHARDS,
                true);

        NitroDBImpl db = new NitroDBImpl(config);
        try {
            for (int i = 0; i < 200; i++) {
                db.put(bytes("k%03d".formatted(i)), bytes("v%03d".formatted(i)));
            }
            db.awaitBackgroundWorkForTesting();
            db.triggerCompactionForTesting();
            assertTrue(db.sstableCountForTesting() > 0);
            assertArrayEquals(bytes("v050"), db.get(bytes("k050")).orElseThrow());
        } finally {
            db.close();
        }
    }

    @Test
    void repeatedManualCompactionsRemainStable() {
        DBConfig config = new DBConfig(
                tempDir,
                128,
                DBConfig.DEFAULT_BLOCK_CACHE_SIZE_BYTES,
                DBConfig.DEFAULT_BLOCK_SIZE_BYTES,
                DBConfig.DEFAULT_MAX_LEVELS,
                DBConfig.DEFAULT_LEVEL_SIZE_MULTIPLIER,
                DBConfig.DEFAULT_L1_MAX_BYTES,
                DBConfig.DEFAULT_BLOOM_FALSE_POSITIVE_RATE,
                false,
                DBConfig.WalSyncMode.ASYNC,
                DBConfig.WalCorruptionPolicy.LENIENT,
                25L,
                DBConfig.DEFAULT_MAX_IMMUTABLE_COUNT,
                256,
                DBConfig.DEFAULT_WAL_SEGMENT_SIZE_BYTES,
                DBConfig.DEFAULT_NUM_CACHE_SHARDS,
                true);

        NitroDBImpl db = new NitroDBImpl(config);
        try {
            for (int i = 0; i < 160; i++) {
                db.put(bytes("stable%03d".formatted(i)), bytes("value%03d".formatted(i)));
            }
            db.awaitBackgroundWorkForTesting();

            for (int round = 0; round < 5; round++) {
                db.triggerCompactionForTesting();
                db.awaitBackgroundWorkForTesting();
                assertArrayEquals(
                        bytes("value042"),
                        db.get(bytes("stable042")).orElseThrow());
            }
        } finally {
            db.close();
        }
    }

    @Test
    void repeatedSstableReadsUseBlockCache() {
        NitroDBImpl db = (NitroDBImpl) new NitroDBBuilder().dataDir(tempDir).memTableSize(128).build();
        try {
            for (int i = 0; i < 32; i++) {
                db.put(bytes("cache%02d".formatted(i)), bytes("value%02d".formatted(i)));
            }
            db.awaitBackgroundWorkForTesting();

            assertEquals(0L, db.blockCacheSizeForTesting());
            assertArrayEquals(bytes("value10"), db.get(bytes("cache10")).orElseThrow());
            assertTrue(db.blockCacheSizeForTesting() > 0L);
            assertEquals(0L, db.blockCacheHitCountForTesting());
            assertTrue(db.blockCacheMissCountForTesting() > 0L);

            assertArrayEquals(bytes("value10"), db.get(bytes("cache10")).orElseThrow());
            assertTrue(db.blockCacheHitCountForTesting() > 0L);
        } finally {
            db.close();
        }
    }

    @Test
    void compactionDropsObsoleteVersionsAfterSnapshotsRelease() {
        DBConfig config = new DBConfig(
                tempDir,
                128,
                DBConfig.DEFAULT_BLOCK_CACHE_SIZE_BYTES,
                DBConfig.DEFAULT_BLOCK_SIZE_BYTES,
                4,
                DBConfig.DEFAULT_LEVEL_SIZE_MULTIPLIER,
                1,
                DBConfig.DEFAULT_BLOOM_FALSE_POSITIVE_RATE,
                false,
                DBConfig.WalSyncMode.ASYNC,
                DBConfig.WalCorruptionPolicy.LENIENT,
                25L,
                DBConfig.DEFAULT_MAX_IMMUTABLE_COUNT,
                256,
                DBConfig.DEFAULT_WAL_SEGMENT_SIZE_BYTES,
                DBConfig.DEFAULT_NUM_CACHE_SHARDS,
                true);

        NitroDBImpl db = new NitroDBImpl(config);
        try {
            db.put(bytes("mvcc-key"), bytes("v1"));
            forceFlush(db, "round1");

            try (var snapshot = db.getSnapshot()) {
                db.put(bytes("mvcc-key"), bytes("v2"));
                forceFlush(db, "round2");
                db.put(bytes("mvcc-key"), bytes("v3"));
                forceFlush(db, "round3");

                compactSeveralTimes(db, 3);
                assertArrayEquals(
                        bytes("v1"),
                        db.get(bytes("mvcc-key"), new com.nitrodb.api.ReadOptions(snapshot)).orElseThrow());
                assertTrue(db.versionCountForKeyInSstablesTesting(bytes("mvcc-key")) >= 3L);
            }

            compactSeveralTimes(db, 3);
            db.put(bytes("mvcc-key"), bytes("v4"));
            forceFlush(db, "round4");
            compactSeveralTimes(db, 3);
            assertArrayEquals(bytes("v4"), db.get(bytes("mvcc-key")).orElseThrow());
            assertEquals(1L, db.versionCountForKeyInSstablesTesting(bytes("mvcc-key")));
        } finally {
            db.close();
        }
    }

    @Test
    void compactionDropsTombstonesWhenNoDeeperOverlapExists() {
        DBConfig config = new DBConfig(
                tempDir,
                128,
                DBConfig.DEFAULT_BLOCK_CACHE_SIZE_BYTES,
                DBConfig.DEFAULT_BLOCK_SIZE_BYTES,
                4,
                DBConfig.DEFAULT_LEVEL_SIZE_MULTIPLIER,
                1,
                DBConfig.DEFAULT_BLOOM_FALSE_POSITIVE_RATE,
                false,
                DBConfig.WalSyncMode.ASYNC,
                DBConfig.WalCorruptionPolicy.LENIENT,
                25L,
                DBConfig.DEFAULT_MAX_IMMUTABLE_COUNT,
                256,
                DBConfig.DEFAULT_WAL_SEGMENT_SIZE_BYTES,
                DBConfig.DEFAULT_NUM_CACHE_SHARDS,
                true);

        NitroDBImpl db = new NitroDBImpl(config);
        try {
            db.put(bytes("deleted-key"), bytes("value"));
            forceFlush(db, "put");
            db.delete(bytes("deleted-key"));
            forceFlush(db, "delete");

            compactSeveralTimes(db, 3);
            assertFalse(db.get(bytes("deleted-key")).isPresent());
            assertEquals(0L, db.tombstoneCountForKeyInSstablesTesting(bytes("deleted-key")));
        } finally {
            db.close();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void forceFlush(NitroDBImpl db, String prefix) {
        for (int i = 0; i < 24; i++) {
            db.put(bytes(prefix + "-fill-" + i), bytes("filler-" + i));
        }
        db.awaitBackgroundWorkForTesting();
    }

    private static void compactSeveralTimes(NitroDBImpl db, int rounds) {
        for (int i = 0; i < rounds; i++) {
            db.triggerCompactionForTesting();
            db.awaitBackgroundWorkForTesting();
        }
    }
}
