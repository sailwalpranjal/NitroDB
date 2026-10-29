# Run JCStress on a schedule in CI

**Labels:** type:chore, priority:P0, area:benchmarks

## Problem

The 42 of 42 JCStress result was recorded once, on one machine. A change to the memtable, snapshot manager or compaction code could break a concurrency guarantee and nothing would notice.

## Acceptance criteria

- [ ] A scheduled workflow (weekly) builds `nitrodb-jcstress` and runs it in quick mode.
- [ ] The workflow fails if any outcome is forbidden or interesting-but-unexpected.
- [ ] The result summary is attached to the run.
- [ ] `docs/Architecture-and-Design.md` links the workflow instead of quoting a one-off result.
