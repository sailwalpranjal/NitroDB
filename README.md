# NitroDB

[![CI](https://github.com/sailwalpranjal/NitroDB/actions/workflows/ci.yml/badge.svg)](https://github.com/sailwalpranjal/NitroDB/actions/workflows/ci.yml)
[![licence: MIT](https://img.shields.io/badge/licence-MIT-blue.svg)](LICENSE)

A single-node, embedded key-value storage engine for Java 21+, built as an LSM tree: write-ahead log, memtable, SSTables with bloom filters and sparse indexes, MVCC snapshots, leveled compaction, and crash recovery. I wrote it to learn how these pieces fit together, and kept it small enough to read in an afternoon.

It is a learning and demonstration engine. Do not put data you care about in it.

## Who it is for, and the problem

It is for a developer who wants to read, run and discuss a real LSM-tree engine. The papers explain the ideas, and production codebases are large. This sits between them: complete enough that a write really travels from the WAL to an SSTable and survives a restart, small enough that you can follow it, with tests that pin down what happens when things go wrong. I have no recorded users beyond myself.

## Demo

```text
alpha=one
Recovered alpha=one
Recovered beta present=false
```

That is the output of the example runner: write two keys, delete one, close, reopen, read back. To run it, see Quick start. To capture a screen recording for the repository: run the command below in a terminal sized about 100x20, record about 10 seconds with ScreenToGif at 12 fps, and save it as `docs/img/example-runner.gif`.

## Quick start

You need JDK 21 or newer. The Maven wrapper downloads Maven 3.9.11 on first use, so nothing else is required.

```bash
./mvnw -B -q -pl nitrodb-core test       # 69 tests
./mvnw -pl nitrodb-core exec:java -Dexec.mainClass=com.nitrodb.NitroDBExampleRunner -Dexec.args=example-data
```

Windows, with the bundled Maven:

```powershell
.\.tools\apache-maven-3.9.16\bin\mvn.cmd test
.\.tools\apache-maven-3.9.16\bin\mvn.cmd verify
```

API: `put`, `get`, `delete`, `scan`, `getSnapshot`. Configuration lives in `config/*.properties` (`default`, `dev`, `low-latency`, `high-throughput`).

## How it works

```
 put/delete
    |
 striped per-key lock --> sequence number --> WAL (CRC-framed) --> active memtable
                                                                      | full
                                                              immutable memtable
                                                                      | background flush
 get/scan: memtables --> L0 SSTables --> L1..L6 SSTables <-- leveled compaction
                         (key range, bloom filter, sparse index, block cache)

 restart: read manifest, validate files, delete temp files, replay unflushed WAL
```

Reads go through snapshots: a snapshot is a sequence number, and keys are stored as key plus sequence, so readers never block writers. Compaction merges SSTables and drops versions no snapshot can still see. The design document walks through each path and ties every claim to a test: [docs/Architecture-and-Design.md](docs/Architecture-and-Design.md).

## Evidence

Measured for this README by running `mvn -pl nitrodb-core test` on JDK 25 (compiling for Java 21): **69 tests, 0 failures, 0 errors.**

Recorded in the design document from a run on 2026-06-05, not repeated for this README: `mvn verify` succeeded across all three modules, and the JCStress suite (concurrent write and read, snapshot isolation, compaction race) passed 42 of 42 outcomes.

The design document contains a claim-verification matrix: each feature, the code that implements it, the tests that cover it, and the command that was run. A feature that has no row there should not be claimed.

Benchmarks: JMH benchmarks are packaged (`nitrodb-benchmarks`). The only results published are quick smoke runs in the design document (one warm-up, one measurement, one fork, one machine). I quote none here because they do not support a performance claim.

## Limits and known gaps

- **Not for production.** Single node. No compression, no replication, no SQL, no TTL.
- **Durability depends on configuration.** `config/default.properties` sets `syncWrites=false` and `walSyncMode=ASYNC`, so the most recent writes can be lost on power failure. Change them if that matters.
- **The compaction smoke benchmark looks wrong or slow** (about 96 ops/s against about 142,000 for sequential puts). I have not worked out which. It is the first roadmap item.
- **JCStress is not run in CI.** The 42 of 42 result is a one-off.
- **Not audited:** the off-heap block cache allocator's lifetime rules.

## Product thinking

- [PRD](docs/product/PRD.md), [roadmap](docs/product/ROADMAP.md) (RICE and ICE arithmetic), [decision log](docs/product/DECISIONS.md), [metrics plan](docs/product/METRICS.md)
- [Changelog](CHANGELOG.md), [project board setup](docs/project-board.md), [issue drafts](docs/issues/)
- [Original architecture blueprint](BluePrint.md), written before the implementation
- [Project understanding and interview guide](docs/Project-Understanding-and-Interview-Guide.md)

## Repository layout

- `nitrodb-core`: the engine, the example runner, and the correctness tests
- `nitrodb-benchmarks`: packaged JMH benchmarks
- `nitrodb-jcstress`: packaged JCStress concurrency suite
- `scripts`: build, test, stress, coverage and label helpers
- `config`: sample configuration profiles

## Licence

MIT, see [LICENSE](LICENSE).
