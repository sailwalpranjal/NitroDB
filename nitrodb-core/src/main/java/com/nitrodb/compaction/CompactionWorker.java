package com.nitrodb.compaction;

import com.nitrodb.DBConfig;
import com.nitrodb.cache.BlockCache;
import com.nitrodb.manifest.LevelMetadata;
import com.nitrodb.manifest.LevelMetadata;
import com.nitrodb.manifest.ManifestManager;
import com.nitrodb.memtable.MemTableEntry;
import com.nitrodb.mvcc.SnapshotManager;
import com.nitrodb.sstable.SSTableMetadata;
import com.nitrodb.sstable.SSTableReader;
import com.nitrodb.sstable.SSTableWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class CompactionWorker {

    private final Path dataDir;
    private final DBConfig config;
    private final ManifestManager manifestManager;
    private final SnapshotManager snapshotManager;
    private final BlockCache blockCache;
    private final Consumer<SSTableMetadata> onSstableCreated;
    private final Consumer<SSTableMetadata> onSstableRemoved;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong completedCompactions = new AtomicLong();
    private final AtomicLong compactedBytesRead = new AtomicLong();
    private final AtomicLong compactedBytesWritten = new AtomicLong();
    private final CompactionPlanner planner;
    private final AtomicBoolean activeCompaction = new AtomicBoolean();
    private final ReentrantLock compactionLock = new ReentrantLock();

    private Thread thread;

    public CompactionWorker(
            Path dataDir,
            DBConfig config,
            ManifestManager manifestManager,
            SnapshotManager snapshotManager,
            BlockCache blockCache,
            Consumer<SSTableMetadata> onSstableCreated,
            Consumer<SSTableMetadata> onSstableRemoved) {
        this.dataDir = dataDir;
        this.config = config;
        this.manifestManager = manifestManager;
        this.snapshotManager = snapshotManager;
        this.blockCache = blockCache;
        this.onSstableCreated = onSstableCreated;
        this.onSstableRemoved = onSstableRemoved;
        this.planner = new CompactionPlanner(config);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            thread = Thread.ofVirtual().name("nitrodb-compaction-worker").start(this::runLoop);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false) && thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void triggerNow() {
        compactOnceBlocking();
    }

    public void awaitIdle() {
        while (running.get() && activeCompaction.get()) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void runLoop() {
        while (running.get()) {
            compactOnceIfIdle();
            try {
                Thread.sleep(config.compactionIntervalMs());
            } catch (InterruptedException e) {
                if (!running.get()) {
                    return;
                }
                Thread.currentThread().interrupt();
            }
        }
    }

    private void compactOnceIfIdle() {
        if (!compactionLock.tryLock()) {
            return;
        }
        try {
            compactOnceLocked();
        } finally {
            compactionLock.unlock();
        }
    }

    private void compactOnceBlocking() {
        compactionLock.lock();
        try {
            compactOnceLocked();
        } finally {
            compactionLock.unlock();
        }
    }

    private void compactOnceLocked() {
        activeCompaction.set(true);
        try {
            compactInternal();
        } finally {
            activeCompaction.set(false);
        }
    }

    private void compactInternal() {
        List<LevelMetadata> levels = new ArrayList<>();
        for (int level = 0; level < config.maxLevels(); level++) {
            levels.add(manifestManager.getLevel(level));
        }
        Optional<CompactionJob> maybeJob = planner.selectCompaction(levels, snapshotManager.oldestActiveSequence());
        if (maybeJob.isEmpty()) {
            return;
        }
        CompactionJob job = maybeJob.orElseThrow();
        List<SSTableMetadata> inputs = new ArrayList<>(job.sourceFiles());
        inputs.addAll(job.targetFiles());
        List<SSTableReader> readers =
                inputs.stream().map(metadata -> SSTableReader.open(metadata.filePath(), config)).toList();
        SSTableMetadata output;
        try {
            List<com.nitrodb.iter.InternalIterator> iterators = readers.stream()
                    .map(reader -> reader.iterator(reader.metadata().minKey(), Long.MAX_VALUE))
                    .map(iterator -> (com.nitrodb.iter.InternalIterator) iterator)
                    .toList();
            try (MergeIterator iterator = new MergeIterator(iterators);
                    SSTableWriter writer = SSTableWriter.open(dataDir, job.targetLevel(), config)) {
                byte[] minKey = minKey(inputs);
                byte[] maxKey = maxKey(inputs);
                boolean canDropTombstones = !hasOverlappingFilesBelow(job.targetLevel(), minKey, maxKey);
                writeCompactedOutput(iterator, writer, job.minSeqForGc(), canDropTombstones);
                output = writer.finish();
            }
        } finally {
            readers.forEach(SSTableReader::close);
        }
        compactedBytesRead.addAndGet(inputs.stream().mapToLong(SSTableMetadata::fileSize).sum());
        compactedBytesWritten.addAndGet(output.fileSize());
        completedCompactions.incrementAndGet();
        manifestManager.applyCompaction(inputs, List.of(output));
        onSstableCreated.accept(output);
        for (SSTableMetadata input : inputs) {
            onSstableRemoved.accept(input);
            blockCache.evictFile(input.fileId());
            try {
                Files.deleteIfExists(input.filePath());
            } catch (java.io.IOException e) {
                throw new com.nitrodb.api.DBException.IOStorageException(
                        "Failed to delete compacted SSTable " + input.filePath(),
                        e);
            }
        }
    }

    public long completedCompactions() {
        return completedCompactions.get();
    }

    public long compactedBytesRead() {
        return compactedBytesRead.get();
    }

    public long compactedBytesWritten() {
        return compactedBytesWritten.get();
    }

    private void writeCompactedOutput(
            MergeIterator iterator,
            SSTableWriter writer,
            long minSeqForGc,
            boolean canDropTombstones) {
        MemTableEntry carry = null;
        while (carry != null || iterator.hasNext()) {
            MemTableEntry first = carry != null ? carry : iterator.next();
            carry = null;
            List<MemTableEntry> versions = new ArrayList<>();
            versions.add(first);
            while (iterator.hasNext()) {
                MemTableEntry candidate = iterator.next();
                if (!Arrays.equals(first.key(), candidate.key())) {
                    carry = candidate;
                    break;
                }
                versions.add(candidate);
            }
            for (MemTableEntry entry : compactVersions(versions, minSeqForGc, canDropTombstones)) {
                writer.add(entry.key(), entry.value(), entry.sequenceNumber(), entry.type());
            }
        }
    }

    private List<MemTableEntry> compactVersions(
            List<MemTableEntry> versions,
            long minSeqForGc,
            boolean canDropTombstones) {
        if (versions.isEmpty()) {
            return List.of();
        }
        List<MemTableEntry> retained = new ArrayList<>();
        MemTableEntry firstGcEligible = null;
        for (MemTableEntry version : versions) {
            if (version.sequenceNumber() > minSeqForGc) {
                retained.add(version);
            } else if (firstGcEligible == null) {
                firstGcEligible = version;
            }
        }
        if (firstGcEligible == null) {
            return retained;
        }
        if (firstGcEligible.isTombstone() && canDropTombstones) {
            return retained;
        }
        retained.add(firstGcEligible);
        return retained;
    }

    private boolean hasOverlappingFilesBelow(int targetLevel, byte[] minKey, byte[] maxKey) {
        for (int level = targetLevel + 1; level < config.maxLevels(); level++) {
            LevelMetadata metadata = manifestManager.getLevel(level);
            if (!metadata.overlapping(minKey, maxKey).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static byte[] minKey(List<SSTableMetadata> files) {
        return files.stream().map(SSTableMetadata::minKey).min(Arrays::compareUnsigned).orElse(new byte[0]);
    }

    private static byte[] maxKey(List<SSTableMetadata> files) {
        return files.stream().map(SSTableMetadata::maxKey).max(Arrays::compareUnsigned).orElse(new byte[0]);
    }
}
