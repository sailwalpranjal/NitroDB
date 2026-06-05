#!/usr/bin/env sh
set -eu
mvn test jacoco:report
