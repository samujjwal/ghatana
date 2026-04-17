#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT="${1:-local}"

echo "Running social proof suite for '$ENVIRONMENT'"
./scripts/collect-social-validation-evidence.sh
./scripts/verify-social-routes.sh

echo "Social proof suite complete"
