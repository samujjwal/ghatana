#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

quarter() {
    python3 - <<'PY'
import datetime
today = datetime.date.today()
quarter = (today.month - 1) // 3 + 1
print(f"{today.year}-Q{quarter}")
PY
}

QUARTER="$(quarter)"
REPORT_FILE="docs/audits/boundary-scores-${QUARTER}.md"
GENERATED_AT="$(date '+%Y-%m-%d %H:%M:%S')"

module_score() {
    local count="$1"
    if [[ "$count" -le 5 ]]; then
        echo 25
    elif [[ "$count" -le 10 ]]; then
        echo 20
    elif [[ "$count" -le 15 ]]; then
        echo 15
    elif [[ "$count" -le 20 ]]; then
        echo 10
    elif [[ "$count" -le 30 ]]; then
        echo 5
    else
        echo 0
    fi
}

count_modules() {
    local product="$1"
    find "products/${product}" -name 'build.gradle.kts' -not -path '*/build/*' 2>/dev/null | wc -l | tr -d ' '
}

count_archunit_tests() {
    local product="$1"
    (grep -ERl 'com\.tngtech\.archunit|@ArchTest|AnalyzeClasses' "products/${product}" \
        --include='*.java' \
        --include='*.kt' \
        --exclude-dir=build \
        2>/dev/null || true) | wc -l | tr -d ' '
}

count_platform_internal_imports() {
    local product="$1"
    (grep -ERh '^import com\.ghatana\.(platform|core)\..*\.(impl|internal)\.' "products/${product}" \
        --include='*.java' \
        --include='*.kt' \
        --exclude-dir=build \
        2>/dev/null || true) | wc -l | tr -d ' '
}

count_cross_product_deps() {
    local product="$1"
    local total=0
    while IFS= read -r build_file; do
        while IFS= read -r dep; do
            [[ -z "$dep" ]] && continue
            local target
            target="$(echo "$dep" | sed 's|^products:||' | cut -d':' -f1)"
            if [[ "$target" != "$product" ]]; then
                total=$((total + 1))
            fi
        done < <(grep -ho 'project(":products:[^"]*")' "$build_file" 2>/dev/null | sed 's/.*project(":\([^"]*\)").*/\1/' || true)
    done < <(find "products/${product}" -name 'build.gradle.kts' -not -path '*/build/*' 2>/dev/null)
    echo "$total"
}

boundary_score() {
    local internal_imports="$1"
    local cross_product_deps="$2"
    local score=25

    if [[ "$internal_imports" -gt 0 ]]; then
        score=$((score - 15))
    fi

    if [[ "$cross_product_deps" -gt 0 ]]; then
        local penalty=$((cross_product_deps * 5))
        if [[ "$penalty" -gt 10 ]]; then
            penalty=10
        fi
        score=$((score - penalty))
    fi

    if [[ "$score" -lt 0 ]]; then
        score=0
    fi

    echo "$score"
}

ownership_score() {
    local product="$1"
    if [[ -f "products/${product}/OWNER.md" ]]; then
        echo 25
    else
        echo 0
    fi
}

test_score() {
    local archunit_tests="$1"
    if [[ "$archunit_tests" -gt 0 ]]; then
        echo 25
    else
        echo 0
    fi
}

mkdir -p docs/audits

rows=()
summary_lines=()

while IFS= read -r product_dir; do
    product="$(basename "$product_dir")"

    owner_points="$(ownership_score "$product")"
    module_count="$(count_modules "$product")"
    module_points="$(module_score "$module_count")"
    archunit_tests="$(count_archunit_tests "$product")"
    test_points="$(test_score "$archunit_tests")"
    internal_imports="$(count_platform_internal_imports "$product")"
    cross_product_deps="$(count_cross_product_deps "$product")"
    boundary_points="$(boundary_score "$internal_imports" "$cross_product_deps")"
    total_points=$((owner_points + boundary_points + test_points + module_points))

    rows+=("| ${product} | ${owner_points} | ${boundary_points} | ${test_points} | ${module_points} | ${total_points} | ${module_count} | ${archunit_tests} | ${internal_imports} | ${cross_product_deps} |")

    if [[ "$owner_points" -lt 25 ]]; then
        summary_lines+=("- ${product}: missing OWNER.md")
    fi
    if [[ "$test_points" -lt 25 ]]; then
        summary_lines+=("- ${product}: no ArchUnit coverage detected")
    fi
    if [[ "$internal_imports" -gt 0 ]]; then
        summary_lines+=("- ${product}: ${internal_imports} platform/core internal import(s) detected")
    fi
    if [[ "$cross_product_deps" -gt 0 ]]; then
        summary_lines+=("- ${product}: ${cross_product_deps} direct cross-product build dependency reference(s)")
    fi
done < <(find products -mindepth 1 -maxdepth 1 -type d -not -name build -not -name .gradle | sort)

IFS=$'\n' sorted_rows=($(printf '%s\n' "${rows[@]}" | sort -t'|' -k7,7nr))
unset IFS

{
    echo "# Product Boundary Scorecard — ${QUARTER}"
    echo
    echo "**Generated**: ${GENERATED_AT}"
    echo "**Script**: scripts/compute-boundary-scores.sh"
    echo
    echo "## Scoring Model"
    echo
    echo "- Ownership: 25 points if the product has OWNER.md, otherwise 0"
    echo "- Boundary Clarity: starts at 25, minus 15 for any platform/core internal import usage, minus 5 per direct cross-product dependency up to 10"
    echo "- Test Coverage: 25 points if ArchUnit coverage is detected, otherwise 0"
    echo "- Module Count: 25 for <=5 modules, 20 for <=10, 15 for <=15, 10 for <=20, 5 for <=30, otherwise 0"
    echo
    echo "## Scorecard"
    echo
    echo "| Product | Ownership | Boundary | Test | Module | Total | Modules | ArchUnit Files | Internal Imports | Cross-Product Deps |"
    echo "|:---|---:|---:|---:|---:|---:|---:|---:|---:|---:|"
    printf '%s\n' "${sorted_rows[@]}"
    echo
    echo "## Findings"
    echo
    if [[ ${#summary_lines[@]} -eq 0 ]]; then
        echo "- No immediate product-level boundary issues detected by this heuristic scan"
    else
        printf '%s\n' "${summary_lines[@]}"
    fi
    echo
    echo "## Notes"
    echo
    echo "- This report is heuristic and intentionally conservative"
    echo "- Cross-product dependency count is derived from direct Gradle project references only"
    echo "- Platform/core internal imports are detected from Java/Kotlin import statements"
} > "$REPORT_FILE"

echo "Boundary scorecard written to ${REPORT_FILE}"