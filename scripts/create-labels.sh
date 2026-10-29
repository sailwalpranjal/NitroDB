#!/usr/bin/env bash
# Creates the labels described in docs/project-board.md.
# Run from the repo root after `gh auth login`. --force makes it safe to re-run.
set -euo pipefail

gh label create "type:bug" --color d73a4a --description "Something is broken" --force
gh label create "type:feature" --color 1d76db --description "New capability or change in behaviour" --force
gh label create "type:docs" --color 0075ca --description "Documentation only" --force
gh label create "type:chore" --color cfd3d7 --description "Tooling, CI, dependencies" --force
gh label create "priority:P0" --color b60205 --description "Do now" --force
gh label create "priority:P1" --color d93f0b --description "Do next" --force
gh label create "priority:P2" --color fbca04 --description "Do later" --force
gh label create "needs-triage" --color ededed --description "Not yet reviewed" --force
gh label create "needs-info" --color d876e3 --description "Waiting for more detail from the reporter" --force
gh label create "blocked" --color 000000 --description "Cannot move until something else lands" --force
gh label create "area:wal" --color 5319e7 --description "Touches wal" --force
gh label create "area:memtable" --color 5319e7 --description "Touches memtable" --force
gh label create "area:sstable" --color 5319e7 --description "Touches sstable" --force
gh label create "area:compaction" --color 5319e7 --description "Touches compaction" --force
gh label create "area:mvcc" --color 5319e7 --description "Touches mvcc" --force
gh label create "area:recovery" --color 5319e7 --description "Touches recovery" --force
gh label create "area:benchmarks" --color 5319e7 --description "Touches benchmarks" --force
gh label create "area:docs" --color 5319e7 --description "Touches docs" --force
