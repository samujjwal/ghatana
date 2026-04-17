#!/usr/bin/env bash
set -euo pipefail

export TP_ENVIRONMENT=production
./scripts/run-content-proof-suite.sh
