# Model-based test against a TreeMap, with restarts

**Labels:** type:feature, priority:P1, area:recovery

## Problem

The current tests check specific scenarios. Nothing checks that an arbitrary sequence of puts, deletes, scans, snapshots and restarts always agrees with a trivially correct model.

## Acceptance criteria

- [ ] A test generates random sequences of `put`, `delete`, `get`, `scan`, `getSnapshot` and clean restart, applies each to NitroDB and to a `TreeMap`, and asserts equal results after every step.
- [ ] Sequences are seeded; a failure prints the seed and the minimal failing prefix.
- [ ] It includes at least one run that forces memtable rotation and a compaction (small `memTableSizeBytes`).
- [ ] It runs inside `mvn test` in under 30 seconds.
