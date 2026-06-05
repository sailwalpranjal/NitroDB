package com.nitrodb;

import java.nio.file.Path;
import java.util.Objects;

public record DBConfig(
        Path dataDir,
        long memTableSizeBytes,
        long blockCacheSizeBytes,
        int blockSizeBytes,
        int maxLevels,
        int levelSizeMultiplier,
        long l1MaxBytes,
        double bloomFalsePositiveRate,
        boolean syncWrites,
        WalSyncMode walSyncMode,
        WalCorruptionPolicy walCorruptionPolicy,
        long compactionIntervalMs,
        int maxImmutableCount,
        long targetSSTableSizeBytes,
        long walSegmentSizeBytes,
        int numCacheShards,
        boolean checksumVerification) {

    public static final long MEBIBYTE = 1024L * 1024L;
    public static final long DEFAULT_MEMTABLE_SIZE_BYTES = 64L * MEBIBYTE;
    public static final long DEFAULT_BLOCK_CACHE_SIZE_BYTES = 256L * MEBIBYTE;
    public static final int DEFAULT_BLOCK_SIZE_BYTES = 4 * 1024;
    public static final int DEFAULT_MAX_LEVELS = 7;
    public static final int DEFAULT_LEVEL_SIZE_MULTIPLIER = 10;
    public static final long DEFAULT_L1_MAX_BYTES = 256L * MEBIBYTE;
    public static final double DEFAULT_BLOOM_FALSE_POSITIVE_RATE = 0.01d;
    public static final boolean DEFAULT_SYNC_WRITES = false;
    public static final WalSyncMode DEFAULT_WAL_SYNC_MODE = WalSyncMode.ASYNC;
    public static final WalCorruptionPolicy DEFAULT_WAL_CORRUPTION_POLICY = WalCorruptionPolicy.LENIENT;
    public static final long DEFAULT_COMPACTION_INTERVAL_MS = 1_000L;
    public static final int DEFAULT_MAX_IMMUTABLE_COUNT = 4;
    public static final long DEFAULT_TARGET_SSTABLE_SIZE_BYTES = 64L * MEBIBYTE;
    public static final long DEFAULT_WAL_SEGMENT_SIZE_BYTES = 64L * MEBIBYTE;
    public static final int DEFAULT_NUM_CACHE_SHARDS = 16;
    public static final boolean DEFAULT_CHECKSUM_VERIFICATION = true;

    public DBConfig {
        dataDir = Objects.requireNonNull(dataDir, "dataDir").toAbsolutePath().normalize();
        walSyncMode = Objects.requireNonNull(walSyncMode, "walSyncMode");
        walCorruptionPolicy = Objects.requireNonNull(walCorruptionPolicy, "walCorruptionPolicy");

        requirePositive(memTableSizeBytes, "memTableSizeBytes");
        requirePositive(blockCacheSizeBytes, "blockCacheSizeBytes");
        requirePositive(blockSizeBytes, "blockSizeBytes");
        requirePositive(maxLevels, "maxLevels");
        requirePositive(levelSizeMultiplier, "levelSizeMultiplier");
        requirePositive(l1MaxBytes, "l1MaxBytes");
        requirePositive(compactionIntervalMs, "compactionIntervalMs");
        requirePositive(maxImmutableCount, "maxImmutableCount");
        requirePositive(targetSSTableSizeBytes, "targetSSTableSizeBytes");
        requirePositive(walSegmentSizeBytes, "walSegmentSizeBytes");
        requirePositive(numCacheShards, "numCacheShards");

        if (bloomFalsePositiveRate <= 0.0d || bloomFalsePositiveRate >= 1.0d) {
            throw new IllegalArgumentException("bloomFalsePositiveRate must be in the range (0, 1)");
        }
        if (syncWrites && walSyncMode == WalSyncMode.ASYNC) {
            throw new IllegalArgumentException("syncWrites=true is incompatible with walSyncMode=ASYNC");
        }
    }

    public static DBConfig defaults(Path dataDir) {
        return new DBConfig(
                dataDir,
                DEFAULT_MEMTABLE_SIZE_BYTES,
                DEFAULT_BLOCK_CACHE_SIZE_BYTES,
                DEFAULT_BLOCK_SIZE_BYTES,
                DEFAULT_MAX_LEVELS,
                DEFAULT_LEVEL_SIZE_MULTIPLIER,
                DEFAULT_L1_MAX_BYTES,
                DEFAULT_BLOOM_FALSE_POSITIVE_RATE,
                DEFAULT_SYNC_WRITES,
                DEFAULT_WAL_SYNC_MODE,
                DEFAULT_WAL_CORRUPTION_POLICY,
                DEFAULT_COMPACTION_INTERVAL_MS,
                DEFAULT_MAX_IMMUTABLE_COUNT,
                DEFAULT_TARGET_SSTABLE_SIZE_BYTES,
                DEFAULT_WAL_SEGMENT_SIZE_BYTES,
                DEFAULT_NUM_CACHE_SHARDS,
                DEFAULT_CHECKSUM_VERIFICATION);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
    }

    public enum WalSyncMode {
        SYNC,
        ASYNC
    }

    public enum WalCorruptionPolicy {
        STRICT,
        LENIENT
    }
}
