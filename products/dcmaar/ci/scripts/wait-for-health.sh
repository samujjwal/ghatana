#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <url> <timeout-seconds> [interval-seconds]"
  exit 2
fi

URL="$1"
TIMEOUT="$2"
INTERVAL="${3:-1}"

start=$(date +%s)
end=$((start + TIMEOUT))

while true; do
  if curl -sSf "$URL" >/dev/null 2>&1; then
    echo "OK: $URL"
    exit 0
  fi
  now=$(date +%s)
  if [ "$now" -ge "$end" ]; then
    echo "ERROR: Timed out waiting for $URL after ${TIMEOUT}s"
    exit 1
  fi
  sleep "$INTERVAL"
done
