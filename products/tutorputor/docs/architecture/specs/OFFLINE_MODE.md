# TutorPutor Offline Mode Specification

> Version: 1.0.0  
> Status: Draft  
> Last Updated: 2025-01-14

## 1. Overview

TutorPutor's offline mode enables students to continue learning even without internet connectivity. This specification defines the architecture, data models, and behaviors for offline-first functionality.

## 2. Architecture

### 2.1 Technology Stack

| Component        | Technology                     | Purpose                       |
| ---------------- | ------------------------------ | ----------------------------- |
| Storage          | IndexedDB via `@ghatana/state` | Client-side persistence       |
| Sync Engine      | `@ghatana/state/sync`          | Mutation queue & sync         |
| State Management | `@ghatana/state/offline`       | Reactive offline state        |
| Service Worker   | Workbox                        | Asset caching & offline shell |

### 2.2 Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        TutorPutor Web App                       │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐ │
│  │  UI Layer   │───▶│  Hooks      │───▶│  Offline Store      │ │
│  │  (React)    │    │  useOffline │    │  (createOfflineStore)│ │
│  └─────────────┘    └─────────────┘    └──────────┬──────────┘ │
│                                                    │            │
│  ┌─────────────┐    ┌─────────────┐    ┌──────────▼──────────┐ │
│  │  Sync       │◀───│  Sync       │◀───│  IndexedDB          │ │
│  │  Engine     │    │  Handlers   │    │  Adapter            │ │
│  └──────┬──────┘    └─────────────┘    └─────────────────────┘ │
│         │                                                       │
└─────────┼───────────────────────────────────────────────────────┘
          │
          ▼
    ┌─────────────┐
    │  TutorPutor │
    │  API        │
    └─────────────┘
```

## 3. IndexedDB Schema

### 3.1 Database Structure

```typescript
// Database: tutorputor-offline
// Version: 1

interface OfflineDatabase {
  stores: {
    // Cached learning content
    cache: {
      keyPath: "key";
      indexes: ["expiresAt", "cachedAt", "category"];
    };

    // Pending mutations queue
    mutations: {
      keyPath: "id";
      indexes: ["createdAt", "type", "retryCount"];
    };

    // User progress (high priority cache)
    progress: {
      keyPath: "moduleId";
      indexes: ["updatedAt", "syncStatus"];
    };

    // Downloaded modules for offline access
    modules: {
      keyPath: "id";
      indexes: ["downloadedAt", "category", "grade"];
    };
  };
}
```

### 3.2 Store Definitions

#### Cache Store

```typescript
interface CacheEntry {
  key: string; // Unique cache key
  data: unknown; // Cached data
  cachedAt: string; // ISO timestamp
  expiresAt: string; // ISO timestamp
  version: number; // Incremented on update
  category: string; // 'module' | 'quiz' | 'content' | 'media'
}
```

#### Mutations Store

```typescript
interface PendingMutation {
  id: string; // Unique mutation ID
  type: string; // Mutation type identifier
  payload: unknown; // Mutation payload
  createdAt: string; // ISO timestamp
  retryCount: number; // Current retry attempt
  maxRetries: number; // Maximum retry attempts
  lastError?: string; // Last error message
  idempotencyKey: string; // Prevent duplicate processing
}
```

#### Progress Store

```typescript
interface OfflineProgress {
  moduleId: string; // Module identifier
  progress: number; // 0-100 percentage
  currentLesson: number; // Current lesson index
  completedLessons: string[]; // Completed lesson IDs
  quizScores: Record<string, number>; // Quiz ID -> score
  timeSpentMs: number; // Total time spent
  updatedAt: string; // ISO timestamp
  syncStatus: "synced" | "pending" | "error";
}
```

#### Modules Store

```typescript
interface OfflineModule {
  id: string; // Module ID
  title: string; // Module title
  category: string; // Subject category
  grade: number; // Grade level
  lessons: OfflineLesson[];
  quizzes: OfflineQuiz[];
  downloadedAt: string; // ISO timestamp
  totalSizeBytes: number;
  version: string; // Content version
}
```

## 4. Mutation Types

### 4.1 Supported Mutations

| Type              | Payload                                 | Priority | Description            |
| ----------------- | --------------------------------------- | -------- | ---------------------- |
| `COMPLETE_LESSON` | `{ moduleId, lessonId, timeSpentMs }`   | High     | Mark lesson complete   |
| `SUBMIT_QUIZ`     | `{ moduleId, quizId, answers, score }`  | High     | Submit quiz answers    |
| `UPDATE_PROGRESS` | `{ moduleId, progress, currentLesson }` | Medium   | Update module progress |
| `ADD_BOOKMARK`    | `{ moduleId, lessonId, position }`      | Low      | Bookmark a position    |
| `ADD_NOTE`        | `{ moduleId, lessonId, content }`       | Low      | Add a note             |
| `SYNC_ANALYTICS`  | `{ events: AnalyticsEvent[] }`          | Low      | Batch analytics sync   |

### 4.2 Sync Handlers

```typescript
// handlers/progressSyncHandler.ts
export const progressSyncHandlers: Record<string, SyncHandler> = {
  COMPLETE_LESSON: createApiSyncHandler(async (payload) => {
    await apiClient.post("/api/progress/complete-lesson", payload);
  }),

  SUBMIT_QUIZ: createApiSyncHandler(async (payload) => {
    await apiClient.post("/api/quizzes/submit", payload);
  }),

  UPDATE_PROGRESS: createApiSyncHandler(async (payload) => {
    await apiClient.patch("/api/progress", payload);
  }),
};
```

## 5. Caching Strategy

### 5.1 Cache Tiers

| Tier      | TTL      | Content Types              | Eviction Priority |
| --------- | -------- | -------------------------- | ----------------- |
| Critical  | 30 days  | User progress, credentials | Never auto-evict  |
| Primary   | 7 days   | Downloaded modules         | Low               |
| Secondary | 24 hours | Recently accessed content  | Medium            |
| Transient | 1 hour   | Search results, listings   | High              |

### 5.2 Cache Keys

```typescript
// Cache key patterns
const CACHE_KEYS = {
  module: (id: string) => `module:${id}`,
  lesson: (moduleId: string, lessonId: string) =>
    `lesson:${moduleId}:${lessonId}`,
  quiz: (moduleId: string, quizId: string) => `quiz:${moduleId}:${quizId}`,
  progress: (moduleId: string) => `progress:${moduleId}`,
  userProfile: () => "user:profile",
  curriculum: (grade: number, subject: string) =>
    `curriculum:${grade}:${subject}`,
};
```

### 5.3 Preloading Strategy

1. **On Module Download**: Cache all lessons, quizzes, and media
2. **On App Start**: Refresh critical user data if online
3. **Background Sync**: Prefetch next 3 lessons in current module

## 6. Sync Behavior

### 6.1 Sync Triggers

- App comes online (automatic)
- User manually triggers sync
- Periodic background sync (every 5 minutes when online)
- Before app close (attempt final sync)

### 6.2 Sync Order

1. Critical mutations (lessons, quizzes) - FIFO
2. Progress updates - deduplicated, latest wins
3. Analytics events - batched, low priority

### 6.3 Conflict Resolution

| Conflict Type     | Resolution Strategy                                     |
| ----------------- | ------------------------------------------------------- |
| Progress conflict | Server wins for completion, merge for partial           |
| Quiz submission   | Client wins (server rejects duplicates via idempotency) |
| Notes/Bookmarks   | Timestamp-based, most recent wins                       |

### 6.4 Retry Policy

```typescript
const RETRY_CONFIG = {
  maxRetries: 3,
  baseDelayMs: 1000,
  maxDelayMs: 60000,
  backoffMultiplier: 2,
  // Retry delays: 1s, 2s, 4s (then give up)
};
```

## 7. UI/UX Requirements

### 7.1 Offline Indicators

| State      | UI Element                            | Location      |
| ---------- | ------------------------------------- | ------------- |
| Offline    | Yellow banner with cloud-offline icon | Top of screen |
| Syncing    | Spinning sync icon with count         | Header        |
| Sync Error | Red badge with error count            | Header        |
| Downloaded | Green checkmark                       | Module card   |

### 7.2 Offline-Available Actions

- Continue downloaded modules
- Complete lessons (queued for sync)
- Take quizzes (queued for sync)
- View cached progress
- Add notes/bookmarks

### 7.3 Offline-Unavailable Actions

- Download new modules
- View leaderboards (show cached if available)
- Access non-downloaded content
- View real-time analytics

## 8. Storage Quotas

### 8.1 Limits

| Resource          | Limit      | Action on Exceed      |
| ----------------- | ---------- | --------------------- |
| Total Storage     | 500 MB     | Prompt to clear cache |
| Single Module     | 50 MB      | Warn before download  |
| Pending Mutations | 100 items  | Block new mutations   |
| Cache Entries     | 1000 items | LRU eviction          |

### 8.2 Cleanup Strategy

1. Evict expired cache entries
2. Remove modules not accessed in 30 days
3. Compress analytics batches
4. Prompt user to manually clear if needed

## 9. Testing Requirements

### 9.1 Unit Tests

- [ ] IndexedDBAdapter CRUD operations
- [ ] OfflineStore cache management
- [ ] SyncEngine retry logic
- [ ] Mutation queue ordering
- [ ] Cache eviction policies

### 9.2 Integration Tests

- [ ] Offline → Online transition
- [ ] Mutation sync with server
- [ ] Conflict resolution scenarios
- [ ] Storage quota handling

### 9.3 E2E Tests

- [ ] Complete lesson offline
- [ ] Submit quiz offline
- [ ] Sync on reconnect
- [ ] Download module for offline

## 10. Metrics & Monitoring

### 10.1 Key Metrics

| Metric                     | Type    | Description                   |
| -------------------------- | ------- | ----------------------------- |
| `offline.mutations.queued` | Counter | Mutations added to queue      |
| `offline.mutations.synced` | Counter | Successfully synced mutations |
| `offline.mutations.failed` | Counter | Failed sync attempts          |
| `offline.cache.hits`       | Counter | Cache hit count               |
| `offline.cache.misses`     | Counter | Cache miss count              |
| `offline.storage.bytes`    | Gauge   | Current storage usage         |

## 11. Security Considerations

1. **Encryption**: Sensitive data (quiz answers) should be encrypted at rest
2. **Token Storage**: Auth tokens in secure storage, not IndexedDB
3. **Data Sanitization**: Validate all cached data before use
4. **Quota Abuse**: Rate limit cache writes to prevent DoS

## 12. Migration Path

### 12.1 From No Offline Support

1. Detect existing local storage data
2. Migrate to IndexedDB structure
3. Clear legacy storage
4. Show migration complete notification

### 12.2 Schema Upgrades

- Use IndexedDB versioning
- Implement upgrade handlers for each version bump
- Preserve user data during migrations

---

## Appendix A: API Contracts

See `/products/tutorputor/contracts/v1/types.ts` for full type definitions.

## Appendix B: Related Documentation

- [Block 4 Implementation Plan](./tutorputor-day-by-day-impl-plan-block-4.md)
- [Design Architecture](./DESIGN_ARCHITECTURE.md)
- [@ghatana/state Documentation](../../../libs/typescript/state/README.md)
