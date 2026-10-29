#!/usr/bin/env sh
set -eu
./mvnw -pl nitrodb-core -Dtest='NitroDBConcurrentTest,CrashRecoveryTest' test
./mvnw -pl nitrodb-jcstress -am -DskipTests package
java -jar nitrodb-jcstress/target/jcstress.jar \
  -t 'ConcurrentWriteReadStressTest|SnapshotIsolationStressTest|CompactionRaceStressTest' \
  -m quick \
  -f 1
