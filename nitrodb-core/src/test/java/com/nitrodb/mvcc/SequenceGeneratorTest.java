package com.nitrodb.mvcc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SequenceGeneratorTest {

    @Test
    void nextIsMonotonic() {
        SequenceGenerator generator = new SequenceGenerator();

        assertEquals(1L, generator.next());
        assertEquals(2L, generator.next());
        assertEquals(2L, generator.current());
    }

    @Test
    void setMinimumOnlyMovesForward() {
        SequenceGenerator generator = new SequenceGenerator();
        generator.setMinimum(42L);
        generator.setMinimum(5L);

        assertEquals(42L, generator.current());
        assertEquals(43L, generator.next());
    }

    @Test
    void concurrentNextProducesUniqueValues() throws Exception {
        SequenceGenerator generator = new SequenceGenerator();
        Set<Long> seen = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(8);
        try {
            for (int i = 0; i < 8; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < 250; j++) {
                        seen.add(generator.next());
                    }
                    latch.countDown();
                });
            }
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2_000, seen.size());
        assertEquals(2_000L, generator.current());
    }
}
