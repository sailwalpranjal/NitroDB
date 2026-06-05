package com.nitrodb.recovery;

import com.nitrodb.DBConfig;
import com.nitrodb.manifest.ManifestManager;
import com.nitrodb.memtable.MemTable;
import com.nitrodb.mvcc.SequenceGenerator;
import com.nitrodb.wal.WalManager;
import java.nio.file.Path;

public final class RecoveryManager {

    private final ManifestManager manifestManager;
    private final WalManager walManager;
    private final SequenceGenerator sequenceGenerator;
    private final ConsistencyChecker consistencyChecker = new ConsistencyChecker();
    private final WalRecovery walRecovery = new WalRecovery();

    public RecoveryManager(ManifestManager manifestManager, WalManager walManager, SequenceGenerator sequenceGenerator) {
        this.manifestManager = manifestManager;
        this.walManager = walManager;
        this.sequenceGenerator = sequenceGenerator;
    }

    public RecoveryResult recover(Path dataDir, DBConfig config) {
        ConsistencyChecker.ConsistencyReport report = consistencyChecker.check(dataDir, manifestManager);
        if (!report.missingSstables().isEmpty()) {
            throw new com.nitrodb.api.DBException.RecoveryException("Missing SSTables referenced by manifest: " + report.missingSstables());
        }
        MemTable memTable = new MemTable();
        long maxRecoveredSequence = walRecovery.replay(
                walManager.unflushedSegments(manifestManager.getFlushedSequence()),
                memTable,
                manifestManager.getFlushedSequence(),
                config.walCorruptionPolicy());
        sequenceGenerator.setMinimum(maxRecoveredSequence);
        return new RecoveryResult(memTable, maxRecoveredSequence, report);
    }

    public record RecoveryResult(
            MemTable recoveredMemTable,
            long maxRecoveredSequence,
            ConsistencyChecker.ConsistencyReport consistencyReport) {
    }
}
