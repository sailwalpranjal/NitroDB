package com.nitrodb.bench;

import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;

public class ReadBenchmark {

    /**
     * Measures a hot point lookup against a pre-populated key.
     *
     * @param state benchmark state backing the workload
     * @return the fetched value or {@code null} if absent
     */
    @Benchmark
    public byte[] pointRead(final ReadBenchmarkState state) {
        return state.db().get("read-key-500".getBytes(StandardCharsets.UTF_8)).orElse(null);
    }
}
