#!/bin/bash
# Cleanup incorrect Go logs service code
# Per roadmap: all server services should be Java

set -e

echo "=== Cleaning up Go logs service code ==="
echo "Reason: Roadmap specifies Java-first for all server services"
echo ""

cd "$(dirname "$0")/.."

# Remove Go internal directory
if [ -d "services/logs-service/internal" ]; then
    echo "Removing services/logs-service/internal/ (Go code)"
    rm -rf services/logs-service/internal/
    echo "✅ Removed Go logs service code"
else
    echo "✅ Go logs service code already removed"
fi

echo ""
echo "=== Cleanup complete ==="
echo "Java logs service implementation in: services/logs-service/src/main/java/"
echo ""
