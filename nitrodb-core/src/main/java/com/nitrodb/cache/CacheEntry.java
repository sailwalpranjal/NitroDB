package com.nitrodb.cache;

import java.nio.ByteBuffer;

public record CacheEntry(CacheKey key, ByteBuffer offHeapData, int sizeBytes, long accessTime) {
}
