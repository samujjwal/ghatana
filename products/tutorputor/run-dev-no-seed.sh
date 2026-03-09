#!/usr/bin/env bash
# Run dev environment WITHOUT automatic seeding
# Seed can be run manually later

set -euo pipefail

export TUTORPUTOR_SKIP_SEED=true

echo "🚀 Starting dev environment (seed skipped - run manually if needed)"
echo ""

./run-dev.sh
