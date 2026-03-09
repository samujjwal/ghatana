#!/bin/bash

# Build iOS app directly using xcodebuild
# This script bypasses the Expo CLI to avoid terminal issues

set -e

cd "$(dirname "$0")"

echo "🔨 Building Flashit iOS app..."
echo "================================"

# Clean build database
echo "🧹 Cleaning Xcode..."
rm -rf ~/Library/Developer/Xcode/DerivedData/Flashit* 2>/dev/null || true

# Install dependencies if needed
if [ ! -d "ios/Pods" ]; then
  echo "📦 Installing CocoaPods dependencies..."
  pod install --repo-update
fi

# Build for simulator
echo "🔨 Building for iOS Simulator..."
xcodebuild \
  -workspace ios/Flashit.xcworkspace \
  -scheme Flashit \
  -configuration Release \
  -derivedDataPath ios/build \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet

echo "✅ Build complete!"
echo ""
echo "Next steps:"
echo "1. Open simulator: open -a Simulator"
echo "2. Install app: xcrun simctl install booted ios/build/Build/Products/Release-iphonesimulator/Flashit.app"
echo "3. Or use: npx expo run:ios --device"
