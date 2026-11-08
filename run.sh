#!/bin/bash
# Runs ConcurrentDeleteTest against one or more Quarkus versions.
# Usage: ./run.sh [version ...]   default: 3.2.12.Final 3.40.1.1 4.0.0.Beta3
# Needs Docker or Podman for the PostgreSQL Dev Service.
set -u
cd "$(dirname "$0")"
for v in "${@:-3.2.12.Final 3.40.1.1 4.0.0.Beta3}"; do
  case $v in 3.[0-9].*|3.1[0-9].*) rest=resteasy-reactive;; *) rest=rest;; esac
  echo "=== Quarkus $v"
  mvn -B -ntp -q clean test -Dtest=ConcurrentDeleteTest -Dquarkus.platform.version="$v" -Drest.artifact="$rest" -Dquarkus.analytics.disabled=true 2>&1 | grep -E 'RESULT41945|Tests run:|BUILD|HR000069' | sort -u | head -5
done
