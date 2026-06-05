package com.nitrodb.wal;

import com.nitrodb.io.FileManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class WalManager implements AutoCloseable {

    private final Path walDir;
    private final long segmentSizeBytes;
    private final FileManager fileManager;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Path, Long> maxSequencePerSegment = new ConcurrentHashMap<>();

    private WalSegment activeSegment;

    public WalManager(Path dataDir, long segmentSizeBytes, FileManager fileManager) {
        this.walDir = dataDir.resolve("wal");
        this.segmentSizeBytes = segmentSizeBytes;
        this.fileManager = fileManager;
    }

    public WalSegment activeSegment() {
        lock.lock();
        try {
            if (activeSegment == null) {
                activeSegment = new WalSegment(nextSegmentPath());
            }
            return activeSegment;
        } finally {
            lock.unlock();
        }
    }

    public void rotateSegment() {
        lock.lock();
        try {
            if (activeSegment != null) {
                activeSegment.force(false);
                activeSegment.close();
            }
            activeSegment = new WalSegment(nextSegmentPath());
        } finally {
            lock.unlock();
        }
    }

    public void recordAppend(Path segmentPath, long sequenceNumber) {
        maxSequencePerSegment.merge(segmentPath, sequenceNumber, Math::max);
    }

    public boolean shouldRotate(long bytesToAppend) {
        lock.lock();
        try {
            return activeSegment != null && activeSegment.size() + bytesToAppend > segmentSizeBytes;
        } finally {
            lock.unlock();
        }
    }

    public void markFlushed(long maxFlushedSeq) {
        lock.lock();
        try {
            for (Path segment : new ArrayList<>(listSegments())) {
                if (activeSegment != null && activeSegment.path().equals(segment)) {
                    continue;
                }
                long maxSeq = maxSequencePerSegment.getOrDefault(segment, Long.MAX_VALUE);
                if (maxSeq <= maxFlushedSeq) {
                    fileManager.deleteFile(segment);
                    maxSequencePerSegment.remove(segment);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public List<Path> unflushedSegments(long flushedSeq) {
        List<Path> result = new ArrayList<>();
        for (Path segment : listSegments()) {
            long maxSeq = maxSequencePerSegment.getOrDefault(segment, Long.MAX_VALUE);
            if (maxSeq > flushedSeq) {
                result.add(segment);
            }
        }
        return result;
    }

    public List<Path> listSegments() {
        return fileManager.listFiles(walDir, ".wal").stream()
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (activeSegment != null) {
                activeSegment.force(false);
                activeSegment.close();
                activeSegment = null;
            }
        } finally {
            lock.unlock();
        }
    }

    private Path nextSegmentPath() {
        try {
            Files.createDirectories(walDir);
        } catch (java.io.IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to create WAL directory " + walDir, e);
        }
        long nextId = listSegments().stream()
                .map(path -> path.getFileName().toString().replace(".wal", ""))
                .mapToLong(name -> Long.parseLong(name))
                .max()
                .orElse(0L) + 1L;
        return walDir.resolve("%020d.wal".formatted(nextId));
    }
}
