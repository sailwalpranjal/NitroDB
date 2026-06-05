package com.nitrodb.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nitrodb.api.DBException.DatabaseLockException;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileManagerTest {

    private final FileManager fileManager = new FileManager();

    @TempDir
    Path tempDir;

    @Test
    void createTempFileCreatesFileUnderDirectory() {
        Path tempFile = fileManager.createTempFile(tempDir, "sst-", ".tmp");

        assertTrue(Files.exists(tempFile));
        assertEquals(tempDir, tempFile.getParent());
    }

    @Test
    void atomicRenameMovesFileToDestination() throws Exception {
        Path source = Files.writeString(tempDir.resolve("source.tmp"), "nitrodb");
        Path target = tempDir.resolve("target.sst");

        fileManager.atomicRename(source, target);

        assertTrue(Files.exists(target));
        assertFalse(Files.exists(source));
        assertEquals("nitrodb", Files.readString(target));
    }

    @Test
    void listFilesFiltersByExtension() throws Exception {
        Files.writeString(tempDir.resolve("001.sst"), "a");
        Files.writeString(tempDir.resolve("002.sst"), "b");
        Files.writeString(tempDir.resolve("003.wal"), "c");

        assertEquals(2, fileManager.listFiles(tempDir, ".sst").size());
    }

    @Test
    void acquireLockPreventsSecondConcurrentLock() {
        Path lockFile = tempDir.resolve("LOCK");
        FileLock lock = fileManager.acquireLock(lockFile);
        try {
            assertThrows(DatabaseLockException.class, () -> fileManager.acquireLock(lockFile));
        } finally {
            fileManager.releaseLock(lock);
        }
    }
}
