# Investigate the compaction load benchmark result

**Labels:** type:bug, priority:P0, area:benchmarks

## Problem

`CompactionLoadBenchmark.writeLoad` measured about 96 ops/s in the smoke run recorded in `docs/Architecture-and-Design.md`, against about 142,000 ops/s for `WriteBenchmark.sequentialPuts`. Either the benchmark is doing something unrepresentative or writes stall during compaction.

## Acceptance criteria

- [ ] A written finding (in the architecture document) says which it is, with the evidence.
- [ ] If it is a stall, an issue is opened for the cause and a test or benchmark pins the behaviour.
- [ ] If it is the benchmark, the benchmark is fixed and the new number replaces the old one.
- [ ] The run records hardware, JVM version, JVM flags and repetition counts.
