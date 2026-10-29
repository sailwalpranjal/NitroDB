# PRD: NitroDB

**Status:** Retrospective PRD, written after the build. NitroDB started from an architecture blueprint (`BluePrint.md`); this document describes what the product turned out to be and who it serves.

## Problem

Storage-engine internals (write-ahead logs, memtables, SSTables, compaction, MVCC) are usually taught from papers or read in large production codebases. There is a gap for a small engine that is complete enough to show how the pieces fit together and small enough to read in an afternoon, with tests that pin down the behaviour.

## Target user

Primary user: a developer who wants to read, run and discuss an LSM-tree engine, starting with me. A secondary use is embedding it in a JVM application that needs a local ordered key-value store, though I would not recommend that for anything important (see risks). I have not interviewed anyone, and there are no recorded users.

## Jobs to be done

- When I want to understand how a write becomes durable, I want to follow one write from the WAL to an SSTable in code that I can run, so I can explain it.
- When I change a piece of the engine, I want tests that say whether crash recovery or snapshot isolation broke.
- When I claim something about concurrency, I want a tool that tries to break it, not just a unit test.

## Goals

1. A single-node embedded key-value API: `put`, `get`, `delete`, `scan`, `getSnapshot`.
2. Durability through a WAL, with recovery after a restart or a crash.
3. Snapshot reads that stay stable while writes continue (MVCC).
4. Leveled compaction that keeps live data and drops obsolete versions.
5. Evidence: unit and integration tests, JCStress concurrency checks, packaged JMH benchmarks.

## Non-goals

- Networking, replication, consensus, SQL.
- Compression and a TTL feature in the write API.
- Performance competitive with RocksDB or LevelDB.
- Production use.

## User stories and acceptance criteria

| Story | Acceptance criteria |
|---|---|
| As a developer I can write a key and read it back after a restart. | `NitroDBIntegrationTest.reopenRetainsData` passes; the example runner prints `Recovered alpha=one` |
| As a developer I can rely on recovery after a crash. | `CrashRecoveryTest` covers WAL replay, dangling temp files, and a corrupt WAL under both the strict and lenient policies |
| As a developer I can read a stable view while writes continue. | Snapshot tests and the JCStress `SnapshotIsolationStressTest` pass |
| As a developer I can scan a range under a snapshot. | Range scans through a snapshot return the same results before and after concurrent writes |

## Success metrics

No one else uses it, so these describe the build.

- North star (proposed): number of readers who can explain the write path after reading the docs. Not measured.
- Input metrics that exist: 68 unit tests pass (`mvn test -pl nitrodb-core`, verified again for this change); 42 of 42 JCStress outcomes pass (recorded in the architecture document; not re-run for this change).
- Guardrail: the claim-verification matrix in `docs/Architecture-and-Design.md` maps every stated feature to code and a test. A claim without a row should not be in the README.

See [METRICS.md](METRICS.md).

## Risks and open questions

- **Not production grade.** Single node, no compression, no controlled benchmark study. The quick JMH numbers in the design document are smoke runs on one machine.
- **Write throughput during compaction** looks low in the smoke run (`CompactionLoadBenchmark.writeLoad`, about 96 ops/s). Open question: is that the benchmark's workload or a real stall?
- **Off-heap cache.** The block cache uses an off-heap allocator (`cache/OffHeapAllocator.java`). I have not audited its lifetime rules for this document.
