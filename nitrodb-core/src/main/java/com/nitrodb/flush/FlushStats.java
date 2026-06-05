package com.nitrodb.flush;

public record FlushStats(long bytesWritten, long durationMs, long entriesFlushed) {
}
