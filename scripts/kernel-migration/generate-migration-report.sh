#!/bin/bash

# Generate comprehensive migration report for kernel platform

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REPORT_DIR="$PROJECT_ROOT/build/reports/kernel-migration"

mkdir -p "$REPORT_DIR"

REPORT_FILE="$REPORT_DIR/migration-report-$(date +%Y%m%d-%H%M%S).md"

cat > "$REPORT_FILE" << 'EOF'
# Kernel Platform Migration Report

**Generated**: $(date)
**Report Version**: 1.0

---

## Executive Summary

This report provides a comprehensive analysis of the kernel platform migration status across all products.

EOF

# Function to analyze product migration
analyze_product() {
    local product=$1
    
    cat >> "$REPORT_FILE" << EOF

## $product Migration Status

### Dependency Analysis

EOF
    
    # Check kernel dependency
    if grep -q "implementation project(':platform:java:kernel')" "$PROJECT_ROOT/products/$product/build.gradle" 2>/dev/null; then
        echo "✅ Kernel dependency added" >> "$REPORT_FILE"
    else
        echo "❌ Kernel dependency missing" >> "$REPORT_FILE"
    fi
    
    # Check for framework usage
    cat >> "$REPORT_FILE" << EOF

### Framework Integration

EOF
    
    local src_dir="$PROJECT_ROOT/products/$product/src"
    
    if [ -d "$src_dir" ]; then
        # Security framework
        if grep -r "import com.ghatana.kernel.security" "$src_dir" > /dev/null 2>&1; then
            echo "✅ Security Framework integrated" >> "$REPORT_FILE"
        else
            echo "⚠️ Security Framework not integrated" >> "$REPORT_FILE"
        fi
        
        # Observability framework
        if grep -r "import com.ghatana.kernel.observability" "$src_dir" > /dev/null 2>&1; then
            echo "✅ Observability Framework integrated" >> "$REPORT_FILE"
        else
            echo "⚠️ Observability Framework not integrated" >> "$REPORT_FILE"
        fi
        
        # AI framework
        if grep -r "import com.ghatana.kernel.ai" "$src_dir" > /dev/null 2>&1; then
            echo "✅ AI Framework integrated" >> "$REPORT_FILE"
        else
            echo "⚠️ AI Framework not integrated" >> "$REPORT_FILE"
        fi
        
        # Communication framework
        if grep -r "import com.ghatana.kernel.communication" "$src_dir" > /dev/null 2>&1; then
            echo "✅ Communication Framework integrated" >> "$REPORT_FILE"
        else
            echo "⚠️ Communication Framework not integrated" >> "$REPORT_FILE"
        fi
    fi
    
    cat >> "$REPORT_FILE" << EOF

### Code Quality Metrics

EOF
    
    # Count files using kernel
    local kernel_files=$(find "$src_dir" -name "*.java" -exec grep -l "com.ghatana.kernel" {} \; 2>/dev/null | wc -l)
    echo "- Files using kernel: $kernel_files" >> "$REPORT_FILE"
    
    # Count legacy adapter usage
    local legacy_files=$(find "$src_dir" -name "*.java" -exec grep -l "LegacyCapabilityAdapter" {} \; 2>/dev/null | wc -l)
    echo "- Files using legacy adapters: $legacy_files" >> "$REPORT_FILE"
    
    cat >> "$REPORT_FILE" << EOF

### Test Coverage

EOF
    
    local test_dir="$PROJECT_ROOT/products/$product/src/test"
    if [ -d "$test_dir" ]; then
        local test_files=$(find "$test_dir" -name "*Test.java" -exec grep -l "KernelSecurityManager\|KernelTelemetryManager\|AgentOrchestrator" {} \; 2>/dev/null | wc -l)
        echo "- Kernel integration tests: $test_files" >> "$REPORT_FILE"
    else
        echo "- No test directory found" >> "$REPORT_FILE"
    fi
}

# Analyze each product
echo "Analyzing PHR migration..."
analyze_product "phr"

echo "Analyzing Finance migration..."
analyze_product "finance"

echo "Analyzing Aura migration..."
analyze_product "aura"

# Add recommendations
cat >> "$REPORT_FILE" << 'EOF'

---

## Recommendations

### High Priority
1. Complete security framework integration for all products
2. Add comprehensive integration tests for kernel frameworks
3. Remove all legacy adapter usage

### Medium Priority
1. Implement contract validation in CI/CD
2. Add observability framework to all critical paths
3. Configure AI governance for production models

### Low Priority
1. Optimize performance for kernel framework calls
2. Add comprehensive documentation
3. Conduct training sessions for product teams

---

## Next Steps

1. **Week 1**: Address high priority recommendations
2. **Week 2**: Complete medium priority items
3. **Week 3**: Validate all integrations in staging
4. **Week 4**: Production deployment

EOF

echo "Migration report generated: $REPORT_FILE"
cat "$REPORT_FILE"
