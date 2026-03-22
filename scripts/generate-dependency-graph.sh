#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Dependency Graph Generator (GOV-4)
#
# Usage: ./scripts/generate-dependency-graph.sh [--format dot|mermaid] [--scope platform|products|all]
#
# Generates a dependency graph of Gradle modules and writes it to:
#   docs/audits/dependency-graph-YYYY-MM.{dot|mmd}
#
# Requires: none (pure bash + grep)
# Optional: graphviz (for dot → svg rendering)
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FORMAT="mermaid"
SCOPE="platform"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --format) FORMAT="$2"; shift 2 ;;
        --scope)  SCOPE="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

DATE=$(date '+%Y-%m')
mkdir -p docs/audits

if [[ "$FORMAT" == "mermaid" ]]; then
    OUTPUT="docs/audits/dependency-graph-${DATE}.mmd"
else
    OUTPUT="docs/audits/dependency-graph-${DATE}.dot"
fi

echo "Generating ${FORMAT} dependency graph (scope: ${SCOPE})..."

# Helper: normalize gradle path to node ID
node_id() {
    echo "$1" | tr ':/' '_' | sed 's/^_//'
}

# Helper: generate a display label from the gradle path
node_label() {
    echo "$1" | awk -F: '{print $NF}'
}

if [[ "$FORMAT" == "mermaid" ]]; then
    {
        echo "graph TD"
        echo "    %% Ghatana Platform Dependency Graph — Generated ${DATE}"
        echo "    %% Scope: ${SCOPE}"
        echo ""
    } > "$OUTPUT"

    # Find all build files in scope
    case "$SCOPE" in
        platform) SEARCH_DIRS="platform/java" ;;
        products) SEARCH_DIRS="products" ;;
        all)      SEARCH_DIRS="platform products" ;;
        *)        SEARCH_DIRS="platform/java" ;;
    esac

    while IFS= read -r build_file; do
        # Derive module path from build file location
        MODULE_DIR=$(dirname "$build_file")
        # Get the Gradle project path from settings
        REL_PATH="${MODULE_DIR#$ROOT/}"
        GRADLE_PATH=":$(echo "$REL_PATH" | tr '/' ':')"
        SRC_ID=$(node_id "$GRADLE_PATH")
        SRC_LABEL=$(node_label "$GRADLE_PATH")

        # Extract dependencies (project refs)
        while IFS= read -r dep_line; do
            DEP_PATH=$(echo "$dep_line" | grep -oP '":[\w:/-]+"' | tr -d '"' | head -1)
            [[ -z "$DEP_PATH" ]] && continue
            DST_ID=$(node_id "$DEP_PATH")
            DST_LABEL=$(node_label "$DEP_PATH")

            # Classify dependency type
            if echo "$dep_line" | grep -q "^    api("; then
                ARROW="-->"
            else
                ARROW="-.->|impl|"
            fi

            echo "    ${SRC_ID}[${SRC_LABEL}] ${ARROW} ${DST_ID}[${DST_LABEL}]" >> "$OUTPUT"
        done < <(grep -E '^\s+(api|implementation|runtimeOnly)\(project\(' "$build_file" 2>/dev/null || true)
    done < <(find $SEARCH_DIRS -name "build.gradle.kts" 2>/dev/null)

    echo ""
    echo "Mermaid graph written to: ${OUTPUT}"
    echo ""
    echo "To render in your browser:"
    echo "  1. Open https://mermaid.live"
    echo "  2. Paste the content of ${OUTPUT}"
    echo ""
    echo "Or use the VS Code Markdown Preview with mermaid support."

elif [[ "$FORMAT" == "dot" ]]; then
    {
        echo "digraph GhataanModules {"
        echo "    rankdir=LR;"
        echo "    node [shape=box, style=filled, fillcolor=lightblue];"
        echo "    // Generated: ${DATE}  Scope: ${SCOPE}"
        echo ""
    } > "$OUTPUT"

    case "$SCOPE" in
        platform) SEARCH_DIRS="platform/java" ;;
        products) SEARCH_DIRS="products" ;;
        all)      SEARCH_DIRS="platform products" ;;
        *)        SEARCH_DIRS="platform/java" ;;
    esac

    while IFS= read -r build_file; do
        MODULE_DIR=$(dirname "$build_file")
        REL_PATH="${MODULE_DIR#$ROOT/}"
        GRADLE_PATH=":$(echo "$REL_PATH" | tr '/' ':')"
        SRC_ID=$(node_id "$GRADLE_PATH")

        while IFS= read -r dep_line; do
            DEP_PATH=$(echo "$dep_line" | grep -oP '":[\w:/-]+"' | tr -d '"' | head -1)
            [[ -z "$DEP_PATH" ]] && continue
            DST_ID=$(node_id "$DEP_PATH")

            STYLE=""
            echo "$dep_line" | grep -q "implementation(" && STYLE=' [style=dashed]'
            echo "    \"${SRC_ID}\" -> \"${DST_ID}\"${STYLE};" >> "$OUTPUT"
        done < <(grep -E '^\s+(api|implementation|runtimeOnly)\(project\(' "$build_file" 2>/dev/null || true)
    done < <(find $SEARCH_DIRS -name "build.gradle.kts" 2>/dev/null)

    echo "}" >> "$OUTPUT"
    echo ""
    echo "DOT graph written to: ${OUTPUT}"
    echo ""
    if command -v dot &>/dev/null; then
        SVG="${OUTPUT%.dot}.svg"
        dot -Tsvg "$OUTPUT" -o "$SVG" && echo "SVG rendered to: ${SVG}"
    else
        echo "To render: install graphviz and run:"
        echo "  dot -Tsvg ${OUTPUT} -o ${OUTPUT%.dot}.svg"
    fi
fi
