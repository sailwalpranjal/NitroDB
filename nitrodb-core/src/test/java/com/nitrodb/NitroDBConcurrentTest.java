package com.nitrodb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nitrodb.api.Entry;
import com.nitrodb.api.ReadOptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NitroDBConcurrentTest {

    @TempDir
    Path tempDir;

    @Test
    void rangeScanSnapshotRemainsStableDuringConcurrentWrites() throws Exception {
        NitroDBImpl db = (NitroDBImpl) new NitroDBBuilder().dataDir(tempDir).memTableSize(128).build();
        try {
            db.put(bytes("alpha"), bytes("1"));
            db.put(bytes("gamma"), bytes("3"));
            db.awaitBackgroundWorkForTesting();

            List<String> snapshotKeys;
            try (var snapshot = db.getSnapshot()) {
                ExecutorService executor = Executors.newFixedThreadPool(2);
                Future<?> writes = executor.submit(() -> {
                    db.put(bytes("beta"), bytes("2"));
                    db.put(bytes("delta"), bytes("4"));
                    db.delete(bytes("gamma"));
                });
                snapshotKeys = scanKeys(db, new ReadOptions(snapshot));
                writes.get();
                executor.shutdownNow();
            }

            assertEquals(List.of("alpha", "gamma"), snapshotKeys);
            assertEquals(List.of("alpha", "beta", "delta"), scanKeys(db, ReadOptions.DEFAULT));
        } finally {
            db.close();
        }
    }

    @Test
    void concurrentSingleKeyWritesRemainRecoverable() throws Exception {
        NitroDBImpl db = (NitroDBImpl) new NitroDBBuilder().dataDir(tempDir).memTableSize(256).build();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                final int index = i;
                tasks.add(() -> {
                    db.put(bytes("shared"), bytes("value-" + index));
                    assertTrue(db.get(bytes("shared")).isPresent());
                    return null;
                });
            }
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
            db.awaitBackgroundWorkForTesting();
        } finally {
            executor.shutdownNow();
            db.close();
        }

        try (NitroDB reopened = new NitroDBBuilder().dataDir(tempDir).build()) {
            assertTrue(reopened.get(bytes("shared")).isPresent());
            assertTrue(reopened.get(bytes("shared")).orElseThrow().length > 0);
        }
    }

    private static List<String> scanKeys(NitroDB db, ReadOptions options) {
        List<String> keys = new ArrayList<>();
        try (var scan = db.scan(bytes("alpha"), bytes("omega"), options)) {
            for (Entry entry : scan) {
                keys.add(new String(entry.key(), StandardCharsets.UTF_8));
            }
        }
        return keys;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
