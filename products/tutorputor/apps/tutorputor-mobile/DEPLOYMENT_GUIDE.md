# Mobile App Deployment Guide

**Last Updated:** 2026-04-17  
**Version:** 1.0

---

## Overview

This guide covers deploying the TutorPutor React Native mobile app to iOS App Store and Google Play Store.

---

## iOS Deployment

### Prerequisites

- Apple Developer Account ($99/year)
- Mac with Xcode (latest version)
- CocoaPods installed
- React Native CLI

### Steps

1. **Configure App in App Store Connect**
   - Log in to [App Store Connect](https://appstoreconnect.apple.com)
   - Create new app
   - Fill in app information (bundle identifier, SKU, name)
   - Configure app privacy details

2. **Create Provisioning Profiles**
   - Go to Apple Developer Portal
   - Create App ID with bundle identifier
   - Create Development Provisioning Profile
   - Create Distribution Provisioning Profile

3. **Configure Xcode Project**
   ```bash
   cd apps/tutorputor-mobile
   npx react-native init-link
   ```
   - Open `ios/TutorPutor.xcworkspace` in Xcode
   - Set bundle identifier in General settings
   - Set signing team and provisioning profile
   - Configure deployment target (iOS 15+)

4. **Build and Test**
   ```bash
   cd ios
   pod install
   cd ..
   npx react-native run-ios
   ```

5. **Archive and Submit**
   - In Xcode: Product > Archive
   - Select archive and click "Distribute App"
   - Choose distribution method (App Store Connect)
   - Upload to App Store Connect
   - Complete app store listing (screenshots, descriptions, etc.)
   - Submit for review

---

## Android Deployment

### Prerequisites

- Google Play Developer Account ($25 one-time)
- Android Studio
- Java Development Kit (JDK) 17+
- Android SDK

### Steps

1. **Configure App in Google Play Console**
   - Log in to [Google Play Console](https://play.google.com/console)
   - Create new app
   - Fill in app details
   - Configure content rating questionnaire
   - Set up app privacy policy

2. **Generate Signing Key**
   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore tutorputor-release-key.p12 -alias tutorputor-key-alias -keyalg RSA -keysize 2048 -validity 10000
   ```
   - Store the keystore securely (never commit to git)

3. **Configure Gradle**
   - Edit `android/app/build.gradle`
   - Set signing configuration
   - Configure version code and version name

4. **Build Release APK/AAB**
   ```bash
   cd android
   ./gradlew bundleRelease
   ```
   - Output: `android/app/build/outputs/bundle/release/app-release.aab`

5. **Upload to Play Console**
   - Go to Play Console
   - Create new release
   - Upload AAB file
   - Complete store listing (screenshots, descriptions, etc.)
   - Submit for review

---

## App Icons and Splash Screens

### Requirements

- **iOS:** Multiple sizes (1024x1024 for App Store, various for device)
- **Android:** 512x512 for Play Store, adaptive icons for devices

### Tools

- Use [AppIcon](https://appicon.co/) to generate icons
- Use [MakeAppIcon](https://makeappicon.com/) for splash screens

### Steps

1. Generate icons for all required sizes
2. Place iOS icons in `ios/TutorPutor/Assets.xcassets/AppIcon.appiconset/`
3. Place Android icons in `android/app/src/main/res/mipmap-*/`
4. Update splash screen assets in respective directories

---

## Store Metadata

### iOS App Store

- **App Name:** TutorPutor
- **Subtitle:** AI-Powered Learning Platform
- **Description:** Comprehensive learning platform with AI tutoring, interactive simulations, and personalized learning paths.
- **Keywords:** education, learning, AI, tutoring, science, math, physics
- **Screenshots:** 6.5" and 5.5" iPhone, iPad Pro
- **Category:** Education

### Android Play Store

- **App Name:** TutorPutor
- **Short Description:** AI-powered learning platform
- **Full Description:** Comprehensive learning platform with AI tutoring, interactive simulations, and personalized learning paths for science and math education.
- **Screenshots:** Phone and tablet
- **Category:** Education

---

## Build Automation

### CI/CD Pipeline

Configure GitHub Actions or similar for automated builds:

```yaml
name: Build Mobile App

on:
  push:
    branches: [main]

jobs:
  ios:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build iOS
        run: |
          cd apps/tutorputor-mobile
          cd ios
          pod install
          cd ..
          npx react-native build-ios

  android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build Android
        run: |
          cd apps/tutorputor-mobile
          cd android
          ./gradlew bundleRelease
```

---

## Version Management

### Semantic Versioning

- Format: `MAJOR.MINOR.PATCH`
- MAJOR: Breaking changes
- MINOR: New features
- PATCH: Bug fixes

### Release Process

1. Update version in `package.json`
2. Update version in iOS project (Xcode)
3. Update version in Android project (build.gradle)
4. Commit changes with version tag
5. Build and upload to app stores
6. Release notes for each platform

---

## Troubleshooting

### Common Issues

**iOS Build Fails:**
- Ensure Xcode command line tools are installed
- Run `pod install` in ios directory
- Clean build folder in Xcode

**Android Build Fails:**
- Ensure JDK 17 is installed
- Update Android SDK
- Check Gradle wrapper version

**App Store Rejection:**
- Review app store guidelines
- Ensure all required metadata is complete
- Check for prohibited content

---

**Maintained By:** TutorPutor Engineering Team  
**Contact:** See team documentation for ownership
