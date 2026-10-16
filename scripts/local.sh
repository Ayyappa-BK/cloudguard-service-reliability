#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -B verify
python3 scripts/downstream.py &
DEP=$!
java -jar target/cloudguard-1.0.0.jar &
APP=$!
trap 'kill "$APP" "$DEP" 2>/dev/null || true' EXIT INT TERM
wait "$APP"
