package com.nitrodb.index;

public record SparseIndexEntry(byte[] firstKey, long blockOffset, int blockLength) {
}
