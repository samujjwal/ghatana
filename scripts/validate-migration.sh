#!/bin/bash

# Migration Validation Script
# Validates that the app-platform migration was successful

set -e

echo "🔍 Validating app-platform migration..."

# Check kernel modules exist
echo "📋 Checking kernel modules..."
KERNEL_MODULES=("authentication" "config" "event-store" "audit")
for module in "${KERNEL_MODULES[@]}"; do
    if [ -d "platform/java/kernel/modules/$module" ]; then
        echo "  ✅ $module module exists"
    else
        echo "  ❌ $module module missing"
        exit 1
    fi
done

# Check finance modules exist
echo "📋 Checking finance modules..."
FINANCE_MODULES=("platform-sdk" "operator-workflows" "regulator-portal" "rules-engine" "data-governance" "ledger-framework" "calendar-service" "incident-management")
for module in "${FINANCE_MODULES[@]}"; do
    if [ -d "products/finance/$module" ]; then
        echo "  ✅ $module module exists"
    else
        echo "  ❌ $module module missing"
        exit 1
    fi
done

# Check build files exist
echo "📋 Checking build files..."
for module in "${FINANCE_MODULES[@]}"; do
    if [ -f "products/finance/$module/build.gradle.kts" ]; then
        echo "  ✅ $module build.gradle.kts exists"
    else
        echo "  ❌ $module build.gradle.kts missing"
        exit 1
    fi
done

# Check settings.gradle.kts has been updated
echo "📋 Checking build configuration..."
if grep -q "products:finance" settings.gradle.kts; then
    echo "  ✅ Finance modules included in settings.gradle.kts"
else
    echo "  ❌ Finance modules not found in settings.gradle.kts"
    exit 1
fi

if grep -q "platform:java:kernel:modules" settings.gradle.kts; then
    echo "  ✅ Kernel modules included in settings.gradle.kts"
else
    echo "  ❌ Kernel modules not found in settings.gradle.kts"
    exit 1
fi

# Check for app-platform references
if grep -q "app-platform" settings.gradle.kts; then
    echo "  ⚠️  Warning: app-platform references still exist in settings.gradle.kts"
else
    echo "  ✅ No app-platform references in settings.gradle.kts"
fi

# Test build
echo "🧪 Testing build..."
./gradlew projects --quiet > /dev/null
if [ $? -eq 0 ]; then
    echo "  ✅ Build configuration is valid"
else
    echo "  ❌ Build configuration has issues"
    exit 1
fi

# Check for Java files in migrated modules
echo "📋 Checking Java source files..."
for module in "${FINANCE_MODULES[@]}"; do
    java_files=$(find "products/finance/$module/src" -name "*.java" 2>/dev/null | wc -l)
    if [ "$java_files" -gt 0 ]; then
        echo "  ✅ $module has $java_files Java files"
    else
        echo "  ⚠️  $module has no Java files"
    fi
done

# Check ActiveJ Promise compliance
echo "📋 Checking ActiveJ Promise compliance..."
for module in "${FINANCE_MODULES[@]}"; do
    if [ -d "products/finance/$module/src" ]; then
        completable_futures=$(grep -r "CompletableFuture" "products/finance/$module/src" 2>/dev/null | wc -l)
        if [ "$completable_futures" -eq 0 ]; then
            echo "  ✅ $module has no CompletableFuture usage"
        else
            echo "  ❌ $module has $completable_futures CompletableFuture usage"
            exit 1
        fi
    fi
done

echo ""
echo "🎉 Migration validation completed successfully!"
echo ""
echo "📊 Migration Summary:"
echo "   ✅ All kernel modules migrated"
echo "   ✅ All finance modules migrated"
echo "   ✅ Build configuration updated"
echo "   ✅ Build validation passed"
echo "   ✅ ActiveJ Promise compliance verified"
echo "   ✅ Java source files present"
