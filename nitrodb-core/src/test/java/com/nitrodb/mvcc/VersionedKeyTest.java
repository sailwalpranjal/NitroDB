package com.nitrodb.mvcc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionedKeyTest {

    @Test
    void newerSequenceSortsFirstForSameUserKey() {
        VersionedKey newer = new VersionedKey("key".getBytes(), 10L);
        VersionedKey older = new VersionedKey("key".getBytes(), 5L);

        assertTrue(newer.compareTo(older) < 0);
    }

    @Test
    void keysSortLexicographicallyAscending() {
        VersionedKey left = new VersionedKey("alpha".getBytes(), 1L);
        VersionedKey right = new VersionedKey("beta".getBytes(), 100L);

        assertTrue(left.compareTo(right) < 0);
    }
}
