package com.nitrodb.bench;

import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;

public class RangeScanBenchmark {

    /**
     * Measures a bounded range scan over a pre-populated key space.
     *
     * @param state benchmark state backing the workload
     * @return number of rows produced by the scan
     */
    @Benchmark
    public int scan(final RangeScanBenchmarkState state) {
        int count = 0;
        try (var result = state.db()
                .scan(
                        "scan-key-0000".getBytes(StandardCharsets.UTF_8),
                        "scan-key-0500".getBytes(StandardCharsets.UTF_8))) {
            for (var ignored : result) {
                count++;
            }
        }
        return count;
    }
}
