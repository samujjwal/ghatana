#!/usr/bin/env bash
set -euo pipefail

export TP_ENVIRONMENT=staging
./scripts/run-content-proof-suite.sh
