package com.nitrodb.flush;

import com.nitrodb.DBConfig;
import com.nitrodb.manifest.ManifestManager;
import com.nitrodb.memtable.ImmutableMemTable;
import com.nitrodb.memtable.MemTableEntry;
import com.nitrodb.sstable.SSTableMetadata;
import com.nitrodb.sstable.SSTableWriter;
import com.nitrodb.wal.WalManager;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class FlushWorker {

    private final LinkedBlockingQueue<ImmutableMemTable> queue = new LinkedBlockingQueue<>();
    private final Path dataDir;
    private final DBConfig config;
    private final ManifestManager manifestManager;
    private final WalManager walManager;
    private final Consumer<SSTableMetadata> onSstableCreated;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger activeFlushes = new AtomicInteger();
    private Thread thread;

    public FlushWorker(
            Path dataDir,
            DBConfig config,
            ManifestManager manifestManager,
            WalManager walManager,
            Consumer<SSTableMetadata> onSstableCreated) {
        this.dataDir = dataDir;
        this.config = config;
        this.manifestManager = manifestManager;
        this.walManager = walManager;
        this.onSstableCreated = Objects.requireNonNull(onSstableCreated, "onSstableCreated");
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            thread = Thread.ofVirtual().name("nitrodb-flush-worker").start(this::runLoop);
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

    public void enqueue(ImmutableMemTable immutableMemTable) {
        queue.offer(immutableMemTable);
    }

    public void awaitIdle() {
        while (running.get() && (!queue.isEmpty() || activeFlushes.get() > 0)) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void runLoop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                ImmutableMemTable immutableMemTable = queue.poll(100L, TimeUnit.MILLISECONDS);
                if (immutableMemTable != null) {
                    activeFlushes.incrementAndGet();
                    try {
                        SSTableMetadata metadata = flushOne(immutableMemTable);
                        manifestManager.addSSTable(0, metadata);
                        manifestManager.setFlushedSequence(immutableMemTable.maxSeq());
                        walManager.markFlushed(immutableMemTable.maxSeq());
                        onSstableCreated.accept(metadata);
                    } finally {
                        activeFlushes.decrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                if (!running.get()) {
                    return;
                }
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                if (!running.get()) {
                    return;
                }
                throw e;
            }
        }
    }

    private SSTableMetadata flushOne(ImmutableMemTable immutableMemTable) {
        try (SSTableWriter writer = SSTableWriter.open(dataDir, 0, config)) {
            var iterator = immutableMemTable.iterator();
            while (iterator.hasNext()) {
                MemTableEntry entry = iterator.next();
                writer.add(entry.key(), entry.value(), entry.sequenceNumber(), entry.type());
            }
            iterator.close();
            return writer.finish();
        }
    }
}
