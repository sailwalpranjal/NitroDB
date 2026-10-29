# Metrics

NitroDB is a library with no telemetry. This is a plan for what I would measure, split into engine behaviour (which I can measure) and adoption (which I cannot, yet). Nothing here is measured except where stated.

## Engine questions

1. How long do writes stall during compaction?
2. How much space does the engine use compared with the logical data size (space amplification), and how many bytes does it write per user byte (write amplification)?
3. How often does a point read touch more than one SSTable?

## What already exists

`metrics/NitroDBMetrics.java` accumulates counters and `MetricsReporter` publishes them to a `MetricsSink`. I would add these counters if they are not already there:

| Counter | Question |
|---|---|
| `bytes_written_user`, `bytes_written_disk` | 2 |
| `put_latency_micros` histogram, with a `compaction_active` flag | 1 |
| `sstables_probed_per_get` histogram | 3 |
| `bloom_negative_skips`, `bloom_false_positives` | 3 |
| `snapshot_open_count`, `oldest_snapshot_age_ms` | cleanup delay under long-lived snapshots |

## Queries

Write amplification from the counters:

```
write_amplification = bytes_written_disk / bytes_written_user
```

Stall check from a latency log (CSV of `ts,put_micros,compaction_active`):

```sql
SELECT compaction_active,
       COUNT(*) AS puts,
       percentile_cont(0.99) WITHIN GROUP (ORDER BY put_micros) AS p99_micros
FROM put_log
GROUP BY compaction_active;
```

## Adoption signals (opt-in, public)

GitHub stars, forks and issues; nothing is collected from the library itself.

## Guardrails that exist today

- `mvn test -pl nitrodb-core`: 68 tests (re-run for this change: 0 failures, 0 errors).
- JCStress suite: 42 of 42 outcomes passed in the run recorded in the architecture document. Not re-run for this change.
