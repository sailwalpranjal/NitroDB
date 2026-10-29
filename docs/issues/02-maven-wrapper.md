# Replace the committed Maven distribution with the Maven wrapper

**Labels:** type:chore, priority:P0, area:docs

## Problem

`.tools/apache-maven-3.9.16/` and its zip (about 20 MB, 97 files) are committed. The README build commands use a Windows path into it. On every other platform the repository carries dead weight.

## Acceptance criteria

- [ ] `mvnw` and `.mvn/wrapper/` are added and `./mvnw -B test -pl nitrodb-core` passes.
- [ ] `.tools/` is removed from the working tree and added to `.gitignore`.
- [ ] README build commands use `./mvnw` (and `mvnw.cmd` on Windows).
- [ ] CI uses the wrapper.
- [ ] History is left alone; the files stay in old commits.
