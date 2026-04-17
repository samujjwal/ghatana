#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT="${1:-local}"

echo "Running remediation proof suite for '$ENVIRONMENT'"
./scripts/collect-critical-journey-evidence.sh "$ENVIRONMENT"
./scripts/collect-gdpr-deletion-evidence.sh "$ENVIRONMENT"
./scripts/verify-object-storage-encryption.sh

echo "Remediation proof suite complete"
