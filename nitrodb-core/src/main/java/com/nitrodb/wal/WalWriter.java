package com.nitrodb.wal;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

public final class WalWriter implements AutoCloseable {

    private final WalManager walManager;
    private final ReentrantLock writeLock = new ReentrantLock();

    public WalWriter(WalManager walManager) {
        this.walManager = walManager;
    }

    public void append(WalRecord record) {
        writeLock.lock();
        try {
            byte[] encoded = encodeRecord(record);
            if (walManager.shouldRotate(encoded.length)) {
                walManager.rotateSegment();
            }
            WalSegment segment = walManager.activeSegment();
            segment.write(encoded);
            walManager.recordAppend(segment.path(), record.sequenceNumber());
        } finally {
            writeLock.unlock();
        }
    }

    public void sync() {
        writeLock.lock();
        try {
            walManager.activeSegment().force(false);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        writeLock.lock();
        try {
            walManager.close();
        } finally {
            writeLock.unlock();
        }
    }

    private byte[] encodeRecord(WalRecord record) {
        byte[] key = record.key();
        byte[] value = record.value();
        int keyLength = key == null ? 0 : key.length;
        int valueLength = value == null ? 0 : value.length;
        ByteBuffer payload = ByteBuffer.allocate(1 + Long.BYTES + Integer.BYTES + Integer.BYTES + keyLength + valueLength);
        payload.put((byte) record.type().ordinal());
        payload.putLong(record.sequenceNumber());
        payload.putInt(keyLength);
        payload.putInt(valueLength);
        if (keyLength > 0) {
            payload.put(key);
        }
        if (valueLength > 0) {
            payload.put(value);
        }
        byte[] bytes = payload.array();
        int crc = com.nitrodb.io.ChecksumUtil.compute(bytes);
        ByteBuffer frame = ByteBuffer.allocate(WalConstants.FRAME_HEADER_SIZE + bytes.length);
        frame.putInt(crc);
        frame.putInt(bytes.length);
        frame.put(bytes);
        return frame.array();
    }
}
