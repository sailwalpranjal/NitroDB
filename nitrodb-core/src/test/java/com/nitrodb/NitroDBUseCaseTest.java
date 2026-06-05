package com.nitrodb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nitrodb.api.Entry;
import com.nitrodb.api.ReadOptions;
import com.nitrodb.metrics.NoOpMetricsSink;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NitroDBUseCaseTest {

    @TempDir
    Path tempDir;

    @Test
    void applicationSettingsStoragePersistsAcrossRestart() {
        Path dataDir = tempDir.resolve("settings-store");
        try (NitroDB db = builder(dataDir).build()) {
            db.put(bytes("settings/theme"), bytes("dark"));
            db.put(bytes("settings/language"), bytes("en-IN"));
        }

        try (NitroDB db = builder(dataDir).build()) {
            assertArrayEquals(bytes("dark"), db.get(bytes("settings/theme")).orElseThrow());
            assertArrayEquals(bytes("en-IN"), db.get(bytes("settings/language")).orElseThrow());
        }
    }

    @Test
    void userPreferenceStorageSupportsSnapshotReads() {
        try (NitroDB db = builder(tempDir.resolve("preferences-store")).build()) {
            db.put(bytes("user:42:timezone"), bytes("UTC"));

            try (var snapshot = db.getSnapshot()) {
                db.put(bytes("user:42:timezone"), bytes("Asia/Kolkata"));
                assertArrayEquals(
                        bytes("UTC"),
                        db.get(bytes("user:42:timezone"), new ReadOptions(snapshot)).orElseThrow());
            }

            assertArrayEquals(bytes("Asia/Kolkata"), db.get(bytes("user:42:timezone")).orElseThrow());
        }
    }

    @Test
    void localEmbeddedDatabaseSupportsRangeScans() {
        try (NitroDB db = builder(tempDir.resolve("embedded-db")).build()) {
            db.put(bytes("project:001:name"), bytes("NitroDB"));
            db.put(bytes("project:001:owner"), bytes("engineer"));
            db.put(bytes("project:002:name"), bytes("Other"));

            List<String> keys = new ArrayList<>();
            try (var scan = db.scan(bytes("project:001"), bytes("project:002"))) {
                for (Entry entry : scan) {
                    keys.add(text(entry.key()));
                }
            }

            assertEquals(List.of("project:001:name", "project:001:owner"), keys);
        }
    }

    @Test
    void metadataStoreSupportsOverwriteAndDelete() {
        try (NitroDB db = builder(tempDir.resolve("metadata-store")).build()) {
            db.put(bytes("file:manifest"), bytes("{\"version\":1}"));
            db.put(bytes("file:manifest"), bytes("{\"version\":2}"));
            assertArrayEquals(bytes("{\"version\":2}"), db.get(bytes("file:manifest")).orElseThrow());

            db.delete(bytes("file:manifest"));
            assertFalse(db.get(bytes("file:manifest")).isPresent());
        }
    }

    @Test
    void cachePersistenceLayerRetainsEntriesAcrossRestart() {
        Path dataDir = tempDir.resolve("cache-store");
        try (NitroDB db = builder(dataDir).build()) {
            db.put(bytes("cache:session:abc"), bytes("{\"user\":\"alice\"}"));
        }

        try (NitroDB db = builder(dataDir).build()) {
            assertArrayEquals(bytes("{\"user\":\"alice\"}"), db.get(bytes("cache:session:abc")).orElseThrow());
        }
    }

    @Test
    void lightweightKeyValueEngineHandlesSmallBulkWorkload() {
        try (NitroDB db = builder(tempDir.resolve("kv-engine")).build()) {
            for (int i = 0; i < 100; i++) {
                db.put(bytes("kv:%03d".formatted(i)), bytes("value-%03d".formatted(i)));
            }

            assertArrayEquals(bytes("value-042"), db.get(bytes("kv:042")).orElseThrow());
            assertTrue(db.get(bytes("kv:099")).isPresent());

            db.delete(bytes("kv:042"));
            assertFalse(db.get(bytes("kv:042")).isPresent());
        }
    }

    private NitroDBBuilder builder(Path dataDir) {
        return new NitroDBBuilder()
                .dataDir(dataDir)
                .memTableSize(256)
                .metricsSink(NoOpMetricsSink.INSTANCE);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
