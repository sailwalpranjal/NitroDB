package com.nitrodb.mvcc;

import java.util.Arrays;

public record VersionedKey(byte[] userKey, long sequenceNumber) implements Comparable<VersionedKey> {

    public VersionedKey {
        userKey = Arrays.copyOf(userKey, userKey.length);
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be >= 0");
        }
    }

    @Override
    public byte[] userKey() {
        return Arrays.copyOf(userKey, userKey.length);
    }

    @Override
    public int compareTo(VersionedKey other) {
        int keyComparison = compareUnsigned(userKey, other.userKey);
        if (keyComparison != 0) {
            return keyComparison;
        }
        return Long.compare(other.sequenceNumber, sequenceNumber);
    }

    private static int compareUnsigned(byte[] left, byte[] right) {
        int minLength = Math.min(left.length, right.length);
        for (int i = 0; i < minLength; i++) {
            int cmp = Integer.compare(Byte.toUnsignedInt(left[i]), Byte.toUnsignedInt(right[i]));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(left.length, right.length);
    }
}
