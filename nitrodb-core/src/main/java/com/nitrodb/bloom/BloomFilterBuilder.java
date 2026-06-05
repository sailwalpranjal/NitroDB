package com.nitrodb.bloom;

public final class BloomFilterBuilder {

    private final BloomFilter filter;

    private BloomFilterBuilder(BloomFilter filter) {
        this.filter = filter;
    }

    public static BloomFilterBuilder build(int expectedKeys, double falsePositiveRate) {
        int keys = Math.max(1, expectedKeys);
        int bits = Math.max(64, (int) Math.ceil(-keys * Math.log(falsePositiveRate) / (Math.pow(Math.log(2), 2))));
        int hashFunctions = Math.max(1, (int) Math.round((double) bits / keys * Math.log(2)));
        int longs = (int) Math.ceil((double) bits / Long.SIZE);
        return new BloomFilterBuilder(new BloomFilter(new long[longs], hashFunctions, longs * Long.SIZE));
    }

    public void add(byte[] key) {
        filter.add(key);
    }

    public BloomFilter finish() {
        return new BloomFilter(filter.bitArray(), filter.numHashFunctions(), filter.bitArraySize());
    }
}
