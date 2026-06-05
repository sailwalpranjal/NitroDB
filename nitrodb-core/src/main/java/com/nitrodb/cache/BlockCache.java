package com.nitrodb.cache;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class BlockCache implements AutoCloseable {

    private final CacheShard[] shards;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();

    public BlockCache(long capacityBytes, int numShards) {
        OffHeapAllocator allocator = new OffHeapAllocator();
        long shardCapacity = Math.max(1L, capacityBytes / Math.max(1, numShards));
        this.shards = new CacheShard[Math.max(1, numShards)];
        for (int i = 0; i < shards.length; i++) {
            shards[i] = new CacheShard(shardCapacity, allocator);
        }
    }

    public Optional<ByteBuffer> get(CacheKey key) {
        Optional<ByteBuffer> buffer = shardFor(key).get(key);
        if (buffer.isPresent()) {
            hitCount.incrementAndGet();
        } else {
            missCount.incrementAndGet();
        }
        return buffer;
    }

    public void put(CacheKey key, ByteBuffer data) {
        shardFor(key).put(key, data);
    }

    public void evict(CacheKey key) {
        if (shardFor(key).evict(key)) {
            evictionCount.incrementAndGet();
        }
    }

    public void evictFile(String fileId) {
        for (CacheShard shard : shards) {
            evictionCount.addAndGet(shard.evictWhere(key -> key.fileId().equals(fileId)));
        }
    }

    public long sizeBytes() {
        long size = 0L;
        for (CacheShard shard : shards) {
            size += shard.sizeBytes();
        }
        return size;
    }

    public long hitCount() {
        return hitCount.get();
    }

    public long missCount() {
        return missCount.get();
    }

    public long evictionCount() {
        return evictionCount.get();
    }

    public long requestCount() {
        return hitCount() + missCount();
    }

    public long hitRatioPercent() {
        long requests = requestCount();
        if (requests == 0L) {
            return 0L;
        }
        return Math.round((hitCount() * 100.0d) / requests);
    }

    @Override
    public void close() {
        for (CacheShard shard : shards) {
            shard.close();
        }
    }

    private CacheShard shardFor(CacheKey key) {
        int index = Math.floorMod(key.hashCode(), shards.length);
        return shards[index];
    }
}
