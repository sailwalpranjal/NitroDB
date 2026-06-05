package com.nitrodb.bloom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BloomFilterTest {

    @Test
    void rejectsKnownAbsentKey() {
        BloomFilterBuilder builder = BloomFilterBuilder.build(128, 0.01d);
        builder.add("alpha".getBytes());
        builder.add("beta".getBytes());
        BloomFilter filter = builder.finish();

        assertTrue(filter.mightContain("alpha".getBytes()));
        assertFalse(filter.mightContain("missing".getBytes()));
    }
}
