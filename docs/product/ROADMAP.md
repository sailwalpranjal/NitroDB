# Roadmap

Scores are my own judgement. RICE = Reach x Impact x Confidence / Effort. Reach is on a 1-10 scale, Impact is 0.25 / 0.5 / 1 / 2 / 3, Confidence is a fraction, Effort is person-weeks. ICE = (Impact + Confidence + Ease) / 3, each out of 10.

## Now

| Item | Why | Score (my judgement) |
|---|---|---|
| Investigate the 96 ops/s result of `CompactionLoadBenchmark.writeLoad` | Either the benchmark is wrong or writes stall during compaction; both are worth knowing | RICE: 5 x 2 x 0.6 / 1 = **6.0** |
| Run the JCStress suite in CI on a schedule | The 42/42 result is recorded once; a regression would go unnoticed | RICE: 6 x 2 x 0.7 / 1 = **8.4** |
| Publish a controlled benchmark note (hardware, JVM flags, repetitions) | The only numbers today are quick smoke runs | ICE: (5 + 7 + 6) / 3 = **6.0** |

## Next

| Item | Why | Score (my judgement) |
|---|---|---|
| Block compression (for example LZ4) | Largest listed missing feature for disk footprint | RICE: 5 x 1 x 0.7 / 3 = **1.2** |
| A model-based test: random operations against `NitroDB` and a `TreeMap`, with restarts in between | Strong evidence of correctness for the whole read and write path | RICE: 6 x 2 x 0.6 / 2 = **3.6** |
| TTL in the write API | Common request for key-value stores | RICE: 4 x 1 x 0.5 / 2 = **1.0** |

## Later

| Item | Why | Score (my judgement) |
|---|---|---|
| Metrics integration with a standard exporter | Sinks exist; no exporter ships | ICE: (3 + 6 + 6) / 3 = **5.0** |
| Replication | Different product | ICE: (4 + 2 + 1) / 3 = **2.3** |
