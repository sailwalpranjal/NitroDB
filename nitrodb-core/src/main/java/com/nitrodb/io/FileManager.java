package com.nitrodb.io;

import com.nitrodb.api.DBException.DatabaseLockException;
import com.nitrodb.api.DBException.IOStorageException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FileManager {

    private final Map<FileLock, FileChannel> heldLocks = new ConcurrentHashMap<>();

    public Path createTempFile(Path dir, String prefix, String suffix) {
        try {
            Files.createDirectories(dir);
            return Files.createTempFile(dir, prefix, suffix);
        } catch (IOException e) {
            throw new IOStorageException("Failed to create temp file in " + dir, e);
        }
    }

    public void atomicRename(Path src, Path dst) {
        try {
            Path parent = dst.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IOStorageException("Failed to atomically rename " + src + " to " + dst, e);
        }
    }

    public void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IOStorageException("Failed to delete file " + path, e);
        }
    }

    public List<Path> listFiles(Path dir, String extension) {
        try {
            if (!Files.exists(dir)) {
                return List.of();
            }
            try (var stream = Files.list(dir)) {
                return stream
                        .filter(path -> path.getFileName().toString().endsWith(extension))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }
        } catch (IOException e) {
            throw new IOStorageException("Failed to list files in " + dir, e);
        }
    }

    public FileLock acquireLock(Path lockFile) {
        try {
            Path parent = lockFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            FileChannel channel = FileChannel.open(
                    lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new DatabaseLockException("Database lock already held for " + lockFile);
            }
            heldLocks.put(lock, channel);
            return lock;
        } catch (OverlappingFileLockException e) {
            throw new DatabaseLockException("Database lock already held for " + lockFile, e);
        } catch (IOException e) {
            throw new DatabaseLockException("Failed to acquire database lock for " + lockFile, e);
        }
    }

    public void releaseLock(FileLock lock) {
        FileChannel channel = heldLocks.remove(lock);
        IOException closeFailure = null;
        try {
            lock.release();
        } catch (IOException e) {
            closeFailure = e;
        }
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                if (closeFailure == null) {
                    closeFailure = e;
                } else {
                    closeFailure.addSuppressed(e);
                }
            }
        }
        if (closeFailure != null) {
            throw new IOStorageException("Failed to release database lock", closeFailure);
        }
    }
}
