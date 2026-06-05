# NitroDB

NitroDB is a single-node embedded key-value storage engine for Java 21+. It exists to demonstrate modern storage-engine internals in a runnable, reviewable codebase: write-ahead logging, concurrent memtables, immutable SSTables, bloom filters, sparse indexes, MVCC snapshots, leveled compaction, crash recovery, block caching, and benchmarks.

## Why It Is Interesting

- It focuses on storage-engine mechanics instead of wrapping a CRUD app around a database.
- It is small enough to understand end to end, but deep enough to discuss with senior engineers.
- It is verified with unit tests, integration tests, recovery tests, a packaged JMH suite, and a packaged JCStress suite.

## Feature Set

- `put`, `get`, `delete`, `scan`, and `getSnapshot` API
- WAL-backed durability and restart recovery
- active and immutable memtables with background flush
- SSTable persistence with checksums
- bloom filters and sparse indexes on the read path
- MVCC snapshot reads and snapshot-stable range scans
- leveled compaction with obsolete-version cleanup
- sharded block cache
- internal metrics with pluggable sinks
- JMH benchmarks and JCStress concurrency checks

## Architecture In One Minute

- Writes append to the WAL, then land in the active memtable.
- When the memtable crosses its threshold, it becomes immutable and a flush worker writes it to a level-0 SSTable.
- Reads check the active memtable, immutable memtables, and then SSTables.
- SSTable reads use key-range checks, bloom filters, sparse indexes, and the block cache to avoid unnecessary block reads.
- Recovery opens the manifest, validates on-disk state, deletes temp files, and replays unflushed WAL segments into a fresh memtable.

## Build

```powershell
.\.tools\apache-maven-3.9.16\bin\mvn.cmd clean compile
.\.tools\apache-maven-3.9.16\bin\mvn.cmd test
.\.tools\apache-maven-3.9.16\bin\mvn.cmd verify
```

Verified on `2026-06-05`:

- `mvn test`: `68` tests, `0` failures, `0` errors
- `mvn verify`: `BUILD SUCCESS`
- `java -jar nitrodb-benchmarks\target\benchmarks.jar -l`: workload listing works
- `scripts\stress.ps1`: passed, including `42 passed, 0 failed` JCStress outcomes

## Quick Demonstration

```powershell
.\.tools\apache-maven-3.9.16\bin\mvn.cmd -pl nitrodb-core exec:java "-Dexec.mainClass=com.nitrodb.NitroDBExampleRunner" "-Dexec.args=example-data"
```

Observed output:

```text
alpha=one
Recovered alpha=one
Recovered beta present=false
```

## Repository Structure

- `nitrodb-core`: engine, example runner, and correctness tests
- `nitrodb-benchmarks`: packaged JMH benchmarks
- `nitrodb-jcstress`: packaged JCStress concurrency suite
- `scripts`: helper scripts, including the stress run
- `BluePrint.md`: original pre-implementation architecture blueprint plus post-implementation findings

## Documentation

- [Architecture-and-Design.md](/docs/Architecture-and-Design.md)
- [Project-Understanding-and-Interview-Guide.md](/docs/Project-Understanding-and-Interview-Guide.md)
