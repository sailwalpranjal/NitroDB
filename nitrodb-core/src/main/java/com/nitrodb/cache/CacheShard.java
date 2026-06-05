package com.nitrodb.cache;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class CacheShard {

    private final long capacityBytes;
    private final OffHeapAllocator allocator;
    private final LinkedHashMap<CacheKey, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private long currentSizeBytes;

    public CacheShard(long capacityBytes, OffHeapAllocator allocator) {
        this.capacityBytes = capacityBytes;
        this.allocator = allocator;
    }

    public Optional<ByteBuffer> get(CacheKey key) {
        lock.writeLock().lock();
        try {
            CacheEntry entry = entries.get(key);
            if (entry == null) {
                return Optional.empty();
            }
            return Optional.of(entry.offHeapData().duplicate().asReadOnlyBuffer());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(CacheKey key, ByteBuffer data) {
        lock.writeLock().lock();
        try {
            ByteBuffer copy = allocator.allocate(data.remaining());
            ByteBuffer duplicate = data.duplicate();
            copy.put(duplicate);
            copy.flip();
            CacheEntry previous = entries.remove(key);
            if (previous != null) {
                currentSizeBytes -= previous.sizeBytes();
                allocator.release(previous.offHeapData());
            }
            ensureCapacity(copy.remaining());
            CacheEntry entry = new CacheEntry(key, copy, copy.remaining(), System.nanoTime());
            entries.put(key, entry);
            currentSizeBytes += entry.sizeBytes();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean evict(CacheKey key) {
        lock.writeLock().lock();
        try {
            CacheEntry removed = entries.remove(key);
            if (removed != null) {
                currentSizeBytes -= removed.sizeBytes();
                allocator.release(removed.offHeapData());
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int evictWhere(Predicate<CacheKey> predicate) {
        lock.writeLock().lock();
        try {
            int removed = 0;
            var iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<CacheKey, CacheEntry> entry = iterator.next();
                if (predicate.test(entry.getKey())) {
                    currentSizeBytes -= entry.getValue().sizeBytes();
                    allocator.release(entry.getValue().offHeapData());
                    iterator.remove();
                    removed++;
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long sizeBytes() {
        lock.readLock().lock();
        try {
            return currentSizeBytes;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void close() {
        lock.writeLock().lock();
        try {
            for (CacheEntry entry : entries.values()) {
                allocator.release(entry.offHeapData());
            }
            entries.clear();
            currentSizeBytes = 0L;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void ensureCapacity(int needed) {
        var iterator = entries.entrySet().iterator();
        while (currentSizeBytes + needed > capacityBytes && iterator.hasNext()) {
            Map.Entry<CacheKey, CacheEntry> oldest = iterator.next();
            currentSizeBytes -= oldest.getValue().sizeBytes();
            allocator.release(oldest.getValue().offHeapData());
            iterator.remove();
        }
    }
}
