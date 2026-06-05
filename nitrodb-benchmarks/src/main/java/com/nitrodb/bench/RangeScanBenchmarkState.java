package com.nitrodb.bench;

import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class RangeScanBenchmarkState extends BenchmarkState {

    private static final int DATASET_SIZE = 1_000;

    /**
     * Loads a deterministic key range for scan benchmarks.
     */
    @Setup(Level.Trial)
    public void populate() {
        for (int i = 0; i < DATASET_SIZE; i++) {
            db()
                    .put(
                            ("scan-key-%04d".formatted(i)).getBytes(StandardCharsets.UTF_8),
                            ("value-" + i).getBytes(StandardCharsets.UTF_8));
        }
    }
}
