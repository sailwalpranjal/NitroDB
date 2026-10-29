# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow semantic versioning.

## [Unreleased]

## [0.2.0] - proposed tag `v0.2.0`

### Added
- MIT licence.
- Issue and pull request templates, contributing, security and conduct files.
- Product documents under `docs/product/`.

### Fixed
- A key could be unreadable, or lost from the flushed copy, while the active memtable was being rotated. Writers now hold a read lock during the put and rotation takes the write lock, and the frozen table is made readable before the empty one becomes active. Covered by `writesStayVisibleAndSurviveRestartWhileMemtablesRotate` (failed 3 of 3 runs before the change). 69 tests; JCStress still 42 of 42.

### Changed
- The 20 MB Maven distribution under `.tools/` is replaced by the Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper`). Build and CI commands use it.
- README restructured: who it is for, how it works, evidence, limits. The unsupported-for-production position is stated first.
- Documentation links in the README are relative, so they work on GitHub.

## [0.1.0] - proposed tag `v0.1.0`

First version: WAL with recovery, memtable and background flush, SSTables with bloom filters and sparse indexes, MVCC snapshots, leveled compaction, sharded block cache, metrics sinks, JMH and JCStress modules.
