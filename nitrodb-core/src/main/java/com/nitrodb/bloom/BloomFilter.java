package com.nitrodb.bloom;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

public final class BloomFilter {

    private final long[] bitArray;
    private final int numHashFunctions;
    private final int bitArraySize;

    public BloomFilter(long[] bitArray, int numHashFunctions, int bitArraySize) {
        this.bitArray = bitArray.clone();
        this.numHashFunctions = numHashFunctions;
        this.bitArraySize = bitArraySize;
    }

    public void add(byte[] key) {
        long hash1 = hash1(key);
        long hash2 = hash2(key, hash1);
        for (int i = 0; i < numHashFunctions; i++) {
            int position = Math.floorMod(hash1 + i * hash2, bitArraySize);
            setBit(position);
        }
    }

    public boolean mightContain(byte[] key) {
        long hash1 = hash1(key);
        long hash2 = hash2(key, hash1);
        for (int i = 0; i < numHashFunctions; i++) {
            int position = Math.floorMod(hash1 + i * hash2, bitArraySize);
            if (!testBit(position)) {
                return false;
            }
        }
        return true;
    }

    public double expectedFalsePositiveRate(long insertedKeys) {
        return Math.pow(1 - Math.exp(-(double) numHashFunctions * insertedKeys / bitArraySize), numHashFunctions);
    }

    public long[] bitArray() {
        return bitArray.clone();
    }

    public int numHashFunctions() {
        return numHashFunctions;
    }

    public int bitArraySize() {
        return bitArraySize;
    }

    private long hash1(byte[] key) {
        HashCode hashCode = Hashing.murmur3_128().hashBytes(key);
        return hashCode.asLong();
    }

    private long hash2(byte[] key, long seed) {
        return Hashing.murmur3_128((int) seed).hashBytes(key).asLong();
    }

    private void setBit(int position) {
        bitArray[position / Long.SIZE] |= 1L << (position % Long.SIZE);
    }

    private boolean testBit(int position) {
        return (bitArray[position / Long.SIZE] & (1L << (position % Long.SIZE))) != 0;
    }
}
