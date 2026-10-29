# Decision log

Decisions visible in the code and in `docs/Architecture-and-Design.md`. I did not date them at the time.

## 1. LSM tree rather than a B-tree

**Context.** I wanted a write-optimised engine and a design with several distinct moving parts to explain.
**Options.** B-tree with a WAL; LSM tree with leveled compaction.
**Decision.** LSM tree: WAL, memtable, immutable SSTables, bloom filters, sparse indexes, leveled compaction.
**Consequences.** Writes are sequential and cheap. Reads may touch several files, which bloom filters and the block cache reduce. Compaction competes with foreground work, and the smoke benchmark for it is the weakest number in the project.

## 2. WAL before memtable, with a strict and a lenient corruption policy

**Context.** Recovery reasoning has to be simple.
**Options.** Memtable first and WAL after; WAL first. On a corrupt tail: fail, or truncate and continue.
**Decision.** WAL first (`NitroDBImpl.put` appends then inserts). `DBConfig.WalCorruptionPolicy` offers strict fail-fast and lenient tail truncation.
**Consequences.** The intent is that a lenient recovery yields a prefix of the written operations and a strict one refuses to guess; `CrashRecoveryTest` exercises both policies. `config/default.properties` sets `syncWrites=false` and `walSyncMode=ASYNC`, so with that configuration the most recent writes can be lost on power failure.

## 3. MVCC with monotonically increasing sequence numbers

**Context.** I wanted snapshot reads and range scans that stay stable under concurrent writes.
**Options.** Locks during scans; copy-on-read; versioned keys.
**Decision.** Every write gets a sequence number from `SequenceGenerator`; keys are stored as `VersionedKey` (key plus sequence). A snapshot is a sequence number.
**Consequences.** Readers never block writers. Old versions pile up until compaction removes versions no snapshot can see. Long-lived snapshots delay that cleanup.

## 4. Striped per-key locks for writes

**Context.** One global write lock would serialise everything; no lock would race sequence assignment and WAL order.
**Options.** Global lock; per-key lock table; lock-free.
**Decision.** A striped lock array keyed by hash of the key in `NitroDBImpl`.
**Consequences.** Writes to different keys proceed in parallel. Two keys can share a stripe. Behaviour under contention is covered by `NitroDBConcurrentTest` and the JCStress classes rather than by proof.

## 5. Prove concurrency claims with JCStress

**Context.** Unit tests rarely hit rare interleavings.
**Options.** More unit tests with threads; stress loops; JCStress.
**Decision.** A separate `nitrodb-jcstress` module with three classes (concurrent write/read, snapshot isolation, compaction race). The architecture document records 42 of 42 outcomes passing in a run on 2026-06-05.
**Consequences.** Stronger evidence than a loop of threads, at the cost of a slower, separate build. It is not run in CI today.

## 6. Publish only smoke benchmarks

**Context.** Benchmark numbers from one laptop are easy to over-read.
**Options.** Publish headline throughput; publish nothing; publish with caveats.
**Decision.** JMH benchmarks are packaged and runnable; the one table of results is labelled "smoke test, not a controlled performance study" in `docs/Architecture-and-Design.md`. The README quotes none.
**Consequences.** Nothing to disprove, and nothing to impress anyone with. A controlled run is on the roadmap.
