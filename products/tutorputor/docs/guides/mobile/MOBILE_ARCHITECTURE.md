# Mobile Architecture Documentation

**Last Updated:** 2026-04-17  
**Version:** 1.0

---

## Overview

TutorPutor mobile app is built with React Native, providing cross-platform support for iOS and Android with offline capabilities, push notifications, and background sync.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Mobile App Layer                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │  UI Components    │  │  Navigation      │                │
│  │  (Screens)        │  │  (React Nav)     │                │
│  └────────┬─────────┘  └────────┬─────────┘                │
│           │                     │                             │
│           └──────────┬──────────┘                             │
│                      ▼                                        │
│           ┌──────────────────┐                               │
│           │  State Management │                               │
│           │  (React Query)    │                               │
│           └────────┬─────────┘                               │
│                    │                                        │
│           ┌────────┴────────┐                               │
│           ▼                 ▼                                │
│  ┌──────────────────┐ ┌──────────────────┐                 │
│  │  Data Layer      │ │  Services        │                 │
│  │  (SQLite + MMKV) │ │  (Sync, Push)    │                 │
│  └──────────────────┘ └──────────────────┘                 │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Technology Stack

### Framework
- **React Native 0.85** - Cross-platform mobile framework
- **TypeScript** - Type safety
- **React 19** - UI library

### Navigation
- **React Navigation 7** - Navigation library
- **Native Stack Navigator** - Native navigation experience

### State Management
- **React Query (TanStack Query)** - Data fetching and caching
- **Async Storage** - Persistent storage
- **MMKV** - High-performance key-value storage

### Offline Support
- **SQLite** - Local database for offline data
- **Background Sync** - Sync service for data synchronization
- **Network Info** - Network status monitoring

### Push Notifications
- **PushNotificationIOS** - iOS push notifications
- **FCM** - Android Firebase Cloud Messaging

---

## Core Features

### 1. Module Learning

**Screens:**
- `HomeScreen` - Dashboard with enrolled modules
- `ModulesScreen` - Browse available modules
- `ModuleDetailScreen` - Module details and lessons
- `LessonScreen` - Lesson content and activities

**Implementation:**
- React Query for module data fetching
- SQLite for offline module caching
- Background sync for progress updates

### 2. Assessments

**Screens:**
- `QuizScreen` - Take assessments offline

**Implementation:**
- Offline quiz taking
- Local result storage
- Sync results when online

### 3. AI Tutor

**Integration:**
- AI tutor chat interface
- Offline message queue
- Sync when online

### 4. User Profile

**Screens:**
- `ProfileScreen` - User profile and settings
- `DownloadsScreen` - Manage offline downloads

**Implementation:**
- Profile data caching
- Download management
- Storage usage tracking

---

## Offline Mode Implementation

### Data Storage

**SQLite Storage:**
- Modules and lessons
- Assessment attempts
- User progress
- AI tutor conversations

**MMKV Storage:**
- Auth tokens
- User preferences
- Sync state
- Notification preferences

### Background Sync

**Sync Service:**
- Queue changes when offline
- Sync when network available
- Conflict resolution
- Retry logic for failed syncs

**Sync Flow:**
```typescript
1. User makes change (e.g., completes lesson)
2. Change queued locally
3. Network status change detected
4. Sync service processes queue
5. Changes sent to server
6. Server response processed
7. Queue updated
```

### Network Monitoring

**Implementation:**
- `@react-native-community/netinfo` for network status
- `useOffline` hook for network state
- Offline banner component for user feedback
- Sync status bar component for sync progress

---

## Push Notification Integration

### Notification Types

**Module Reminders:**
- "You have a module due soon"
- "New module available"

**Assessment Reminders:**
- "Assessment deadline approaching"
- "Assessment results available"

**AI Tutor:**
- "New AI tutor message"
- "Learning path updated"

### Implementation

**Initialization:**
```typescript
// App startup
await pushNotificationService.init();
await pushNotificationService.requestPermissions();
await pushNotificationService.loadPreferences();
```

**Token Registration:**
```typescript
// After login
await pushNotificationService.registerToken(apiBaseUrl, userId, authToken);
```

**Notification Handling:**
```typescript
// Background and foreground
pushNotificationService.handleNotification(payload);
```

---

## Development Workflow

### Setup

```bash
# Install dependencies
cd apps/tutorputor-mobile
npm install

# iOS pods
cd ios
pod install
cd ..

# Start Metro
npm start

# Run on iOS
npm run ios

# Run on Android
npm run android
```

### Testing

```bash
# Run tests
npm test

# Type check
npm run type-check

# Lint
npm run lint
```

---

## Deployment Process

### iOS

1. Configure App Store Connect
2. Create provisioning profiles
3. Configure Xcode project
4. Build and test
5. Archive and submit

See `DEPLOYMENT_GUIDE.md` for detailed steps.

### Android

1. Configure Google Play Console
2. Generate signing key
3. Configure Gradle
4. Build release AAB
5. Upload to Play Console

See `DEPLOYMENT_GUIDE.md` for detailed steps.

---

## Performance Optimization

### Strategies

1. **Image Optimization**
   - Use WebP format where possible
   - Lazy load images
   - Cache images locally

2. **Data Caching**
   - React Query caching
   - SQLite for offline data
   - MMKV for frequently accessed data

3. **Code Splitting**
   - Lazy load screens
   - Split bundles by feature

4. **Bundle Size**
   - Use Hermes JavaScript engine
   - Enable ProGuard for Android
   - Optimize assets

---

## Security Considerations

1. **Authentication**
   - Secure token storage (Keychain on iOS, Keystore on Android)
   - Token refresh mechanism
   - Logout clears all local data

2. **Data Encryption**
   - Encrypt sensitive data at rest
   - Use HTTPS for all API calls
   - Certificate pinning

3. **Code Obfuscation**
   - ProGuard for Android
   - Code obfuscation for iOS builds

---

## Best Practices

1. **Always handle network errors** - Provide fallback UI when offline
2. **Persist user preferences** - Use MMKV for fast access
3. **Optimize battery usage** - Minimize background processing
4. **Test on real devices** - Emulators don't reflect real-world performance
5. **Follow platform guidelines** - iOS Human Interface Guidelines, Android Material Design

---

## Future Enhancements

- Biometric authentication
- Dark mode support
- Widget support
- Siri Shortcuts (iOS)
- App Actions (Android)
- AR features for simulations

---

**Maintained By:** TutorPutor Engineering Team  
**Contact:** See team documentation for ownership
