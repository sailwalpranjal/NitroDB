package com.nitrodb;

import com.nitrodb.memtable.MemTable;
import java.nio.charset.StandardCharsets;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.L_Result;

@JCStressTest
@Outcome(id = "MISSING", expect = Expect.ACCEPTABLE, desc = "Read won the race.")
@Outcome(id = "value", expect = Expect.ACCEPTABLE, desc = "Write won the race.")
@State
public class ConcurrentWriteReadStressTest {

    private final MemTable memTable = new MemTable();
    private final byte[] key = "key".getBytes(StandardCharsets.UTF_8);

    @Actor
    public void writer() {
        memTable.put(key, "value".getBytes(StandardCharsets.UTF_8), 1L);
    }

    @Actor
    public void reader(L_Result result) {
        result.r1 = memTable.get(key, Long.MAX_VALUE)
                .map(entry -> new String(entry.value(), StandardCharsets.UTF_8))
                .orElse("MISSING");
    }
}
