package com.nitrodb.sstable;

import java.nio.file.Path;

public record SSTableMetadata(
        Path filePath,
        int level,
        long fileSize,
        byte[] minKey,
        byte[] maxKey,
        long entryCount,
        long minSeq,
        long maxSeq,
        long bloomOffset,
        int bloomLength,
        long indexOffset,
        int indexLength,
        String fileId) {
}
