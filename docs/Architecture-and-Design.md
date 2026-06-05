# NitroDB Architecture And Design

This document explains how NitroDB works and ties each major claim to code, tests, and command-level verification performed on `2026-06-05`.

## Verified Baseline

- `mvn test` passed with `68` tests and `0` failures.
- `mvn verify` passed for `nitrodb-core`, `nitrodb-benchmarks`, and `nitrodb-jcstress`.
- `NitroDBExampleRunner` successfully wrote, deleted, restarted, and recovered data.
- `java -jar nitrodb-benchmarks\target\benchmarks.jar -l` listed all benchmark workloads.
- `java -jar nitrodb-benchmarks\target\benchmarks.jar ".*Benchmark" -wi 1 -i 1 -f 1 -r 1s -w 1s` ran successfully.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\stress.ps1` passed, including `42 passed, 0 failed` JCStress outcomes.

## Claim Verification Matrix

| Claim | Implementation | Test Coverage | Verification Evidence | Status |
|---|---|---|---|---|
| NitroDB is a single-node embedded key-value engine | `nitrodb-core/src/main/java/com/nitrodb/NitroDB.java`, `NitroDBImpl.java`, `NitroDBBuilder.java` | `NitroDBIntegrationTest`, `NitroDBUseCaseTest` | API exercised by example runner and integration tests | Verified |
| NitroDB supports `put`, `get`, `delete`, `scan`, and `getSnapshot` | `NitroDB.java`, `NitroDBImpl.java` | `NitroDBIntegrationTest`, `NitroDBConcurrentTest`, `NitroDBUseCaseTest` | `mvn test` passed with end-to-end API coverage | Verified |
| Writes are WAL-backed before memtable insertion | `wal/WalWriter.java`, `wal/WalManager.java`, `NitroDBImpl.java` | `WalWriterReaderTest`, `CrashRecoveryTest`, `RecoveryManagerTest` | Code path appends WAL before `activeMemTable.put` | Verified |
| Data persists across restart | `NitroDBImpl.close`, `FlushWorker.java`, `SSTableWriter.java`, `ManifestManager.java` | `NitroDBIntegrationTest.reopenRetainsData`, `NitroDBUseCaseTest.applicationSettingsStoragePersistsAcrossRestart` | Example runner output shows recovered key after reopen | Verified |
| Crash recovery replays unflushed WAL data | `recovery/RecoveryManager.java`, `recovery/WalRecovery.java`, `wal/WalReader.java` | `CrashRecoveryTest`, `RecoveryManagerTest` | Example runner and crash tests recover expected values | Verified |
| WAL corruption policies support strict fail-fast and lenient tail truncation | `DBConfig.WalCorruptionPolicy`, `WalReader.java`, `RecoveryManager.java` | `CrashRecoveryTest.strictRecoveryFailsOnCorruptTail`, `CrashRecoveryTest.lenientRecoveryStopsAtCorruptTail`, `WalWriterReaderTest` | Corruption tests pass under both policies | Verified |
| Memtables are concurrent and ordered by MVCC versioned keys | `memtable/MemTable.java`, `mvcc/VersionedKey.java`, `memtable/MemTableIterator.java` | `MemTableTest`, `VersionedKeyTest`, `ConcurrentWriteReadStressTest` | Concurrent read/write coverage in JUnit and JCStress | Verified |
| Immutable memtables flush to SSTables in the background | `flush/FlushWorker.java`, `sstable/SSTableWriter.java`, `NitroDBImpl.rotateMemTable` | `NitroDBIntegrationTest`, `SSTableWriterReaderTest` | Flush is exercised by forced small memtable tests | Verified |
| SSTables are readable after write and reopen | `sstable/SSTableWriter.java`, `sstable/SSTableReader.java`, `manifest/ManifestManager.java` | `SSTableWriterReaderTest`, `NitroDBIntegrationTest.reopenRetainsData` | Reopen path reloads manifest and readers successfully | Verified |
| Bloom filters avoid some negative block reads | `bloom/BloomFilter.java`, `sstable/SSTableReader.getEntry` | `BloomFilterTest`, `SSTableWriterReaderTest.bloomNegativeLookupSkipsBlockReads` | Negative lookup left block-cache request count at `0` | Verified |
| Sparse indexes narrow SSTable lookups to candidate blocks | `index/SparseIndex.java`, `index/SparseIndexBuilder.java`, `sstable/SSTableReader.java` | `SparseIndexTest`, `SSTableWriterReaderTest` | Binary-search lookup returns expected block handles | Verified |
| Snapshot reads are MVCC bounded and stable | `mvcc/Snapshot.java`, `mvcc/SnapshotManager.java`, `NitroDBImpl.get`, `NitroDBImpl.scan`, `iter/MergingIterator.java`, `iter/IteratorUtils.java` | `NitroDBIntegrationTest.snapshotsSeeOldValue`, `NitroDBConcurrentTest.rangeScanSnapshotRemainsStableDuringConcurrentWrites`, `SnapshotIsolationStressTest` | Snapshot tests and JCStress pass | Verified |
| Compaction preserves live data and removes obsolete versions safely | `compaction/CompactionWorker.java`, `compaction/MergeIterator.java`, `manifest/ManifestManager.java` | `NitroDBIntegrationTest.manualCompactionPreservesData`, `repeatedManualCompactionsRemainStable`, `compactionDropsObsoleteVersionsAfterSnapshotsRelease`, `compactionDropsTombstonesWhenNoDeeperOverlapExists` | Compaction tests passed under repeated manual triggering | Verified |
| Manifest records track SSTables and flushed WAL sequence | `manifest/ManifestManager.java`, `manifest/ManifestRecord.java`, `manifest/ManifestEntry.java` | `ManifestManagerTest` | Reopen restores state and corruption is rejected | Verified |
| Recovery deletes dangling temp files and rejects missing manifest SSTables | `recovery/ConsistencyChecker.java`, `recovery/RecoveryManager.java` | `CrashRecoveryTest.startupDeletesDanglingTempFiles`, `RecoveryManagerTest` | Startup validation and temp cleanup tests pass | Verified |
| Block cache is used on repeated SSTable reads | `cache/BlockCache.java`, `cache/CacheShard.java`, `sstable/SSTableReader.readBlock` | `BlockCacheTest`, `NitroDBIntegrationTest.repeatedSstableReadsUseBlockCache` | First read caused misses and cache growth; second read caused hits | Verified |
| Runtime metrics are accumulated and can be published to sinks | `metrics/NitroDBMetrics.java`, `MetricsReporter.java`, `MetricsSink.java`, `NitroDBImpl.java` | `MetricsReporterTest`, `NitroDBMetricsIntegrationTest` | Integration test observes write, delete, read, and latency metrics | Verified |
| Benchmarks are packaged, listed, and runnable | `nitrodb-benchmarks/pom.xml`, `bench/*.java`, `BenchmarkRunner.java` | Build-time packaging plus command validation | `java -jar ... -l` and quick benchmark run succeeded on `2026-06-05` | Verified |
| Concurrency stress coverage is packaged and runnable | `nitrodb-jcstress/pom.xml`, `ConcurrentWriteReadStressTest.java`, `SnapshotIsolationStressTest.java`, `CompactionRaceStressTest.java` | `scripts/stress.ps1` plus packaged JCStress jar | Stress run completed with `42 passed, 0 failed` | Verified |

## Storage Engine Overview

NitroDB is an embedded LSM-tree engine. Recent writes live in memory, durable intent is recorded in the WAL, and immutable on-disk SSTables hold flushed and compacted data.

### Write Path

1. `NitroDBImpl.put` or `NitroDBImpl.delete` acquires a striped per-key lock.
2. `SequenceGenerator` assigns a monotonically increasing sequence number.
3. `WalWriter` appends a CRC-protected frame to the active WAL segment.
4. The mutation is inserted into the active `MemTable`.
5. If the memtable crosses its size threshold, `rotateMemTable` freezes it and sends it to `FlushWorker`.
6. `FlushWorker` writes a new level-0 SSTable and updates the manifest.

### Read Path

1. `NitroDBImpl.get` first looks in the active memtable.
2. It then checks immutable memtables from newest to oldest.
3. If not found, it visits SSTables in read order.
4. Each SSTable reader performs:
   - key-range exclusion
   - bloom filter check
   - sparse-index block lookup
   - block-cache lookup
   - checksum-validated block read on cache miss
5. Range scans use `MergingIterator` plus deduplication and tombstone filtering.

### Recovery Path

1. `ManifestManager` opens and rebuilds level state from the append-only manifest.
2. `ConsistencyChecker` deletes `.tmp` files and verifies referenced SSTables exist and open.
3. `WalRecovery` replays unflushed WAL segments above the flushed sequence.
4. `SequenceGenerator` is advanced to at least the maximum recovered sequence.
5. Background flush, compaction, and metrics threads start only after state is rebuilt.

## LSM Tree Design

### Why LSM Here

- sequential WAL appends and batched SSTable writes are a natural fit for write-heavy embedded workloads
- immutable files simplify recovery and compaction reasoning
- bloom filters and sparse indexes reduce the read amplification cost

### Tradeoffs

- writes are fast, but compaction introduces write amplification
- reads may inspect multiple in-memory and on-disk structures
- point reads are helped by the cache, bloom filters, and sparse indexes, but cold reads still pay disk cost

## Component Design

### WAL

- Purpose: durability and crash recovery.
- Main files: `wal/WalWriter.java`, `WalReader.java`, `WalManager.java`, `WalSegment.java`.
- Format: WAL header plus framed records containing CRC, payload length, type, sequence number, key length, value length, key, and value.
- Complexity: append is `O(1)` amortized; replay is `O(number of WAL records)`.
- Tradeoff: simpler single-writer append path over more advanced batching infrastructure.

### MemTable

- Purpose: current mutable state.
- Main files: `memtable/MemTable.java`, `ImmutableMemTable.java`, `MemTableIterator.java`, `mvcc/VersionedKey.java`.
- Design: `ConcurrentSkipListMap<VersionedKey, MemTableEntry>` sorted by key ascending and sequence descending.
- Complexity: point insert and lookup are `O(log n)`.
- Tradeoff: standard concurrent structure instead of a custom lock-free tree.

### Immutable MemTable

- Purpose: a frozen snapshot waiting for background flush.
- Main files: `ImmutableMemTable.java`, `FlushWorker.java`.
- Design: copied ordered map plus size and sequence bounds.
- Tradeoff: copies state at freeze time for a simpler flush contract.

### SSTables

- Purpose: immutable persisted sorted runs.
- Main files: `sstable/SSTableWriter.java`, `SSTableReader.java`, `Block.java`, `BlockBuilder.java`, `SSTableFooter.java`.
- Design: block-oriented file format with footer metadata, sparse index bytes, bloom filter bytes, and per-block CRC.
- Complexity: point read is `O(log blocks)` for sparse-index lookup plus local block scan.
- Tradeoff: simple flat SSTables instead of more advanced partitioned index structures.

### Bloom Filters

- Purpose: reject many negative lookups before block reads.
- Main files: `bloom/BloomFilter.java`, `BloomFilterBuilder.java`, `BloomFilterSerializer.java`.
- Design: per-SSTable serialized bloom filter loaded on open.
- Complexity: `O(k)` hash probes per check.
- Tradeoff: accepts false positives to avoid false negatives and reduce disk access.

### Sparse Index

- Purpose: map a search key to the nearest candidate block.
- Main files: `index/SparseIndex.java`, `SparseIndexBuilder.java`, `SparseIndexEntry.java`.
- Design: sorted first-key entries with binary search.
- Complexity: `O(log blocks)` lookup.
- Tradeoff: smaller than a full entry index, but still requires scanning within the chosen block.

### MVCC And Snapshot Isolation

- Purpose: consistent point-in-time reads without stopping writers.
- Main files: `mvcc/SequenceGenerator.java`, `Snapshot.java`, `SnapshotManager.java`, `VersionedKey.java`.
- Design: every write gets a global sequence number; reads are bounded by a snapshot sequence ceiling.
- Complexity: version visibility is enforced during iteration and point lookup without separate lock tables.
- Tradeoff: old versions remain until compaction can safely discard them.

### Manifest

- Purpose: durable metadata for SSTable ownership and flushed sequence tracking.
- Main files: `manifest/ManifestManager.java`, `ManifestRecord.java`, `ManifestEntry.java`.
- Design: append-only manifest with checksummed frames and replay-on-open rebuild.
- Complexity: open is `O(number of manifest records)`.
- Tradeoff: simple replay-based manifest instead of periodic snapshots.

### Recovery

- Purpose: rebuild consistent state after crash or abrupt stop.
- Main files: `recovery/RecoveryManager.java`, `WalRecovery.java`, `ConsistencyChecker.java`.
- Design: validate manifest state, clean temporary files, then replay WAL above the flushed sequence.
- Tradeoff: startup pays replay cost instead of forcing every mutation fully to SSTables before acknowledging.

### Compaction

- Purpose: merge SSTables, reduce overlap, and garbage-collect old versions and tombstones when safe.
- Main files: `compaction/CompactionWorker.java`, `CompactionPlanner.java`, `MergeIterator.java`, `LeveledCompactionStrategy.java`.
- Design: choose source and overlapping target files, merge in order, install output SSTables atomically through the manifest.
- Complexity: roughly linear in the input data size of the selected job.
- Tradeoff: straightforward leveled compaction instead of more elaborate prioritization or subcompactions.

### Block Cache

- Purpose: avoid repeated disk reads of hot blocks.
- Main files: `cache/BlockCache.java`, `CacheShard.java`, `CacheEntry.java`, `CacheKey.java`, `OffHeapAllocator.java`.
- Design: sharded cache storing off-heap block copies keyed by SSTable file id and block offset.
- Complexity: shard-local lookup is near `O(1)` expected.
- Tradeoff: manual off-heap accounting and cache eviction complexity for reduced heap pressure.

### Metrics

- Purpose: observe writes, reads, cache behavior, and compaction work.
- Main files: `metrics/NitroDBMetrics.java`, `MetricsReporter.java`, `MetricsSink.java`, `DefaultMetricsSink.java`, `LoggingMetricsSink.java`, `NoOpMetricsSink.java`.
- Design: internal counters, gauges, and histograms with periodic publication to a pluggable sink.
- Tradeoff: lightweight internal metrics rather than direct Micrometer, Prometheus, or OpenTelemetry integration.

## Execution Flows

| Flow | Main Classes | Disk Files | Key Data Structures | Expected Complexity |
|---|---|---|---|---|
| Startup | `NitroDBImpl`, `ManifestManager`, `RecoveryManager`, `ConsistencyChecker` | `MANIFEST.mf`, `wal/*.wal`, `sst/*.sst` | manifest state, memtable, SSTable readers | manifest replay + WAL replay |
| Put | `NitroDBImpl`, `WalWriter`, `MemTable` | active WAL segment | sequence generator, striped locks, skip list | `O(log n)` plus append |
| Get | `NitroDBImpl`, `MemTable`, `SSTableReader`, `BlockCache` | WAL not touched, SSTables read on miss | memtables, bloom, sparse index, cache | `O(log n)` in memory plus SSTable candidate checks |
| Delete | `NitroDBImpl`, `WalWriter`, `MemTable` | active WAL segment | tombstone entry in memtable | same as put |
| Scan | `NitroDBImpl`, `MergingIterator`, `IteratorUtils`, `SSTableIterator` | SSTables as needed | merge heap across iterators | proportional to scanned entries |
| Flush | `FlushWorker`, `SSTableWriter`, `ManifestManager` | new `L0-*.sst`, manifest, WAL metadata | immutable memtable iterator | linear in immutable memtable size |
| Compaction | `CompactionWorker`, `MergeIterator`, `ManifestManager` | input/output SSTables, manifest | merge iterators, cache eviction | linear in compaction input size |
| Recovery | `RecoveryManager`, `WalRecovery`, `WalReader` | manifest and WAL | fresh memtable, replay state | linear in unflushed WAL size |

## Storage Format Summary

### WAL

- fixed header with magic and version
- repeated framed records with CRC and payload length
- payload stores type, sequence, key length, value length, key, and value

### SSTables

- file header with magic and version
- one or more data blocks
- serialized bloom filter and sparse index near the tail
- footer with key and sequence bounds plus offsets and lengths
- trailer containing footer length and footer magic

### Manifest

- header with magic and version
- append-only framed records with CRC
- record types for add SSTable, remove SSTable, set flushed sequence, compaction start, and compaction end

## Performance Discussion

Quick benchmark smoke run on `2026-06-05` using Java `25.0.1`, one warmup iteration, one measurement iteration, and one fork:

| Benchmark | Result |
|---|---|
| `CompactionLoadBenchmark.writeLoad` | `96.262 ops/s` |
| `MixedWorkloadBenchmark.mixedReadWrite` | `129608.754 ops/s` |
| `RangeScanBenchmark.scan` | `6725.516 ops/s` |
| `ReadBenchmark.pointRead` | `2254766.463 ops/s` |
| `WriteBenchmark.sequentialPuts` | `142079.186 ops/s` |

These numbers are smoke-test results, not a controlled performance study. They prove the benchmark suite works and provide a baseline for future tuning.

## Design Decisions That Held Up

- WAL before memtable mutation kept recovery reasoning simple.
- Immutable SSTables and manifest replay made restart behavior straightforward to validate.
- MVCC with monotonically increasing sequence numbers gave a clean model for snapshot visibility.
- Sharded block caching improved repeated-read behavior without changing read correctness.
- Keeping the engine embedded avoided diluting the project with networking or distributed systems concerns.

## Current Limitations

- single-node only
- no SQL layer
- no replication or consensus
- no compression
- no TTL feature in the public write API
- metrics are internal and sink-based rather than integrated with an external observability stack
- benchmark results are quick-run proof points, not exhaustive tuning studies
