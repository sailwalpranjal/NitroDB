package com.nitrodb.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nitrodb.sstable.BlockHandle;
import org.junit.jupiter.api.Test;

class SparseIndexTest {

    @Test
    void returnsNearestBlockForLookupKey() {
        SparseIndexBuilder builder = new SparseIndexBuilder();
        builder.add("alpha".getBytes(), 100L, 32);
        builder.add("delta".getBytes(), 200L, 32);
        builder.add("omega".getBytes(), 300L, 32);
        SparseIndex index = SparseIndex.deserialize(builder.serialize());

        BlockHandle between = index.findBlock("gamma".getBytes());
        BlockHandle exact = index.findBlock("omega".getBytes());
        BlockHandle beforeFirst = index.findBlock("aardvark".getBytes());

        assertEquals(200L, between.offset());
        assertEquals(300L, exact.offset());
        assertNotNull(beforeFirst);
        assertEquals(100L, beforeFirst.offset());
        assertNull(SparseIndex.deserialize(new byte[0]).findBlock("anything".getBytes()));
    }
}
