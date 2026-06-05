package com.nitrodb.bloom;

import java.nio.ByteBuffer;

public final class BloomFilterSerializer {

    public byte[] serialize(BloomFilter filter) {
        long[] bitArray = filter.bitArray();
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 3 + Long.BYTES * bitArray.length);
        buffer.putInt(filter.numHashFunctions());
        buffer.putInt(filter.bitArraySize());
        buffer.putInt(bitArray.length);
        for (long value : bitArray) {
            buffer.putLong(value);
        }
        return buffer.array();
    }

    public BloomFilter deserialize(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int hashFunctions = buffer.getInt();
        int bitArraySize = buffer.getInt();
        int arrayLength = buffer.getInt();
        long[] bitArray = new long[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            bitArray[i] = buffer.getLong();
        }
        return new BloomFilter(bitArray, hashFunctions, bitArraySize);
    }
}
