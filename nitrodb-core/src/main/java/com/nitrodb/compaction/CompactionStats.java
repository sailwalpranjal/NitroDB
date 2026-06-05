package com.nitrodb.compaction;

public record CompactionStats(
        long bytesRead,
        long bytesWritten,
        long durationMs,
        int filesIn,
        int filesOut,
        int levelFrom,
        int levelTo) {
}
