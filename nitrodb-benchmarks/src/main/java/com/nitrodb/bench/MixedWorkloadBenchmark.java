package com.nitrodb.bench;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class MixedWorkloadBenchmark {

    private final AtomicLong sequence = new AtomicLong();

    /**
     * Alternates writes and reads to emulate a mixed application workload.
     *
     * @param state benchmark state backing the workload
     * @return the last read value, or {@code null} on the first lookup
     */
    @Benchmark
    public byte[] mixedReadWrite(final ReadBenchmarkState state) {
        long value = sequence.incrementAndGet();
        byte[] key = ("mix-key-" + value).getBytes(StandardCharsets.UTF_8);
        byte[] payload = ("mix-value-" + value).getBytes(StandardCharsets.UTF_8);
        state.db().put(key, payload);
        return state.db().get(key).orElse(null);
    }
}
