package com.nitrodb.cache;

public record CacheKey(String fileId, long blockOffset) {
}
