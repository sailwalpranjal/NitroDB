package com.nitrodb.manifest;

import com.nitrodb.sstable.SSTableMetadata;
import java.util.List;

public record ManifestEntry(
        Integer level,
        SSTableMetadata metadata,
        Long sequence,
        Long transactionId,
        List<SSTableMetadata> removeFiles,
        List<SSTableMetadata> addFiles) {

    public static ManifestEntry sstable(int level, SSTableMetadata metadata) {
        return new ManifestEntry(level, metadata, null, null, List.of(), List.of());
    }

    public static ManifestEntry flushedSequence(long sequence) {
        return new ManifestEntry(null, null, sequence, null, List.of(), List.of());
    }

    public static ManifestEntry compaction(long transactionId, List<SSTableMetadata> removeFiles, List<SSTableMetadata> addFiles) {
        return new ManifestEntry(null, null, null, transactionId, List.copyOf(removeFiles), List.copyOf(addFiles));
    }

    public static ManifestEntry transactionEnd(long transactionId) {
        return new ManifestEntry(null, null, null, transactionId, List.of(), List.of());
    }
}
