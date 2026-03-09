#!/bin/bash

# Build Flashit iOS app using Expo EAS Cloud Build
# This bypasses local build issues by using Expo's managed build service

echo "🚀 Flashit iOS Build via EAS (Expo's Managed Build Service)"
echo "============================================================"
echo ""
echo "This script builds the app in the cloud, handling all dependencies automatically."
echo ""

cd "$(dirname "$0")"

# Check if eas-cli is installed
if ! command -v eas &> /dev/null; then
    echo "📦 Installing EAS CLI..."
    npm install -g eas-cli
fi

# Check if user is logged in
echo ""
echo "🔐 Checking Expo login status..."
eas whoami || {
    echo "📝 You need to log in to use EAS Build. Please run: eas login"
    exit 1
}

echo ""
echo "🏗️ Building iOS app in the cloud..."
echo ""

# Build for iOS simulator (faster, for testing)
# Use --simulator flag for quick testing on simulator
# Remove --simulator for production/TestFlight builds

eas build \
    --platform ios \
    --simulator \
    --clear

echo ""
echo "✅ Build process started!"
echo "📱 Check your build status: https://expo.dev/accounts/[your-username]/builds"
echo ""
echo "Once the build completes:"
echo "1. Download the .app file"
echo "2. Open it with Xcode or use: xcrun simctl install booted <path-to-app>"
echo ""
