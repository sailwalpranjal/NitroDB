package com.nitrodb.bench;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class WriteBenchmark {

    /**
     * Sequence used to generate unique write keys across invocations.
     */
    private final AtomicLong sequence = new AtomicLong();

    /**
     * Measures steady-state sequential writes.
     *
     * @param state benchmark state backing the workload
     */
    @Benchmark
    public void sequentialPuts(final BenchmarkState state) {
        long value = sequence.incrementAndGet();
        state.db()
                .put(
                        ("key-" + value).getBytes(StandardCharsets.UTF_8),
                        ("value-" + value).getBytes(StandardCharsets.UTF_8));
    }
}
