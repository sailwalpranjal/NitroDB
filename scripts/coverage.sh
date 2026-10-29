#!/usr/bin/env sh
set -eu
./mvnw test jacoco:report
