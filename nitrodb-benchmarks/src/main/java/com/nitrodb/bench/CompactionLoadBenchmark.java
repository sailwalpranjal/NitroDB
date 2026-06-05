package com.nitrodb.bench;

import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;

public class CompactionLoadBenchmark {

    private static final int WRITES_PER_INVOCATION = 1_000;

    /**
     * Produces sustained write pressure that triggers flushes and compactions.
     *
     * @param state benchmark state backing the workload
     */
    @Benchmark
    public void writeLoad(final BenchmarkState state) {
        for (int i = 0; i < WRITES_PER_INVOCATION; i++) {
            state.db()
                    .put(
                            ("compact-key-" + i).getBytes(StandardCharsets.UTF_8),
                            ("payload-" + i).getBytes(StandardCharsets.UTF_8));
        }
    }
}
