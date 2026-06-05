package com.nitrodb;

import com.nitrodb.memtable.MemTable;
import com.nitrodb.mvcc.Snapshot;
import com.nitrodb.mvcc.SnapshotManager;
import java.nio.charset.StandardCharsets;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.LL_Result;

@JCStressTest
@Outcome(id = "v1, v1", expect = Expect.ACCEPTABLE, desc = "Writer ran after arbiter.")
@Outcome(id = "v1, v2", expect = Expect.ACCEPTABLE, desc = "Snapshot stayed stable.")
@State
public class SnapshotIsolationStressTest {

    private final MemTable memTable = new MemTable();
    private final SnapshotManager snapshotManager = new SnapshotManager();
    private final byte[] key = "key".getBytes(StandardCharsets.UTF_8);
    private volatile Snapshot snapshot;

    public SnapshotIsolationStressTest() {
        memTable.put(key, "v1".getBytes(StandardCharsets.UTF_8), 1L);
    }

    @Actor
    public void snapshotter() {
        snapshot = snapshotManager.create(1L);
    }

    @Actor
    public void writer() {
        memTable.put(key, "v2".getBytes(StandardCharsets.UTF_8), 2L);
    }

    @Arbiter
    public void arbiter(LL_Result result) {
        Snapshot local = snapshot != null ? snapshot : snapshotManager.create(1L);
        result.r1 = new String(
                memTable.get(key, local.sequenceNumber()).orElseThrow().value(),
                StandardCharsets.UTF_8);
        result.r2 = new String(
                memTable.get(key, Long.MAX_VALUE).orElseThrow().value(),
                StandardCharsets.UTF_8);
        local.close();
    }
}
