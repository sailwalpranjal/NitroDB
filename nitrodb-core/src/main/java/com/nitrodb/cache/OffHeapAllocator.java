package com.nitrodb.cache;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public final class OffHeapAllocator {

    private final AtomicLong totalAllocatedBytes = new AtomicLong();

    public ByteBuffer allocate(int size) {
        totalAllocatedBytes.addAndGet(size);
        return ByteBuffer.allocateDirect(size);
    }

    public void release(ByteBuffer buffer) {
        if (buffer != null) {
            totalAllocatedBytes.addAndGet(-buffer.capacity());
        }
    }

    public long totalAllocatedBytes() {
        return totalAllocatedBytes.get();
    }
}
