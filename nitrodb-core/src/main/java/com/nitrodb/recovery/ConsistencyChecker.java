package com.nitrodb.recovery;

import com.nitrodb.manifest.ManifestManager;
import com.nitrodb.sstable.SSTableMetadata;
import com.nitrodb.sstable.SSTableReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ConsistencyChecker {

    public ConsistencyReport check(Path dataDir, ManifestManager manifest) {
        List<Path> deletedTempFiles = new ArrayList<>();
        deleteTempFiles(dataDir.resolve("sst"), deletedTempFiles);
        deleteTempFiles(dataDir.resolve("wal"), deletedTempFiles);
        List<SSTableMetadata> missing = new ArrayList<>();
        for (SSTableMetadata metadata : manifest.getAllSSTables()) {
            if (!Files.exists(metadata.filePath())) {
                missing.add(metadata);
                continue;
            }
            try (SSTableReader ignored = SSTableReader.open(metadata.filePath(), com.nitrodb.DBConfig.defaults(dataDir))) {
                // open validates format
            }
        }
        return new ConsistencyReport(missing, deletedTempFiles);
    }

    private void deleteTempFiles(Path dir, List<Path> deletedTempFiles) {
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (var stream = Files.list(dir)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".tmp")).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                        deletedTempFiles.add(path);
                    } catch (IOException e) {
                        throw new com.nitrodb.api.DBException.IOStorageException("Failed to delete temp file " + path, e);
                    }
                });
            }
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed during consistency check", e);
        }
    }

    public record ConsistencyReport(List<SSTableMetadata> missingSstables, List<Path> deletedTempFiles) {
    }
}
