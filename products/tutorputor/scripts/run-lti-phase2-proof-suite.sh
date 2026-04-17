#!/usr/bin/env bash
set -euo pipefail

./scripts/verify-lti-phase2-routes.sh
./scripts/collect-lti-phase2-validation-evidence.sh
