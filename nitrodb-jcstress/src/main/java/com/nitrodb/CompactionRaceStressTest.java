package com.nitrodb;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.L_Result;

@JCStressTest
@Outcome(id = "OK", expect = Expect.ACCEPTABLE, desc = "No compaction race surfaced.")
@State
public class CompactionRaceStressTest {

    private final NitroDBImpl db;
    private volatile String failure;

    public CompactionRaceStressTest() {
        try {
            Path dir = Files.createTempDirectory("nitrodb-jcstress");
            DBConfig config = new DBConfig(
                    dir,
                    128,
                    DBConfig.DEFAULT_BLOCK_CACHE_SIZE_BYTES,
                    DBConfig.DEFAULT_BLOCK_SIZE_BYTES,
                    DBConfig.DEFAULT_MAX_LEVELS,
                    DBConfig.DEFAULT_LEVEL_SIZE_MULTIPLIER,
                    DBConfig.DEFAULT_L1_MAX_BYTES,
                    DBConfig.DEFAULT_BLOOM_FALSE_POSITIVE_RATE,
                    false,
                    DBConfig.WalSyncMode.ASYNC,
                    DBConfig.WalCorruptionPolicy.LENIENT,
                    10L,
                    DBConfig.DEFAULT_MAX_IMMUTABLE_COUNT,
                    256,
                    DBConfig.DEFAULT_WAL_SEGMENT_SIZE_BYTES,
                    DBConfig.DEFAULT_NUM_CACHE_SHARDS,
                    true);
            db = new NitroDBImpl(config);
            for (int i = 0; i < 64; i++) {
                db.put(
                        ("seed-" + i).getBytes(StandardCharsets.UTF_8),
                        ("value-" + i).getBytes(StandardCharsets.UTF_8));
            }
            db.awaitBackgroundWorkForTesting();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Actor
    public void writer() {
        try {
            db.put("race-key".getBytes(StandardCharsets.UTF_8), "value".getBytes(StandardCharsets.UTF_8));
            db.awaitBackgroundWorkForTesting();
        } catch (RuntimeException e) {
            failure = e.getClass().getSimpleName();
        }
    }

    @Actor
    public void compactor() {
        try {
            db.triggerCompactionForTesting();
        } catch (RuntimeException e) {
            failure = e.getClass().getSimpleName();
        }
    }

    @Arbiter
    public void arbiter(L_Result result) {
        result.r1 = failure == null ? "OK" : failure;
        db.close();
    }
}
