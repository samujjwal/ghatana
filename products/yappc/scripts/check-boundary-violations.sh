#!/usr/bin/env bash
###############################################################################
# YAPPC Architecture Boundary Enforcement (YAPPC-018)
#
# Runs dependency-cruiser over the frontend workspace and Checkstyle-style
# package-boundary grep checks over the Java core.
#
# Exit codes:
#   0 — no violations detected
#   1 — TypeScript boundary violations found
#   2 — Java cross-module import violations found
#   3 — both TS and Java violations found
#
# Usage:
#   ./scripts/check-boundary-violations.sh           # report only
#   ./scripts/check-boundary-violations.sh --ci      # fail on violations
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAPPC_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_DIR="$YAPPC_ROOT/frontend"
CORE_DIR="$YAPPC_ROOT/core"

CI_MODE=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --ci) CI_MODE=true; shift ;;
        *) echo "error: unknown argument '$1'" >&2; exit 2 ;;
    esac
done

TS_VIOLATIONS=0
JAVA_VIOLATIONS=0

# ─── TypeScript: dependency-cruiser ───────────────────────────────────────────
echo ""
echo "=== TypeScript boundary check (dependency-cruiser) ==="

if [[ ! -d "$FRONTEND_DIR" ]]; then
    echo "SKIP: frontend directory not found at $FRONTEND_DIR"
else
    DEPCRUISE_BIN="$FRONTEND_DIR/node_modules/.bin/depcruise"
    CONFIG="$FRONTEND_DIR/.dependency-cruiser.js"

    if [[ ! -x "$DEPCRUISE_BIN" ]]; then
        echo "WARN: dependency-cruiser not installed at $DEPCRUISE_BIN — skipping TS check"
        echo "      Run: cd frontend && pnpm install"
    elif [[ ! -f "$CONFIG" ]]; then
        echo "WARN: .dependency-cruiser.js not found at $CONFIG — skipping TS check"
    else
        echo "Running dependency-cruiser on frontend/apps and frontend/libs..."
        set +e
        "$DEPCRUISE_BIN" \
            --config "$CONFIG" \
            --output-type err \
            "$FRONTEND_DIR/apps" "$FRONTEND_DIR/libs" 2>&1
        DC_EXIT=$?
        set -e

        if [[ $DC_EXIT -ne 0 ]]; then
            echo "FAIL: TypeScript boundary violations detected (exit $DC_EXIT)"
            TS_VIOLATIONS=1
        else
            echo "OK: No TypeScript boundary violations"
        fi
    fi
fi

# ─── Java: cross-module package boundary checks ───────────────────────────────
echo ""
echo "=== Java cross-module boundary check ==="

if [[ ! -d "$CORE_DIR" ]]; then
    echo "SKIP: core directory not found at $CORE_DIR"
else
    JAVA_REPORT=$(mktemp)

    # Rule 1: frontend/* must not be imported from core Java
    echo "Checking: core Java must not import from products/yappc/frontend..."
    grep -rn "import.*yappc\.frontend\." "$CORE_DIR" --include="*.java" 2>/dev/null \
        >> "$JAVA_REPORT" || true

    # Rule 2: yappc-domain must not import from yappc-services or yappc-api
    echo "Checking: yappc-domain must not import from yappc-services..."
    DOMAIN_SRC="$CORE_DIR/yappc-domain-impl/src/main/java"
    if [[ -d "$DOMAIN_SRC" ]]; then
        grep -rn "import com\.ghatana\.yappc\.services\." "$DOMAIN_SRC" --include="*.java" 2>/dev/null \
            >> "$JAVA_REPORT" || true
        grep -rn "import com\.ghatana\.yappc\.api\." "$DOMAIN_SRC" --include="*.java" 2>/dev/null \
            >> "$JAVA_REPORT" || true
    fi

    # Rule 3: agents must not import from each other (cross-agent imports)
    echo "Checking: agent modules must not cross-import..."
    AGENTS_DIR="$CORE_DIR/agents"
    if [[ -d "$AGENTS_DIR" ]]; then
        for agent_dir in "$AGENTS_DIR"/*/; do
            agent_name=$(basename "$agent_dir")
            src="$agent_dir/src/main/java"
            [[ -d "$src" ]] || continue
            # Each agent must not import from a sibling agent package
            while IFS= read -r -d '' other_dir; do
                other_name=$(basename "$other_dir")
                [[ "$other_name" == "$agent_name" ]] && continue
                other_pkg=$(echo "$other_name" | tr '-' '.')
                grep -rn "import.*agents\.$other_pkg\." "$src" --include="*.java" 2>/dev/null \
                    >> "$JAVA_REPORT" || true
            done < <(find "$AGENTS_DIR" -maxdepth 1 -mindepth 1 -type d -print0)
        done
    fi

    # Rule 4: infrastructure/datacloud must not import from core/yappc-services
    echo "Checking: infrastructure must not import from yappc-services service layer..."
    INFRA_SRC="$YAPPC_ROOT/infrastructure/datacloud/src/main/java"
    if [[ -d "$INFRA_SRC" ]]; then
        grep -rn "import com\.ghatana\.yappc\.services\." "$INFRA_SRC" --include="*.java" 2>/dev/null \
            >> "$JAVA_REPORT" || true
    fi

    JAVA_COUNT=$(wc -l < "$JAVA_REPORT" | tr -d ' ')
    if [[ "$JAVA_COUNT" -gt 0 ]]; then
        echo "FAIL: Java cross-module boundary violations found ($JAVA_COUNT):"
        cat "$JAVA_REPORT"
        JAVA_VIOLATIONS=1
    else
        echo "OK: No Java cross-module boundary violations"
    fi
    rm -f "$JAVA_REPORT"
fi

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "=== Summary ==="
[[ $TS_VIOLATIONS -eq 0 ]]   && echo "TypeScript: PASS" || echo "TypeScript: FAIL ($TS_VIOLATIONS violation set)"
[[ $JAVA_VIOLATIONS -eq 0 ]] && echo "Java:       PASS" || echo "Java:       FAIL ($JAVA_VIOLATIONS violation set)"

if [[ "$CI_MODE" == true ]]; then
    EXIT_CODE=$(( TS_VIOLATIONS + (JAVA_VIOLATIONS * 2) ))
    if [[ $EXIT_CODE -ne 0 ]]; then
        echo ""
        echo "CI FAIL: boundary violations detected (exit $EXIT_CODE)" >&2
        exit $EXIT_CODE
    fi
fi

echo "Boundary check complete."
