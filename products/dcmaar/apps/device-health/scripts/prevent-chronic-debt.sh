#!/bin/bash

# Chronic Debt Prevention Script
# Prevents accumulation of technical debt by enforcing quality standards

set -e

echo "🔍 Running chronic debt prevention checks..."

# Configuration
MAX_ESLINT_DISABLES=5
MAX_TS_EXPECT_ERRORS=3
MAX_TODO_FIXME=10
MAX_CONSOLE_LOGS=0  # No console.log in production code

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    local status=$1
    local message=$2
    if [ "$status" = "PASS" ]; then
        echo -e "${GREEN}✅ $message${NC}"
    elif [ "$status" = "WARN" ]; then
        echo -e "${YELLOW}⚠️  $message${NC}"
    elif [ "$status" = "FAIL" ]; then
        echo -e "${RED}❌ $message${NC}"
    else
        echo "ℹ️  $message"
    fi
}

# Check for eslint-disable comments
check_eslint_disables() {
    echo "Checking eslint-disable usage..."
    
    local disable_count=0
    local files_with_disables=()
    
    while IFS= read -r -d '' file; do
        if [ -f "$file" ]; then
            local file_count=$(grep -c "eslint-disable" "$file" 2>/dev/null || true)
            if [ "$file_count" -gt 0 ]; then
                disable_count=$((disable_count + file_count))
                files_with_disables+=("$file ($file_count)")
            fi
        fi
    done < <(find src/ -name "*.ts" -o -name "*.js" -print0 2>/dev/null || true)
    
    echo "Found $disable_count eslint-disable comments in ${#files_with_disables[@]} files"
    
    if [ "$disable_count" -gt "$MAX_ESLINT_DISABLES" ]; then
        print_status "FAIL" "Too many eslint-disable comments ($disable_count > $MAX_ESLINT_DISABLES)"
        echo "Files with eslint-disable comments:"
        printf '%s\n' "${files_with_disables[@]}"
        echo ""
        echo "Suggested actions:"
        echo "1. Fix the underlying linting issues"
        echo "2. Use more specific disable rules (e.g., eslint-disable-next-line)"
        echo "3. Consider if the rule should be adjusted in eslint.config.js"
        return 1
    else
        print_status "PASS" "eslint-disable usage is within limits ($disable_count <= $MAX_ESLINT_DISABLES)"
        return 0
    fi
}

# Check for @ts-expect-error comments
check_ts_expect_errors() {
    echo "Checking @ts-expect-error usage..."
    
    local error_count=0
    local files_with_errors=()
    
    while IFS= read -r -d '' file; do
        if [ -f "$file" ]; then
            local file_count=$(grep -c "@ts-expect-error" "$file" 2>/dev/null || true)
            if [ "$file_count" -gt 0 ]; then
                error_count=$((error_count + file_count))
                files_with_errors+=("$file ($file_count)")
            fi
        fi
    done < <(find src/ -name "*.ts" -o -name "*.js" -print0 2>/dev/null || true)
    
    echo "Found $error_count @ts-expect-error comments in ${#files_with_errors[@]} files"
    
    if [ "$error_count" -gt "$MAX_TS_EXPECT_ERRORS" ]; then
        print_status "FAIL" "Too many @ts-expect-error comments ($error_count > $MAX_TS_EXPECT_ERRORS)"
        echo "Files with @ts-expect-error comments:"
        printf '%s\n' "${files_with_errors[@]}"
        echo ""
        echo "Suggested actions:"
        echo "1. Create proper type definitions instead of suppressing errors"
        echo "2. Add missing type imports or declarations"
        echo "3. Use type assertions (as) instead of suppressing errors when safe"
        return 1
    else
        print_status "PASS" "@ts-expect-error usage is within limits ($error_count <= $MAX_TS_EXPECT_ERRORS)"
        return 0
    fi
}

# Check for TODO/FIXME comments
check_todo_fixme() {
    echo "Checking TODO/FIXME comments..."
    
    local todo_count=0
    local files_with_todos=()
    
    while IFS= read -r -d '' file; do
        if [ -f "$file" ]; then
            local file_count=$(grep -ci "TODO\|FIXME\|XXX\|HACK" "$file" 2>/dev/null || true)
            if [ "$file_count" -gt 0 ]; then
                todo_count=$((todo_count + file_count))
                files_with_todos+=("$file ($file_count)")
            fi
        fi
    done < <(find src/ -name "*.ts" -o -name "*.js" -print0 2>/dev/null || true)
    
    echo "Found $todo_count TODO/FIXME comments in ${#files_with_todos[@]} files"
    
    if [ "$todo_count" -gt "$MAX_TODO_FIXME" ]; then
        print_status "WARN" "High number of TODO/FIXME comments ($todo_count > $MAX_TODO_FIXME)"
        echo "Consider addressing some of these technical debt items:"
        printf '%s\n' "${files_with_todos[@]}" | head -5
        if [ "${#files_with_todos[@]}" -gt 5 ]; then
            echo "... and $((${#files_with_todos[@]} - 5)) more files"
        fi
        return 0  # Warning, not failure
    else
        print_status "PASS" "TODO/FIXME comments are within acceptable limits ($todo_count <= $MAX_TODO_FIXME)"
        return 0
    fi
}

# Check for console.log in production code
check_console_logs() {
    echo "Checking for console.log statements..."
    
    local console_count=0
    local files_with_console=()
    
    while IFS= read -r -d '' file; do
        if [ -f "$file" ]; then
            # Skip test files
            if [[ "$file" == *"test"* ]] || [[ "$file" == *"spec"* ]]; then
                continue
            fi
            
            local file_count=$(grep -c "console\." "$file" 2>/dev/null || true)
            if [ "$file_count" -gt 0 ]; then
                console_count=$((console_count + file_count))
                files_with_console+=("$file ($file_count)")
            fi
        fi
    done < <(find src/ -name "*.ts" -o -name "*.js" -print0 2>/dev/null || true)
    
    echo "Found $console_count console statements in ${#files_with_console[@]} files"
    
    if [ "$console_count" -gt "$MAX_CONSOLE_LOGS" ]; then
        print_status "FAIL" "Console statements found in production code ($console_count > $MAX_CONSOLE_LOGS)"
        echo "Files with console statements:"
        printf '%s\n' "${files_with_console[@]}"
        echo ""
        echo "Suggested actions:"
        echo "1. Use the logger from RuntimeContext instead of console"
        echo "2. Remove debug console statements"
        echo "3. Use proper logging levels for production"
        return 1
    else
        print_status "PASS" "No console statements in production code"
        return 0
    fi
}

# Check for large files
check_file_sizes() {
    echo "Checking for oversized files..."
    
    local max_file_size=1000  # lines
    local large_files=()
    
    while IFS= read -r -d '' file; do
        if [ -f "$file" ]; then
            local line_count=$(wc -l < "$file")
            if [ "$line_count" -gt "$max_file_size" ]; then
                large_files+=("$file ($line_count lines)")
            fi
        fi
    done < <(find src/ -name "*.ts" -o -name "*.js" -print0 2>/dev/null || true)
    
    if [ "${#large_files[@]}" -gt 0 ]; then
        print_status "WARN" "Large files detected (>${max_file_size} lines)"
        printf '%s\n' "${large_files[@]}"
        echo "Consider breaking these files into smaller, more focused modules"
        return 0  # Warning, not failure
    else
        print_status "PASS" "All files are reasonably sized (<=${max_file_size} lines)"
        return 0
    fi
}

# Check for proper TypeScript usage
check_typescript_usage() {
    echo "Checking TypeScript usage patterns..."
    
    local any_count=0
    local files_with_any=()
    
    while IFS= read -r -d '' file; do
        if [ -f "$file" ] && [[ "$file" == *.ts ]]; then
            # Look for 'any' type usage (excluding comments and strings)
            local file_count=$(grep -v '//' "$file" | grep -v '/\*' | grep -c ': any\|<any>' 2>/dev/null || true)
            if [ "$file_count" -gt 0 ]; then
                any_count=$((any_count + file_count))
                files_with_any+=("$file ($file_count)")
            fi
        fi
    done < <(find src/ -name "*.ts" -print0 2>/dev/null || true)
    
    if [ "$any_count" -gt 5 ]; then
        print_status "WARN" "High usage of 'any' type ($any_count instances)"
        echo "Consider adding proper type definitions:"
        printf '%s\n' "${files_with_any[@]}" | head -3
        return 0  # Warning, not failure
    else
        print_status "PASS" "TypeScript type usage is good ($any_count 'any' types)"
        return 0
    fi
}

# Main execution
main() {
    echo "==========================================="
    echo "🚨 CHRONIC DEBT PREVENTION CHECK"
    echo "==========================================="
    echo ""
    
    local exit_code=0
    
    # Run all checks
    check_eslint_disables || exit_code=1
    echo ""
    
    check_ts_expect_errors || exit_code=1
    echo ""
    
    check_console_logs || exit_code=1
    echo ""
    
    check_todo_fixme
    echo ""
    
    check_file_sizes
    echo ""
    
    check_typescript_usage
    echo ""
    
    # Summary
    echo "==========================================="
    if [ "$exit_code" -eq 0 ]; then
        print_status "PASS" "All chronic debt prevention checks passed!"
        echo "Code quality standards are being maintained. 🎉"
    else
        print_status "FAIL" "Some chronic debt prevention checks failed"
        echo ""
        echo "Please address the issues above before committing."
        echo "These checks help maintain long-term code quality."
        echo ""
        echo "For help with any of these issues, see:"
        echo "- docs/TECHNICAL_GUIDE.md"
        echo "- PROJECT_REFACTOR_REPORT.md"
    fi
    echo "==========================================="
    
    exit $exit_code
}

# Run if called directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi