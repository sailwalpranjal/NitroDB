# NitroDB Project Understanding And Interview Guide 

This guide is for anyone who uses the project as is 100%. It explains what NitroDB is, how to run and explain it, what the validated commands prove, and how to defend the design in interviews.

## Section 1: What NitroDB Is

NitroDB is an embedded Java key-value storage engine that implements a practical LSM-tree architecture. It supports:

- durable writes through a write-ahead log
- in-memory ordered memtables
- immutable SSTables on disk
- bloom filters and sparse indexes on the read path
- MVCC snapshots and snapshot-stable scans
- background flush and compaction
- restart recovery
- internal metrics
- packaged benchmarks and concurrency stress checks

In plain terms, NitroDB is the storage layer you would place inside a JVM application when you want local persistence and want to understand the mechanics instead of hiding them behind a remote database.

## Section 2: What NitroDB Is Not

NitroDB is not:

- a SQL database
- a distributed database
- a replicated system
- a network server
- a multi-key transaction engine
- a compressed or encrypted storage engine
- a drop-in replacement for RocksDB or Cassandra

This is an embedded storage-engine project, not a full database platform.

## Section 3: Why NitroDB Exists

NitroDB exists for three reasons:

1. To build and demonstrate real storage-engine internals in a compact codebase.
2. To provide a portfolio project that is deeper than typical CRUD or REST work.
3. To give a project owner something they can explain to senior engineers without hand-waving.

### Real-World Validation

These use cases were validated with `NitroDBUseCaseTest`.

| Use Case | Example Data | Example Operations | Expected Behavior | Technical Explanation |
|---|---|---|---|---|
| application settings storage | `settings/theme -> dark`, `settings/language -> en-IN` | put, close, reopen, get | settings survive restart | WAL + flush/SSTable persistence preserve small configuration records |
| user preference storage | `user:42:timezone -> UTC`, then update to `Asia/Kolkata` | put, snapshot, update, snapshot read, latest read | old snapshot sees `UTC`, latest read sees `Asia/Kolkata` | snapshot sequence bounds enforce MVCC visibility |
| local embedded database | `project:001:*` records | put multiple keys, range scan by prefix span | scan returns only the selected logical record group | sorted keys plus merge iterators support range access |
| metadata store | `file:manifest -> {"version":1}` then overwrite and delete | put, overwrite, get, delete, get | latest value wins, delete leaves key absent | sequence ordering and tombstones implement overwrite/delete semantics |
| cache persistence layer | `cache:session:abc -> {"user":"alice"}` | put, close, reopen, get | cached object survives process restart | NitroDB can persist data that would otherwise be volatile |
| lightweight key-value engine | `kv:000` through `kv:099` | bulk put, point get, delete | reads succeed and deletes hide keys | engine handles many small records with the same core path |

## Section 4: What Happens When It Runs

At a high level, NitroDB does four things when it is live:

1. It keeps the latest versions of keys in memory.
2. It writes durable intent to the WAL before acknowledging mutations.
3. It moves older immutable data into sorted on-disk SSTables.
4. It continuously reconciles correctness with performance through compaction, caching, and snapshot visibility.

The engine is always balancing three concerns:

- durability
- read correctness
- background maintenance

## Section 5: What Happens During Core Operations

### Startup

- Entry point: `NitroDBBuilder.build()` -> `NitroDBImpl` constructor.
- Main classes: `FileManager`, `ManifestManager`, `RecoveryManager`, `ConsistencyChecker`, `WalManager`, `FlushWorker`, `CompactionWorker`, `MetricsReporter`.
- Files touched: `LOCK`, `MANIFEST.mf`, `wal/*.wal`, `sst/*.sst`.
- Steps:
  - acquire the database lock
  - open and replay the manifest
  - validate SSTables and remove leftover `.tmp` files
  - replay unflushed WAL segments into a new memtable
  - load SSTable readers and start background workers
- Complexity: proportional to manifest size plus unflushed WAL size

### Put

- Entry point: `NitroDBImpl.put`
- Data structures: striped `ReentrantLock`, `SequenceGenerator`, active `MemTable`
- Files touched: active WAL segment
- Steps:
  - lock the key stripe
  - assign a sequence number
  - append a `PUT` record to the WAL
  - optionally sync the WAL
  - insert the versioned entry into the active memtable
  - rotate the memtable if the threshold is crossed
- Complexity: `O(log n)` memtable insert plus `O(1)` append

### Get

- Entry point: `NitroDBImpl.get`
- Data structures: active memtable, immutable memtables, SSTable readers, bloom filters, sparse index, block cache
- Files touched: SSTable files on miss
- Steps:
  - choose the maximum visible sequence from the latest state or a snapshot
  - search the active memtable
  - search immutable memtables newest-first
  - search SSTables by level and recency
  - for each SSTable candidate: key-range check, bloom check, sparse-index lookup, cache lookup, block read if needed
- Complexity: `O(log n)` in memory plus candidate SSTable checks

### Delete

- Entry point: `NitroDBImpl.delete`
- Data structures: same as put
- Files touched: active WAL segment
- Steps:
  - assign a sequence number
  - append a `DELETE` record to the WAL
  - insert a tombstone into the active memtable
  - later flush and compaction preserve or drop the tombstone depending on safety
- Complexity: same as put

### Snapshot

- Entry point: `NitroDBImpl.getSnapshot`
- Main classes: `SequenceGenerator`, `SnapshotManager`, `Snapshot`
- Files touched: none
- Steps:
  - capture the current global sequence number
  - register a snapshot object
  - later reads use `ReadOptions(snapshot)` to bound visible versions
- Complexity: `O(1)`

### Range Scan

- Entry point: `NitroDBImpl.scan`
- Main classes: `MergingIterator`, `IteratorUtils`, `MemTableIterator`, `SSTableIterator`
- Files touched: memtables only or memtables plus SSTables depending on data location
- Steps:
  - create iterators across active memtable, immutable memtables, and SSTables
  - merge them by versioned-key order
  - deduplicate by user key
  - filter tombstones
  - stop at the exclusive end key
- Complexity: proportional to the number of returned and skipped entries

### Flush

- Entry point: `FlushWorker.runLoop`
- Main classes: `ImmutableMemTable`, `SSTableWriter`, `ManifestManager`, `WalManager`
- Files touched: new `sst/L0-*.sst`, `MANIFEST.mf`, WAL metadata state
- Steps:
  - dequeue an immutable memtable
  - stream it into a new SSTable
  - add the SSTable to level 0 in the manifest
  - advance the flushed sequence
  - allow older WAL segments to become reclaimable
- Complexity: linear in immutable memtable size

### Compaction

- Entry point: `CompactionWorker.runLoop` or manual test trigger
- Main classes: `CompactionPlanner`, `CompactionWorker`, `MergeIterator`, `ManifestManager`
- Files touched: input SSTables, output SSTables, `MANIFEST.mf`
- Steps:
  - choose a compaction job
  - open source and overlapping target SSTables
  - merge all versions in order
  - keep only versions still needed for active snapshots
  - drop tombstones when deeper overlap rules say it is safe
  - atomically install outputs and remove inputs
- Complexity: linear in total input bytes

### Recovery

- Entry point: `RecoveryManager.recover`
- Main classes: `ConsistencyChecker`, `WalRecovery`, `WalReader`
- Files touched: `MANIFEST.mf`, `wal/*.wal`, `sst/*.sst`
- Steps:
  - verify manifest-referenced SSTables exist and open
  - clean temp files from previous interrupted operations
  - replay WAL records above the flushed sequence
  - ignore incomplete batches in lenient mode
  - fail fast on corruption in strict mode
- Complexity: linear in the amount of unflushed WAL

## Section 6: What A Successful Build Actually Proves

### `mvn clean compile`

- Proves the full multi-module source tree compiles from a clean slate.
- Includes `nitrodb-core`, `nitrodb-benchmarks`, and `nitrodb-jcstress`.
- Important because it catches missing generated benchmark resources and cross-module breakage.

### `mvn test`

- Proves unit and integration tests pass.
- Observed on `2026-06-05`: `68` tests, `0` failures, `0` errors.
- What it covers:
  - WAL encode/decode and corruption handling
  - memtable behavior
  - SSTable write/read
  - bloom filter and sparse index behavior
  - manifest restore and checksum rejection
  - recovery replay
  - end-to-end put/get/delete/scan
  - snapshot visibility
  - block-cache usage
  - use-case validation
  - runtime metrics accumulation

### `mvn verify`

- Proves the full Maven reactor succeeds.
- Observed on `2026-06-05`: `BUILD SUCCESS`.
- What it additionally proves:
  - benchmark jar packages successfully
  - jcstress jar packages successfully
  - shaded artifacts are built for both executable suites

### Command-Level Proof

| Command | What It Proved | Observed Result |
|---|---|---|
| `.\.tools\apache-maven-3.9.16\bin\mvn.cmd test` | JUnit and integration suite correctness | `68` tests passed |
| `.\.tools\apache-maven-3.9.16\bin\mvn.cmd verify` | full multi-module verification and packaging | `BUILD SUCCESS` |
| `.\.tools\apache-maven-3.9.16\bin\mvn.cmd -pl nitrodb-core exec:java "-Dexec.mainClass=com.nitrodb.NitroDBExampleRunner" "-Dexec.args=example-data"` | runnable demo and restart recovery | `alpha=one`, `Recovered alpha=one`, `Recovered beta present=false` |
| `java -jar nitrodb-benchmarks\target\benchmarks.jar -l` | benchmark packaging and JMH metadata are correct | listed five workloads |
| `java -jar nitrodb-benchmarks\target\benchmarks.jar ".*Benchmark" -wi 1 -i 1 -f 1 -r 1s -w 1s` | quick benchmark execution path works | all benchmark workloads ran successfully |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\stress.ps1` | concurrency-focused validation path works | JUnit stress subset passed and JCStress finished with `42 passed, 0 failed` |

## Section 7: What To Show In Interviews

### 2-Minute Demo

- Show the README and explain that NitroDB is an embedded LSM-tree engine.
- Run the example runner.
- Point out the recovery proof in the output.
- Mention the modules: core, benchmarks, jcstress.

### 5-Minute Demo

- Show `NitroDB.java` and `NitroDBImpl.java`.
- Walk through put -> WAL -> memtable -> flush -> SSTable.
- Open `CrashRecoveryTest` and `NitroDBIntegrationTest`.
- Show `mvn test` or `mvn verify`.

### 10-Minute Demo

- Explain the read path with bloom filter, sparse index, and cache.
- Explain snapshot reads and why compaction must preserve old versions when snapshots exist.
- Show the quick benchmark results.
- Show the stress script output with `42 passed, 0 failed`.

## Section 8: How To Explain NitroDB To Different Audiences

### Non-Technical Person

NitroDB is a fast, durable notebook for software. An app can write down information, close, crash, reopen, and still have the same facts available.

### Recruiter

NitroDB is a storage-engine project in Java that demonstrates WAL durability, MVCC snapshots, compaction, crash recovery, benchmarking, and concurrency validation. It is much closer to systems programming than to standard application CRUD work.

### Software Engineer

NitroDB is a single-node embedded LSM-tree key-value engine. Writes go through a WAL and memtable, then flush to SSTables. Reads combine in-memory structures, bloom filters, sparse indexes, and a block cache. Recovery replays the WAL above the flushed manifest sequence.

### Senior Engineer

NitroDB is a deliberately scoped storage-engine implementation. The interesting parts are the sequencing model, flush/compaction pipeline, manifest replay, MVCC visibility rules, and the balance between correctness and operational simplicity.

### Database Engineer

NitroDB is a compact LSM engine with versioned keys, snapshot-bounded reads, append-only metadata, checksummed block storage, leveled compaction, and packaging for both JMH and JCStress. It is educational and runnable rather than a claim of production parity with mature engines.

## Section 9: Common Questions

### Architecture

1. Why an LSM-tree instead of a B-tree?
   Answer: NitroDB prioritizes append-friendly writes, immutable-file reasoning, and a compact educational implementation. Those are natural strengths of LSM designs.
2. Why keep the project embedded?
   Answer: It keeps the focus on storage-engine mechanics instead of RPC, clustering, authentication, and deployment plumbing.
3. What is the most important class?
   Answer: `NitroDBImpl` because it composes WAL, memtables, SSTables, compaction, metrics, and recovery into one runtime.

### Storage

4. Why use immutable SSTables?
   Answer: Immutability simplifies reads, recovery, manifest bookkeeping, and compaction install/remove semantics.
5. How are tombstones represented?
   Answer: As delete-type `MemTableEntry` objects that can flow through WAL, memtables, SSTables, and compaction.
6. How does NitroDB know which WAL segments are safe to ignore during recovery?
   Answer: The manifest tracks the flushed sequence, and recovery replays only WAL records above that threshold.

### Java

7. Why Java for a storage engine?
   Answer: Java provides strong concurrency primitives, file I/O, testing ecosystems, and enough low-level control to express the design clearly.
8. Where is off-heap memory used?
   Answer: In the block cache through `OffHeapAllocator`.
9. Why virtual threads for background workers?
   Answer: Flush, compaction, and metrics reporting are simple long-lived background tasks, and virtual threads are a clean fit in modern Java.

### Concurrency

10. How are concurrent writes controlled?
    Answer: NitroDB uses striped key locks for mutation serialization plus thread-safe core structures like `ConcurrentSkipListMap`.
11. How do readers avoid blocking writers?
    Answer: MVCC sequence numbers let readers choose visible versions without acquiring write locks on the whole engine.
12. What validates concurrency assumptions?
    Answer: JUnit concurrency tests plus the packaged JCStress suite.

### MVCC And Snapshots

13. How does a snapshot remain stable while writes continue?
    Answer: The snapshot captures a sequence number ceiling. Reads only accept versions at or below that ceiling.
14. Why can compaction not always drop old versions immediately?
    Answer: Active snapshots may still require them.
15. How are duplicate versions filtered in scans?
    Answer: `MergingIterator` merges all sources, and `IteratorUtils.deduplicating` keeps only the newest visible version per key.

### WAL And Recovery

16. What happens if the WAL tail is corrupted?
    Answer: Lenient mode stops replay at the corrupt tail; strict mode throws and aborts startup.
17. How are logical batches handled during recovery?
    Answer: `WalRecovery` tracks `BATCH_START` and `BATCH_END` and does not apply incomplete batches.
18. What happens to `.tmp` files from interrupted flushes or compactions?
    Answer: `ConsistencyChecker` deletes them during startup.

### Read Path

19. What does the bloom filter prove?
    Answer: It proves a negative key can often be rejected before any SSTable block read.
20. What does the sparse index do that the bloom filter does not?
    Answer: The bloom filter answers "maybe or no." The sparse index finds the candidate block when the answer is "maybe."
21. Why add a block cache if the OS already caches files?
    Answer: It gives engine-level control, explicit hit/miss metrics, and off-heap storage for repeated block reuse.

### Compaction

22. What does compaction improve?
    Answer: It reduces overlap, shrinks the number of SSTables a read may need to inspect, and garbage-collects obsolete versions.
23. What makes compaction correctness tricky?
    Answer: Snapshot visibility, tombstone retention rules, file-install atomicity, and input-file lifecycle.
24. How is manifest consistency preserved during compaction?
    Answer: `ManifestManager.applyCompaction` records the transition and rebuild logic can unwind incomplete transitions.

### Performance

25. What is the benchmark value of this project?
    Answer: The benchmarks prove the workload runner works and give a baseline for future profiling and tuning.
26. Are the benchmark numbers production throughput claims?
    Answer: No. The current reported numbers are quick smoke results, not a controlled performance study.
27. Where would you optimize first if read latency mattered?
    Answer: Block layout, cache behavior, bloom sizing, sparse-index density, and unnecessary object allocation on the read path.

### Testing

28. What is the difference between `mvn test` and `scripts/stress.ps1`?
    Answer: `mvn test` runs the main JUnit suite. `scripts/stress.ps1` runs a targeted JUnit subset plus the packaged JCStress concurrency suite.
29. What is the most valuable test in the repo?
    Answer: There is no single winner, but `CrashRecoveryTest`, `NitroDBIntegrationTest`, and the snapshot/concurrency tests carry the most end-to-end confidence.
30. What gap still exists despite the tests?
    Answer: The engine is still educational in scope. It is not validated for massive scale, distributed operation, or adversarial filesystem environments.

## Section 10: Engineering Challenges

1. Snapshot scan returns data newer than the snapshot.
   Root cause: iterator initialization or reseek logic ignores the snapshot ceiling.
   Fix: bound merged iteration by the snapshot sequence before deduplication.
   Tradeoff: more careful iterator composition.

2. A crash leaves a partial WAL batch.
   Root cause: replay treats records individually instead of as a logical unit.
   Fix: apply batch contents only after `BATCH_END`.
   Tradeoff: more WAL state during replay.

3. Compaction deletes a file while a reader still needs it.
   Root cause: file lifecycle is not coordinated with reader ownership.
   Fix: close and unregister readers before deleting input files.
   Tradeoff: slightly stricter sequencing.

4. Benchmarks package but do not list workloads.
   Root cause: the JMH metadata resource is missing from the shaded jar.
   Fix: preserve generated JMH outputs instead of deleting them before a no-op incremental compile.
   Tradeoff: rely on clean builds for full regeneration.

5. Metrics logging is too noisy for an embedded default.
   Root cause: default sink writes snapshots unconditionally.
   Fix: default to `NoOpMetricsSink` and keep logging as an opt-in sink.
   Tradeoff: observability must be explicitly enabled.

6. Recovery accepts a broken manifest.
   Root cause: metadata replay without checksum validation.
   Fix: validate each manifest frame CRC and reject corruption.
   Tradeoff: startup reads slightly more defensively.

7. Bloom filter claim is hard to defend without proof.
   Root cause: unit tests prove membership semantics but not read-path effect.
   Fix: add an SSTable reader test that proves a bloom-negative lookup performs zero block-cache requests.
   Tradeoff: test is proof-oriented rather than broad.

8. API options expose unsupported behavior.
   Root cause: `ttlMs` and per-read checksum knobs existed without engine support.
   Fix: remove those partial surfaces.
   Tradeoff: a narrower but honest API.

9. Delete operations were missing from write-throughput accounting.
   Root cause: mutation metrics only counted `put`.
   Fix: count deletes as writes and track a separate delete counter.
   Tradeoff: none; this is a correctness improvement for metrics.

10. Use-case claims were too abstract.
    Root cause: docs described plausible applications without explicit validation.
    Fix: add `NitroDBUseCaseTest` covering settings, preferences, embedded DB, metadata store, persistence cache, and bulk KV usage.
    Tradeoff: a slightly larger test suite for much stronger proof.

## Section 11: Design Defenses

1. Criticism: This is not a full production database.
   Response: Correct. It is a focused storage-engine project. The value is depth in WAL, compaction, MVCC, recovery, and performance mechanics.

2. Criticism: Java is the wrong language for a storage engine.
   Response: Java still supports the important systems concerns here: concurrency, file I/O, checksums, off-heap buffers, deterministic tests, and benchmark tooling.

3. Criticism: Why not just wrap RocksDB?
   Response: Wrapping RocksDB would not demonstrate design ownership. NitroDB exists to show implementation understanding.

4. Criticism: Concurrent skip lists are not the most memory-efficient memtable.
   Response: True, but they provide ordered concurrency with far lower implementation risk than a custom lock-free structure.

5. Criticism: The metrics system is lightweight.
   Response: Intentionally. The project demonstrates internal metric capture and sink publication without becoming an observability platform project.

6. Criticism: Quick benchmarks are not serious performance analysis.
   Response: Also true. They are proof that the JMH suite is functional and a baseline for future controlled studies.

7. Criticism: Manifest replay is simpler than mature database metadata systems.
   Response: Yes, and that is a deliberate tradeoff for clarity. The append-only manifest is easy to reason about and validate.

8. Criticism: There is no SQL or secondary indexing.
   Response: NitroDB targets the storage-engine layer, not the relational query layer.

9. Criticism: Why use bloom filters and sparse indexes together?
   Response: They solve different problems. Bloom filters reject negatives; sparse indexes locate candidate blocks for possible positives.

10. Criticism: Why keep old versions until compaction instead of eagerly deleting them?
    Response: Because active snapshots may still require those versions. Eager deletion would violate MVCC correctness.

