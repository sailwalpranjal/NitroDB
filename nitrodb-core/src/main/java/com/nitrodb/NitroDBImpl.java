package com.nitrodb;

import com.google.common.primitives.UnsignedBytes;
import com.nitrodb.api.Entry;
import com.nitrodb.api.ReadOptions;
import com.nitrodb.api.ScanResult;
import com.nitrodb.api.WriteOptions;
import com.nitrodb.cache.BlockCache;
import com.nitrodb.compaction.CompactionWorker;
import com.nitrodb.flush.FlushWorker;
import com.nitrodb.io.FileManager;
import com.nitrodb.iter.InternalIterator;
import com.nitrodb.iter.IteratorUtils;
import com.nitrodb.iter.MergingIterator;
import com.nitrodb.manifest.ManifestManager;
import com.nitrodb.memtable.ImmutableMemTable;
import com.nitrodb.memtable.MemTable;
import com.nitrodb.memtable.MemTableEntry;
import com.nitrodb.metrics.DefaultMetricsSink;
import com.nitrodb.metrics.MetricsSnapshot;
import com.nitrodb.metrics.MetricsSink;
import com.nitrodb.metrics.MetricsReporter;
import com.nitrodb.metrics.NitroDBMetrics;
import com.nitrodb.mvcc.SequenceGenerator;
import com.nitrodb.mvcc.Snapshot;
import com.nitrodb.mvcc.SnapshotManager;
import com.nitrodb.recovery.RecoveryManager;
import com.nitrodb.sstable.SSTableMetadata;
import com.nitrodb.sstable.SSTableReader;
import com.nitrodb.wal.WalManager;
import com.nitrodb.wal.WalRecord;
import com.nitrodb.wal.WalWriter;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public final class NitroDBImpl implements NitroDB {

    private static final Comparator<byte[]> KEY_COMPARATOR = UnsignedBytes.lexicographicalComparator();

    private final DBConfig config;
    private final FileManager fileManager = new FileManager();
    private final SequenceGenerator sequenceGenerator = new SequenceGenerator();
    private final SnapshotManager snapshotManager = new SnapshotManager();
    private final ManifestManager manifestManager = new ManifestManager();
    private final BlockCache blockCache;
    private final WalManager walManager;
    private final WalWriter walWriter;
    private final AtomicReference<MemTable> activeMemTable;
    private final CopyOnWriteArrayList<ImmutableMemTable> immutableMemTables = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedDeque<ImmutableMemTable> flushOrder = new ConcurrentLinkedDeque<>();
    private final Map<String, SSTableReader> sstableReaders = new ConcurrentHashMap<>();
    private final ReentrantLock[] stripeLocks = new ReentrantLock[64];
    private final ReentrantLock rotationLock = new ReentrantLock();
    private final FileLock dbLock;
    private final FlushWorker flushWorker;
    private final CompactionWorker compactionWorker;
    private final NitroDBMetrics metrics = new NitroDBMetrics();
    private final MetricsReporter metricsReporter;

    public NitroDBImpl(DBConfig config) {
        this(config, DefaultMetricsSink.create());
    }

    public NitroDBImpl(DBConfig config, MetricsSink metricsSink) {
        this.config = Objects.requireNonNull(config, "config");
        this.metricsReporter = new MetricsReporter(metrics, Objects.requireNonNull(metricsSink, "metricsSink"));
        for (int i = 0; i < stripeLocks.length; i++) {
            stripeLocks[i] = new ReentrantLock();
        }
        this.dbLock = fileManager.acquireLock(config.dataDir().resolve("LOCK"));
        this.manifestManager.open(config.dataDir());
        this.blockCache = new BlockCache(config.blockCacheSizeBytes(), config.numCacheShards());
        this.walManager = new WalManager(config.dataDir(), config.walSegmentSizeBytes(), fileManager);
        RecoveryManager recoveryManager = new RecoveryManager(manifestManager, walManager, sequenceGenerator);
        RecoveryManager.RecoveryResult recovery = recoveryManager.recover(config.dataDir(), config);
        this.activeMemTable = new AtomicReference<>(recovery.recoveredMemTable());
        loadExistingSstables();
        this.walWriter = new WalWriter(walManager);
        this.flushWorker = new FlushWorker(config.dataDir(), config, manifestManager, walManager, this::handleFlushedSstable);
        this.compactionWorker = new CompactionWorker(
                config.dataDir(),
                config,
                manifestManager,
                snapshotManager,
                blockCache,
                this::registerSstable,
                this::unregisterSstable);
        metrics.gauge("memtable.size.bytes", () -> activeMemTable.get().sizeBytes());
        metrics.gauge("sstable.count.l0", () -> (long) manifestManager.getLevel(0).fileCount());
        metrics.gauge("cache.size.bytes", blockCache::sizeBytes);
        metrics.gauge("cache.hit.ratio.percent", blockCache::hitRatioPercent);
        metrics.gauge("cache.hits", blockCache::hitCount);
        metrics.gauge("cache.misses", blockCache::missCount);
        metrics.gauge("compactions.completed", compactionWorker::completedCompactions);
        metrics.gauge("compactions.bytes.read", compactionWorker::compactedBytesRead);
        metrics.gauge("compactions.bytes.written", compactionWorker::compactedBytesWritten);
        flushWorker.start();
        compactionWorker.start();
        metricsReporter.start();
    }

    @Override
    public void put(byte[] key, byte[] value) {
        put(key, value, WriteOptions.DEFAULT);
    }

    @Override
    public void put(byte[] key, byte[] value, WriteOptions options) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        long start = System.nanoTime();
        ReentrantLock stripeLock = acquireStripeLock(key);
        stripeLock.lock();
        try {
            long sequence = sequenceGenerator.next();
            walWriter.append(new WalRecord(WalRecord.RecordType.PUT, key, value, sequence));
            if (shouldSyncWrite(options)) {
                walWriter.sync();
            }
            activeMemTable.get().put(key, value, sequence);
            checkMemTableThreshold();
            metrics.counter("writes.total").increment();
            metrics.histogram("writes.latency.us").record(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start));
        } finally {
            stripeLock.unlock();
        }
    }

    @Override
    public Optional<byte[]> get(byte[] key) {
        return get(key, ReadOptions.DEFAULT);
    }

    @Override
    public Optional<byte[]> get(byte[] key, ReadOptions options) {
        Objects.requireNonNull(key, "key");
        long start = System.nanoTime();
        long maxSeq = options != null && options.hasSnapshot()
                ? options.snapshot().sequenceNumber()
                : sequenceGenerator.current();
        Optional<MemTableEntry> inMemory = activeMemTable.get().get(key, maxSeq);
        if (inMemory.isEmpty()) {
            for (ImmutableMemTable immutable : immutableMemTables) {
                inMemory = immutable.get(key, maxSeq);
                if (inMemory.isPresent()) {
                    break;
                }
            }
        }
        if (inMemory.isPresent()) {
            metrics.counter("reads.total").increment();
            metrics.histogram("reads.latency.us").record(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start));
            return inMemory.filter(entry -> !entry.isTombstone()).map(MemTableEntry::value);
        }
        Optional<MemTableEntry> entry = searchSSTables(key, maxSeq);
        metrics.counter("reads.total").increment();
        metrics.histogram("reads.latency.us").record(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start));
        return entry.filter(found -> !found.isTombstone()).map(MemTableEntry::value);
    }

    @Override
    public void delete(byte[] key) {
        Objects.requireNonNull(key, "key");
        long start = System.nanoTime();
        ReentrantLock stripeLock = acquireStripeLock(key);
        stripeLock.lock();
        try {
            long sequence = sequenceGenerator.next();
            walWriter.append(new WalRecord(WalRecord.RecordType.DELETE, key, null, sequence));
            if (config.syncWrites()) {
                walWriter.sync();
            }
            activeMemTable.get().putTombstone(key, sequence);
            checkMemTableThreshold();
            metrics.counter("writes.total").increment();
            metrics.counter("deletes.total").increment();
            metrics.histogram("writes.latency.us").record(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start));
        } finally {
            stripeLock.unlock();
        }
    }

    @Override
    public ScanResult scan(byte[] startKey, byte[] endKey) {
        return scan(startKey, endKey, ReadOptions.DEFAULT);
    }

    @Override
    public ScanResult scan(byte[] startKey, byte[] endKey, ReadOptions options) {
        byte[] safeStart = startKey == null ? new byte[0] : startKey.clone();
        byte[] safeEnd = endKey == null ? null : endKey.clone();
        long maxSeq = options != null && options.hasSnapshot()
                ? options.snapshot().sequenceNumber()
                : sequenceGenerator.current();
        List<InternalIterator> iterators = new ArrayList<>();
        iterators.add(activeMemTable.get().iterator(safeStart, maxSeq, safeEnd));
        for (ImmutableMemTable immutable : immutableMemTables) {
            iterators.add(immutable.iterator(safeStart, maxSeq, safeEnd));
        }
        for (SSTableReader reader : orderedReadersForScan()) {
            iterators.add(reader.iterator(safeStart, maxSeq));
        }
        InternalIterator merged = IteratorUtils.skippingTombstones(IteratorUtils.deduplicating(new MergingIterator(iterators)));
        Iterator<Entry> iterator = new Iterator<>() {
            private MemTableEntry next;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }
                while (merged.hasNext()) {
                    MemTableEntry candidate = merged.next();
                    if (KEY_COMPARATOR.compare(candidate.key(), safeStart) < 0) {
                        continue;
                    }
                    if (safeEnd != null && KEY_COMPARATOR.compare(candidate.key(), safeEnd) >= 0) {
                        return false;
                    }
                    next = candidate;
                    return true;
                }
                return false;
            }

            @Override
            public Entry next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                Entry current = new Entry(next.key(), next.value(), next.sequenceNumber());
                next = null;
                return current;
            }
        };
        return new ScanResult(iterator, merged::close);
    }

    @Override
    public Snapshot getSnapshot() {
        return snapshotManager.create(sequenceGenerator.current());
    }

    @Override
    public void close() {
        metricsReporter.stop();
        compactionWorker.stop();
        if (activeMemTable.get().entryCount() > 0) {
            rotateMemTable(true);
        }
        flushWorker.awaitIdle();
        flushWorker.stop();
        sstableReaders.values().forEach(SSTableReader::close);
        walWriter.close();
        manifestManager.close();
        blockCache.close();
        fileManager.releaseLock(dbLock);
    }

    private void checkMemTableThreshold() {
        if (activeMemTable.get().sizeBytes() >= config.memTableSizeBytes()) {
            rotateMemTable(false);
        }
        while (immutableMemTables.size() > config.maxImmutableCount()) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void rotateMemTable(boolean force) {
        rotationLock.lock();
        try {
            MemTable current = activeMemTable.get();
            if (!force && current.sizeBytes() < config.memTableSizeBytes()) {
                return;
            }
            if (current.entryCount() == 0) {
                return;
            }
            MemTable replacement = new MemTable();
            if (!activeMemTable.compareAndSet(current, replacement)) {
                return;
            }
            ImmutableMemTable immutable = current.freeze();
            immutableMemTables.add(0, immutable);
            flushOrder.addLast(immutable);
            flushWorker.enqueue(immutable);
        } finally {
            rotationLock.unlock();
        }
    }

    private ReentrantLock acquireStripeLock(byte[] key) {
        int hash = java.util.Arrays.hashCode(key);
        return stripeLocks[Math.floorMod(hash, stripeLocks.length)];
    }

    private Optional<MemTableEntry> searchSSTables(byte[] key, long maxSeq) {
        for (SSTableReader reader : orderedReadersForLookup()) {
            Optional<MemTableEntry> entry = reader.getEntry(key, maxSeq);
            if (entry.isPresent()) {
                return entry;
            }
        }
        return Optional.empty();
    }

    private List<SSTableReader> orderedReadersForLookup() {
        return sstableReaders.values().stream()
                .sorted(Comparator
                        .comparingInt((SSTableReader reader) -> reader.metadata().level())
                        .thenComparing((left, right) -> Long.compare(right.metadata().maxSeq(), left.metadata().maxSeq())))
                .toList();
    }

    private List<SSTableReader> orderedReadersForScan() {
        return sstableReaders.values().stream()
                .sorted(Comparator
                        .comparingInt((SSTableReader reader) -> reader.metadata().level())
                        .thenComparing((left, right) -> Long.compare(right.metadata().maxSeq(), left.metadata().maxSeq())))
                .toList();
    }

    private void loadExistingSstables() {
        for (SSTableMetadata metadata : manifestManager.getAllSSTables()) {
            registerSstable(metadata);
        }
    }

    private void handleFlushedSstable(SSTableMetadata metadata) {
        registerSstable(metadata);
        ImmutableMemTable flushed = flushOrder.pollFirst();
        if (flushed != null) {
            immutableMemTables.remove(flushed);
        }
    }

    private void registerSstable(SSTableMetadata metadata) {
        sstableReaders.put(metadata.fileId(), SSTableReader.open(metadata.filePath(), config, blockCache));
    }

    private void unregisterSstable(SSTableMetadata metadata) {
        SSTableReader reader = sstableReaders.remove(metadata.fileId());
        if (reader != null) {
            reader.close();
        }
    }

    void awaitBackgroundWorkForTesting() {
        flushWorker.awaitIdle();
        compactionWorker.awaitIdle();
    }

    void triggerCompactionForTesting() {
        compactionWorker.triggerNow();
    }

    int sstableCountForTesting() {
        return sstableReaders.size();
    }

    long blockCacheSizeForTesting() {
        return blockCache.sizeBytes();
    }

    long blockCacheHitCountForTesting() {
        return blockCache.hitCount();
    }

    long blockCacheMissCountForTesting() {
        return blockCache.missCount();
    }

    MetricsSnapshot metricsSnapshotForTesting() {
        return metrics.snapshot();
    }

    int sstableCountForLevelTesting(int level) {
        return (int) sstableReaders.values().stream()
                .filter(reader -> reader.metadata().level() == level)
                .count();
    }

    long versionCountForKeyInSstablesTesting(byte[] key) {
        long count = 0L;
        for (SSTableReader reader : orderedReadersForLookup()) {
            try (InternalIterator iterator = reader.iterator(key, Long.MAX_VALUE)) {
                while (iterator.hasNext()) {
                    MemTableEntry entry = iterator.next();
                    if (!java.util.Arrays.equals(entry.key(), key)) {
                        break;
                    }
                    count++;
                }
            }
        }
        return count;
    }

    long tombstoneCountForKeyInSstablesTesting(byte[] key) {
        long count = 0L;
        for (SSTableReader reader : orderedReadersForLookup()) {
            try (InternalIterator iterator = reader.iterator(key, Long.MAX_VALUE)) {
                while (iterator.hasNext()) {
                    MemTableEntry entry = iterator.next();
                    if (!java.util.Arrays.equals(entry.key(), key)) {
                        break;
                    }
                    if (entry.isTombstone()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    void simulateCrashForTesting() {
        metricsReporter.stop();
        compactionWorker.stop();
        flushWorker.stop();
        sstableReaders.values().forEach(SSTableReader::close);
        walWriter.close();
        manifestManager.close();
        blockCache.close();
        fileManager.releaseLock(dbLock);
    }

    private boolean shouldSyncWrite(WriteOptions options) {
        if (options != null && options.hasSyncOverride()) {
            return options.syncMode() == WriteOptions.SyncMode.SYNC;
        }
        return config.syncWrites() || config.walSyncMode() == DBConfig.WalSyncMode.SYNC;
    }
}
