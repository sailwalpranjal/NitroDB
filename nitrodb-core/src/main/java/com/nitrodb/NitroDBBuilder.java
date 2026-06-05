package com.nitrodb;

import com.nitrodb.metrics.DefaultMetricsSink;
import com.nitrodb.metrics.MetricsSink;

import java.nio.file.Path;

public final class NitroDBBuilder {

    private Path dataDir = Path.of("data");
    private long memTableSize = DBConfig.DEFAULT_MEMTABLE_SIZE_BYTES;
    private long blockCacheSize = DBConfig.DEFAULT_BLOCK_CACHE_SIZE_BYTES;
    private double bloomFalsePositiveRate = DBConfig.DEFAULT_BLOOM_FALSE_POSITIVE_RATE;
    private boolean syncWrites = DBConfig.DEFAULT_SYNC_WRITES;
    private DBConfig.WalCorruptionPolicy walCorruptionPolicy = DBConfig.DEFAULT_WAL_CORRUPTION_POLICY;
    private MetricsSink metricsSink = DefaultMetricsSink.create();

    public NitroDBBuilder dataDir(Path dir) {
        this.dataDir = dir;
        return this;
    }

    public NitroDBBuilder memTableSize(long bytes) {
        this.memTableSize = bytes;
        return this;
    }

    public NitroDBBuilder blockCacheSize(long bytes) {
        this.blockCacheSize = bytes;
        return this;
    }

    public NitroDBBuilder bloomFalsePositiveRate(double rate) {
        this.bloomFalsePositiveRate = rate;
        return this;
    }

    public NitroDBBuilder syncWrites(boolean sync) {
        this.syncWrites = sync;
        return this;
    }

    public NitroDBBuilder walCorruptionPolicy(DBConfig.WalCorruptionPolicy policy) {
        this.walCorruptionPolicy = policy;
        return this;
    }

    public NitroDBBuilder metricsSink(MetricsSink sink) {
        this.metricsSink = sink;
        return this;
    }

    public NitroDB build() {
        DBConfig config = new DBConfig(
                dataDir,
                memTableSize,
                blockCacheSize,
                DBConfig.DEFAULT_BLOCK_SIZE_BYTES,
                DBConfig.DEFAULT_MAX_LEVELS,
                DBConfig.DEFAULT_LEVEL_SIZE_MULTIPLIER,
                DBConfig.DEFAULT_L1_MAX_BYTES,
                bloomFalsePositiveRate,
                syncWrites,
                syncWrites ? DBConfig.WalSyncMode.SYNC : DBConfig.WalSyncMode.ASYNC,
                walCorruptionPolicy,
                DBConfig.DEFAULT_COMPACTION_INTERVAL_MS,
                DBConfig.DEFAULT_MAX_IMMUTABLE_COUNT,
                DBConfig.DEFAULT_TARGET_SSTABLE_SIZE_BYTES,
                DBConfig.DEFAULT_WAL_SEGMENT_SIZE_BYTES,
                DBConfig.DEFAULT_NUM_CACHE_SHARDS,
                DBConfig.DEFAULT_CHECKSUM_VERIFICATION);
        return new NitroDBImpl(config, metricsSink);
    }
}
