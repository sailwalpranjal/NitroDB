$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$maven = Join-Path $repoRoot ".tools\apache-maven-3.9.16\bin\mvn.cmd"

if (-not (Test-Path $maven)) {
    $maven = "mvn"
}

& $maven -pl nitrodb-core "-Dtest=NitroDBConcurrentTest,CrashRecoveryTest" test
& $maven -pl nitrodb-jcstress -am -DskipTests package
java -jar nitrodb-jcstress\target\jcstress.jar `
    -t "ConcurrentWriteReadStressTest|SnapshotIsolationStressTest|CompactionRaceStressTest" `
    -m quick `
    -f 1
