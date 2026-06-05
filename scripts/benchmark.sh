#!/usr/bin/env sh
set -eu
mvn -pl nitrodb-benchmarks package -DskipTests
java -server -XX:+UseG1GC -XX:MaxDirectMemorySize=2g -jar nitrodb-benchmarks/target/benchmarks.jar
