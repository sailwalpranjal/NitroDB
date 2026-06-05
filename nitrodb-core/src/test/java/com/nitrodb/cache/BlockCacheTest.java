package com.nitrodb.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class BlockCacheTest {

    @Test
    void putGetAndEvictWorks() {
        BlockCache cache = new BlockCache(1024, 2);
        CacheKey key = new CacheKey("file", 1L);
        cache.put(key, ByteBuffer.wrap("value".getBytes()));

        ByteBuffer buffer = cache.get(key).orElseThrow();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        assertArrayEquals("value".getBytes(), bytes);
        assertEquals(1L, cache.hitCount());
        assertEquals(0L, cache.missCount());

        cache.evict(key);
        assertFalse(cache.get(key).isPresent());
        assertEquals(1L, cache.missCount());
    }

    @Test
    void evictFileRemovesMatchingBlocksOnly() {
        BlockCache cache = new BlockCache(1024, 2);
        cache.put(new CacheKey("file-a", 1L), ByteBuffer.wrap("a".getBytes()));
        cache.put(new CacheKey("file-b", 1L), ByteBuffer.wrap("b".getBytes()));

        cache.evictFile("file-a");

        assertFalse(cache.get(new CacheKey("file-a", 1L)).isPresent());
        assertTrue(cache.get(new CacheKey("file-b", 1L)).isPresent());
    }
}
