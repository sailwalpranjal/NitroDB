#!/usr/bin/env sh
set -eu
mvn -pl nitrodb-core -Dtest='NitroDBConcurrentTest,CrashRecoveryTest' test
mvn -pl nitrodb-jcstress -am -DskipTests package
java -jar nitrodb-jcstress/target/jcstress.jar \
  -t 'ConcurrentWriteReadStressTest|SnapshotIsolationStressTest|CompactionRaceStressTest' \
  -m quick \
  -f 1
