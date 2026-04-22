#!/bin/bash
#
# Check for deprecated package usage in active code and config surfaces.
# This script is used by CI to prevent removed compatibility packages from
# re-entering the repository through imports, aliases, or manifest entries.
#

set -euo pipefail

DEPRECATED_PACKAGES=(
    "@ghatana/ui"
    "@ghatana/accessibility-audit"
    "@ghatana/yappc-component-traceability"
    "@yappc/component-traceability"
)

EXIT_CODE=0
USE_RG=0
SEARCH_ROOTS=()

if command -v rg >/dev/null 2>&1; then
    USE_RG=1
fi

for candidate in products platform shared-services apps; do
    if [ -d "$candidate" ]; then
        SEARCH_ROOTS+=("$candidate")
    fi
done

find_files() {
    local mode="$1"

    case "$mode" in
        source)
            git ls-files \
                'products/**/*.ts' 'products/**/*.tsx' 'products/**/*.js' 'products/**/*.jsx' \
                'platform/**/*.ts' 'platform/**/*.tsx' 'platform/**/*.js' 'platform/**/*.jsx' \
                'shared-services/**/*.ts' 'shared-services/**/*.tsx' 'shared-services/**/*.js' 'shared-services/**/*.jsx' \
                'apps/**/*.ts' 'apps/**/*.tsx' 'apps/**/*.js' 'apps/**/*.jsx'
            ;;
        manifest)
            git ls-files 'products/**/package.json' 'platform/**/package.json' 'shared-services/**/package.json' 'apps/**/package.json'
            ;;
        tsconfig)
            git ls-files 'products/**/tsconfig*.json' 'platform/**/tsconfig*.json' 'shared-services/**/tsconfig*.json' 'apps/**/tsconfig*.json'
            ;;
        bundler)
            git ls-files \
                'products/**/vite.config.*' 'products/**/tailwind.config.*' \
                'platform/**/vite.config.*' 'platform/**/tailwind.config.*' \
                'shared-services/**/vite.config.*' 'shared-services/**/tailwind.config.*' \
                'apps/**/vite.config.*' 'apps/**/tailwind.config.*'
            ;;
    esac
}

existing_files() {
    while IFS= read -r file; do
        if [ -f "$file" ]; then
            printf '%s\0' "$file"
        fi
    done
}

run_search() {
    local pattern="$1"
    local mode="$2"

    if [ "${#SEARCH_ROOTS[@]}" -eq 0 ] && [ "$mode" != "lockfile" ]; then
        return 1
    fi

    if [ "$USE_RG" -eq 1 ]; then
        case "$mode" in
            source)
                rg "$pattern" \
                    -g '**/*.{ts,tsx,js,jsx,mjs,cjs}' \
                    -g '!**/node_modules/**' -g '!**/build/**' -g '!**/dist/**' -g '!**/coverage/**' \
                    "${SEARCH_ROOTS[@]}" --no-heading --line-number -n
                ;;
            manifest)
                rg "\"${pattern}\"" -g '**/package.json' -g '!**/node_modules/**' \
                    "${SEARCH_ROOTS[@]}" --no-heading --line-number -n
                ;;
            tsconfig)
                rg "$pattern" -g 'tsconfig*.json' -g '!**/node_modules/**' \
                    "${SEARCH_ROOTS[@]}" --no-heading --line-number -n
                ;;
            bundler)
                rg "$pattern" -g 'vite.config.*' -g 'tailwind.config.*' -g '!**/node_modules/**' \
                    "${SEARCH_ROOTS[@]}" --no-heading --line-number -n
                ;;
            lockfile)
                rg "${pattern}@" pnpm-lock.yaml --no-heading --line-number -n
                ;;
        esac
        return
    fi

    case "$mode" in
        source)
            find_files source | existing_files | xargs -0 grep -En "$pattern"
            ;;
        manifest)
            find_files manifest | existing_files | xargs -0 grep -En "$pattern"
            ;;
        tsconfig)
            find_files tsconfig | existing_files | xargs -0 grep -En "$pattern"
            ;;
        bundler)
            find_files bundler | existing_files | xargs -0 grep -En "$pattern"
            ;;
        lockfile)
            grep -En "${pattern}@" pnpm-lock.yaml
            ;;
    esac
}

build_source_pattern() {
    local pkg="$1"
    printf "^[[:space:]]*(import|export)[^;]*['\"]%s(/[^'\"]*)?['\"]|require\\(['\"]%s(/[^'\"]*)?['\"]\\)" "$pkg" "$pkg"
}

build_exact_config_pattern() {
    local pkg="$1"
    printf "['\"]%s(['\"]|/|\\*)" "$pkg"
}

check_pattern() {
    local label="$1"
    local pattern="$2"
    local mode="$3"

    echo ""
    echo "Checking ${label}..."
    if run_search "$pattern" "$mode"; then
        echo ""
        echo "❌ Found deprecated package usage in ${label}"
        EXIT_CODE=1
    else
        echo "✅ No deprecated package usage in ${label}"
    fi
}

for deprecated_package in "${DEPRECATED_PACKAGES[@]}"; do
    echo ""
    echo "🔍 Checking for deprecated ${deprecated_package} usage..."

    check_pattern \
        "TypeScript and JavaScript sources" \
        "$(build_source_pattern "${deprecated_package}")" \
        source

    check_pattern \
        "package manifests" \
        "$(build_exact_config_pattern "${deprecated_package}")" \
        manifest

    check_pattern \
        "TypeScript path aliases" \
        "$(build_exact_config_pattern "${deprecated_package}")" \
        tsconfig

    check_pattern \
        "Vite and Tailwind configs" \
        "$(build_exact_config_pattern "${deprecated_package}")" \
        bundler

    echo ""
    echo "Checking pnpm-lock.yaml for ${deprecated_package} references..."
    if run_search "${deprecated_package}" lockfile | head -20; then
        echo ""
        echo "⚠️  Found ${deprecated_package} references in pnpm-lock.yaml"
        echo "   Refresh the lockfile if this package becomes active again in the graph."
    else
        echo "✅ No ${deprecated_package} references in pnpm-lock.yaml"
    fi
done

if [ "$EXIT_CODE" -eq 0 ]; then
    echo ""
    echo "✅ All checks passed - no deprecated package usage detected"
else
    echo ""
    echo "==============================================================="
    echo "  ❌ DEPRECATED PACKAGE USAGE DETECTED"
    echo "==============================================================="
    echo ""
    echo "  Migration paths:"
    echo "    - @ghatana/ui -> @ghatana/design-system"
    echo "    - @ghatana/accessibility-audit -> @ghatana/accessibility"
    echo "    - @ghatana/yappc-component-traceability -> @yappc/ui/traceability"
    echo "    - @yappc/component-traceability -> @yappc/ui/traceability"
    echo ""
    echo "  See: docs/execution-plans/MONOREPO_BOUNDARY_EXECUTION_PLAN_2026-03-22.md"
    echo "==============================================================="
fi

exit "$EXIT_CODE"
