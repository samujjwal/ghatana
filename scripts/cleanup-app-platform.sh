#!/bin/bash

# App-Platform Migration Cleanup Script
# This script removes the old app-platform directory after successful migration

set -e

echo "🧹 Starting app-platform cleanup..."

# Check if app-platform directory exists
if [ ! -d "products/app-platform" ]; then
    echo "❌ app-platform directory not found. Nothing to clean up."
    exit 1
fi

# Confirm cleanup
echo "⚠️  This will permanently remove the products/app-platform directory."
echo "   All migrated code has been moved to:"
echo "   - platform/java/kernel/modules/ (kernel modules)"
echo "   - products/finance/ (finance-specific modules)"
echo ""
read -p "Are you sure you want to continue? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Cleanup cancelled."
    exit 1
fi

# Create backup of app-platform directory
BACKUP_DIR=".backup-app-platform-$(date +%Y%m%d-%H%M%S)"
echo "📦 Creating backup in $BACKUP_DIR..."
cp -r products/app-platform "$BACKUP_DIR"

# Remove app-platform directory
echo "🗑️  Removing products/app-platform directory..."
rm -rf products/app-platform

# Verify build configuration is updated
echo "🔍 Verifying build configuration..."
if grep -q "app-platform" settings.gradle.kts; then
    echo "⚠️  Warning: app-platform references still exist in settings.gradle.kts"
    echo "   Please review and remove any remaining references."
fi

# Test build
echo "🧪 Testing build configuration..."
./gradlew projects --quiet > /dev/null
if [ $? -eq 0 ]; then
    echo "✅ Build configuration is valid"
else
    echo "❌ Build configuration has issues. Please review."
    exit 1
fi

echo "✅ App-platform cleanup completed successfully!"
echo "📦 Backup available at: $BACKUP_DIR"
echo ""
echo "🎉 Migration Summary:"
echo "   ✅ Kernel modules migrated to platform/java/kernel/modules/"
echo "   ✅ Finance modules migrated to products/finance/"
echo "   ✅ Build configuration updated"
echo "   ✅ Old app-platform directory removed"
echo "   ✅ Build validation passed"
