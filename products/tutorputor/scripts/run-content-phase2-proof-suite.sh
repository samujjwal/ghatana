#!/usr/bin/env bash
set -euo pipefail

./scripts/verify-content-phase2-routes.sh
./scripts/collect-content-phase2-validation-evidence.sh
