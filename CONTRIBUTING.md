# Contributing to NitroDB

This is a personal project that I maintain on my own, so reviews are not instant. Small, focused changes get merged fastest.

## Before you start

For anything larger than a typo or a one-line fix, open an issue first using the feature request template. It asks for the problem and the acceptance criteria. That conversation is cheaper than a rejected pull request.

## Local setup

```
# Java 21 or newer and Maven 3.9 (a copy of Maven is committed under .tools/)
mvn -B compile
```

## Running the tests

```
mvn -B test -pl nitrodb-core
```

Please run them before you push. CI runs the same command.

## Pull requests

- Branch from the default branch and keep the change to one idea.
- Write commit messages in the imperative: "fix off-by-one in window sizing", not "fixed bug".
- If you change a number quoted in the README or in `docs/`, say where the new number came from.
- Add an entry to `CHANGELOG.md` under Unreleased.

## Reporting security problems

Do not open a public issue. See [SECURITY.md](SECURITY.md).
