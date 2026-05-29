#!/usr/bin/env bash
###############################################################################
# YAPPC E2E Test Execution Script (YAPPC-006)
#
# Runs Playwright E2E tests for the frontend and Java E2E tests.
# Handles environment setup, dev server startup, and canvas seeding.
#
# E2E Test Coverage Philosophy:
# This suite tests complete feature journeys through the YAPPC platform,
# not evidence generation. Tests focus on:
# - Complete project lifecycle workflows (Intent → Planning → Design → Implementation → Testing → Deploy → Evolve)
# - Agent execution flows (registration, code generation, code review, event routing)
# - Multi-tenant isolation and security boundaries
# - Authentication and authorization flows
# - Real user journeys across the platform
#
# Usage:
#   ./scripts/run-e2e-tests.sh                    # run all E2E tests (basic)
#   ./scripts/run-e2e-tests.sh --canvas            # enable canvas seeding
#   ./scripts/run-e2e-tests.sh --java-only        # Java E2E tests only
#   ./scripts/run-e2e-tests.sh --playwright-only  # Playwright tests only
#   ./scripts/run-e2e-tests.sh --ci               # CI mode (no dev server reuse)
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAPPC_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_DIR="$YAPPC_ROOT/frontend"
CORE_DIR="$YAPPC_ROOT/core"

# Parse arguments
CANVAS_SEED=false
JAVA_ONLY=false
PLAYWRIGHT_ONLY=false
CI_MODE=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --canvas) CANVAS_SEED=true; shift ;;
        --java-only) JAVA_ONLY=true; shift ;;
        --playwright-only) PLAYWRIGHT_ONLY=true; shift ;;
        --ci) CI_MODE=true; shift ;;
        *) echo "error: unknown argument '$1'" >&2; exit 2 ;;
    esac
done

cd "$YAPPC_ROOT"

echo "=== YAPPC E2E Test Execution ==="
echo "Canvas seeding: $CANVAS_SEED"
echo "Java E2E only: $JAVA_ONLY"
echo "Playwright only: $PLAYWRIGHT_ONLY"
echo "CI mode: $CI_MODE"
echo ""

# ─── Java E2E Tests ────────────────────────────────────────────────────────
if [[ "$JAVA_ONLY" == "false" ]]; then
    echo "=== Java E2E Tests ==="
    if [[ ! -d "$CORE_DIR" ]]; then
        echo "SKIP: core directory not found at $CORE_DIR"
    else
        echo "Running Java E2E tests with Gradle..."
        cd "$YAPPC_ROOT"
        if [[ "$CI_MODE" == "true" ]]; then
            ./gradlew :products:yappc:e2e-tests:test --rerun-tasks || {
                echo "FAIL: Java E2E tests failed"
                exit 1
            }
        else
            ./gradlew :products:yappc:e2e-tests:test || {
                echo "FAIL: Java E2E tests failed"
                exit 1
            }
        fi
        echo "OK: Java E2E tests passed"
    fi
    echo ""
fi

# ─── Playwright E2E Tests ───────────────────────────────────────────────────
if [[ "$PLAYWRIGHT_ONLY" == "false" ]]; then
    echo "=== Playwright E2E Tests ==="
    if [[ ! -d "$FRONTEND_DIR" ]]; then
        echo "SKIP: frontend directory not found at $FRONTEND_DIR"
    else
        cd "$FRONTEND_DIR"

        # Check if pnpm is installed
        if ! command -v pnpm &> /dev/null; then
            echo "ERROR: pnpm not found. Install pnpm first."
            exit 1
        fi

        # Check if dependencies are installed
        if [[ ! -d "node_modules" ]]; then
            echo "Installing frontend dependencies..."
            pnpm install
        fi

        # Set environment variables
        export PLAYWRIGHT_BASE_URL="${PLAYWRIGHT_BASE_URL:-http://localhost:7002}"
        export PLAYWRIGHT_ENABLE_CANVAS="${CANVAS_SEED}"

        if [[ "$CI_MODE" == "true" ]]; then
            export CI=1
            export PLAYWRIGHT_SERVE_HTML=0
        else
            export PLAYWRIGHT_SERVE_HTML="${PLAYWRIGHT_SERVE_HTML:-0}"
        fi

        echo "Environment:"
        echo "  PLAYWRIGHT_BASE_URL=$PLAYWRIGHT_BASE_URL"
        echo "  PLAYWRIGHT_ENABLE_CANVAS=$PLAYWRIGHT_ENABLE_CANVAS"
        echo "  CI=$CI"
        echo ""

        echo "Running Playwright E2E tests..."
        pnpm exec playwright test || {
            echo "FAIL: Playwright E2E tests failed"
            exit 1
        }
        echo "OK: Playwright E2E tests passed"
    fi
    echo ""
fi

echo "=== All E2E tests passed ==="
