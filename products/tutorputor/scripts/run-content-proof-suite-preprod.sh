#!/usr/bin/env bash
set -euo pipefail

export TP_ENVIRONMENT=preprod
./scripts/run-content-proof-suite.sh
