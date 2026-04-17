#!/usr/bin/env bash
set -euo pipefail

./scripts/verify-content-routes.sh
./scripts/collect-content-validation-evidence.sh
