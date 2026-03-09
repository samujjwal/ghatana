# Guardian Browser Extension & Web App Monitoring Plan

**Version:** 1.0  
**Date:** November 1, 2025  
**Status:** Detailed Implementation Guide

---

## Table of Contents

1. [Overview](#1-overview)
2. [Browser Extension Architecture](#2-browser-extension-architecture)
3. [Web App Monitoring Strategy](#3-web-app-monitoring-strategy)
4. [Component Details](#4-component-details)
5. [Integration with Backend](#5-integration-with-backend)
6. [Implementation Timeline](#6-implementation-timeline)
7. [Security Considerations](#7-security-considerations)

---

## 1. Overview

### 1.1 Problem Statement

Guardian agents on devices (Android, iOS, Windows) can track native app usage, but they **cannot monitor web browsers and web applications**. This gap means:

- ❌ Parents can't see which websites children visit
- ❌ Parents can't block social media, gaming, or streaming websites
- ❌ Web apps (Gmail, Google Classroom, Netflix) aren't tracked
- ❌ YouTube time isn't included in daily limits
- ❌ Incognito/private browsing evades all controls

### 1.2 Solution: Browser Extensions

Deploy **browser extensions** on Chrome, Firefox, Safari, and Edge to:

- ✅ Track website visits and time spent
- ✅ Categorize websites (social, gaming, education, etc.)
- ✅ Block websites in real-time
- ✅ Monitor web app usage
- ✅ Send usage data to Guardian backend
- ✅ Enforce time limits across browsers

### 1.3 Coverage Matrix

| Scenario            | Before Extensions    | After Extensions                         | Impact                           |
| ------------------- | -------------------- | ---------------------------------------- | -------------------------------- |
| YouTube on browser  | Not tracked          | ✅ Tracked + Limited                     | +6 hours/week visibility         |
| TikTok website      | Not tracked          | ✅ Tracked + Blocked                     | +4 hours/week protection         |
| Google Classroom    | Not tracked          | ✅ Tracked                               | +2 hours/week education insights |
| Uninstalling Safari | Still app-based only | ✅ Website blocking via extension        | Improved enforcement             |
| Incognito mode      | Completely hidden    | Partially tracked (extension sees exits) | Better coverage                  |

---

## 2. Browser Extension Architecture

### 2.1 Extension Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      Child's Browser                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Guardian Browser Extension (Manifest V3)                 │   │
│  │                                                           │   │
│  │  ┌─────────────────────────────────────────────────────┐ │   │
│  │  │ Background Service Worker (Persistent)              │ │   │
│  │  │  • Track tab switches                               │ │   │
│  │  │  • Enforce website blocks                           │ │   │
│  │  │  • Sync policies (every 15 min)                     │ │   │
│  │  │  • Batch usage data                                 │ │   │
│  │  └─────────────────────────────────────────────────────┘ │   │
│  │                           ↓                                │   │
│  │  ┌─────────────────────────────────────────────────────┐ │   │
│  │  │ Content Scripts (Per-Page)                          │ │   │
│  │  │  • Detect user interactions                         │ │   │
│  │  │  • Track video/audio playback (YouTube)            │ │   │
│  │  │  • Monitor form submissions                         │ │   │
│  │  └─────────────────────────────────────────────────────┘ │   │
│  │                           ↓                                │   │
│  │  ┌─────────────────────────────────────────────────────┐ │   │
│  │  │ Modules                                             │ │   │
│  │  │  • WebsiteBlocker: Enforce block lists             │ │   │
│  │  │  • UsageTracker: Aggregate visit data              │ │   │
│  │  │  • PolicySync: Download policies from backend      │ │   │
│  │  │  • GrpcClient: Communicate with Guardian backend   │ │   │
│  │  └─────────────────────────────────────────────────────┘ │   │
│  │                           ↓                                │   │
│  │  ┌─────────────────────────────────────────────────────┐ │   │
│  │  │ Local Storage (IndexedDB/localStorage)              │ │   │
│  │  │  • Blocked websites list                            │ │   │
│  │  │  • Website categories cache                         │ │   │
│  │  │  • Pending usage uploads (offline queue)            │ │   │
│  │  └─────────────────────────────────────────────────────┘ │   │
│  │                                                           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                           ↓ (gRPC)                               │
└─────────────────────────────────────────────────────────────────┘
                            ↓
        ┌──────────────────────────────────────────┐
        │   Guardian Backend (Java/ActiveJ)        │
        │  ├─ Policy Service                       │
        │  ├─ Ingest Service (website usage)       │
        │  ├─ Action Service (enforcement)         │
        │  └─ Report Service (analytics)           │
        └──────────────────────────────────────────┘
```

### 2.2 Data Flow

```
User visits TikTok.com
        ↓
Extension detects tab change to tiktok.com
        ↓
Check if blocked: Is TikTok in block list?
        ↓
No → Start tracking session
    • Record: hostname=tiktok.com, title="TikTok", startTime=now
    • Timer running: Each minute accumulates time
        ↓
Batch after 50 visits OR 5 minutes
        ↓
Send to Guardian Backend via gRPC
  {
    visits: [
      {hostname: "tiktok.com", duration: 45min},
      {hostname: "youtube.com", duration: 30min}
    ]
  }
        ↓
Backend stores in ClickHouse
  table: guardian.website_visits
  (device_id, hostname, duration, timestamp)
        ↓
Parent dashboard shows:
  "Social Media: 1h 15min (TikTok 45min + Instagram 30min)"
```

---

## 3. Web App Monitoring Strategy

### 3.1 What Gets Tracked

| Category             | Examples                                               | How Tracked                   | Data Sent                             |
| -------------------- | ------------------------------------------------------ | ----------------------------- | ------------------------------------- |
| **Social Media**     | Facebook, Twitter, Instagram, Reddit, TikTok, Snapchat | URL hostname                  | hostname, duration, category          |
| **Gaming**           | Steam, Epic Games, Roblox, Miniclip                    | URL hostname                  | hostname, duration, category          |
| **Streaming**        | YouTube, Netflix, Hulu, Disney+, Twitch                | URL hostname + video playback | hostname, duration, video_watched     |
| **Communication**    | Discord, Slack, Teams                                  | URL hostname + activity       | hostname, duration, messages_sent     |
| **Education**        | Khan Academy, Coursera, Udemy, Google Classroom        | URL hostname                  | hostname, duration, quiz_attempts     |
| **Productivity**     | Gmail, Google Docs, Office 365, Notion                 | URL hostname + time in focus  | hostname, duration, documents_created |
| **General Websites** | News, shopping, etc.                                   | URL hostname                  | hostname, duration, category          |

### 3.2 Categorization Strategy

**Approach 1: Hardcoded Keywords (Fast, Works Offline)**

```typescript
const keywords = {
  social: ["facebook", "twitter", "instagram", "tiktok", "reddit"],
  gaming: ["steam", "epicgames", "roblox"],
  education: ["khan", "coursera", "classroom.google.com"],
};

function categorize(hostname: string): string {
  for (const [cat, sites] of Object.entries(keywords)) {
    for (const site of sites) {
      if (hostname.includes(site)) return cat;
    }
  }
  return "general";
}
```

**Approach 2: External Categorization API (Accurate, Requires Network)**

```typescript
// Use third-party DNS categorization (CleanBrowsing, OpenDNS, etc.)
async function categorize(hostname: string): Promise<string> {
  const response = await fetch(
    `https://dns.cleanbrowsing.org/api/lookup?url=${hostname}`
  );
  const result = await response.json();
  return result.category; // 'social', 'gaming', etc.
}
```

**Recommendation:** Use Approach 1 (keywords) for MVP, cache results locally, sync with backend categories every 24 hours.

---

## 4. Component Details

### 4.1 Chrome Extension (Manifest V3)

**Architecture:**

- Background Service Worker (always running, low CPU)
- Content Scripts (injected into pages)
- Popup UI (extension icon settings)
- Blocked page (replacement when site blocked)

**Key Files:**

```
chrome-extension/
├── manifest.json              # Extension config
├── src/
│   ├── background.ts          # Service worker
│   ├── content.ts             # Content script
│   ├── popup.tsx              # Extension popup
│   └── blocked.html           # Blocked page
├── modules/
│   ├── websiteBlocker.ts      # Block enforcement
│   ├── usageTracker.ts        # Usage aggregation
│   ├── policySync.ts          # Policy download
│   ├── grpcClient.ts          # Backend communication
│   └── categorizer.ts         # Website categorization
├── storage/
│   ├── blockedSites.ts        # IndexedDB operations
│   └── pendingUploads.ts      # Offline queue
└── styles/
    ├── popup.css
    └── blocked.css
```

**Implementation Steps:**

1. Week 7 - Set up Manifest V3, background worker
2. Week 7 - Implement UsageTracker module
3. Week 7 - Build WebsiteBlocker module
4. Week 8 - Create blocked.html page
5. Week 8 - Implement PolicySync
6. Week 8 - Add IndexedDB storage
7. Week 9 - Add popup UI
8. Week 9 - Test and polish

**Key Technical Details:**

- Storage limit: 10MB (plenty for cached policies + pending uploads)
- Update check: Every 5 hours automatic
- Permission model: activeTab, storage, webRequest, scripting

---

### 4.2 Firefox Extension (WebExtensions API)

**Architecture:** Similar to Chrome but uses WebExtensions API

- Slightly different permission model
- No service workers (uses background scripts)
- Different storage APIs

**Reuse Strategy:**

- ~70% code identical to Chrome (TypeScript logic)
- ~30% API adaptations (WebExtensions vs Chrome Web APIs)

**Implementation Steps:**

1. Week 9 - Port Chrome code to Firefox
2. Week 9 - Adapt permission model
3. Week 9 - Test cross-browser compatibility
4. Week 9 - Create distribution package

---

### 4.3 Safari Extension

**Architecture:** Native Swift, integrates with Safari ecosystem

- Uses SafariExtensionViewController
- Integrates with Content Blocker
- Can use native keychain for secure storage

**Implementation Steps:**

1. Week 9 - Create Xcode project
2. Week 9 - Implement Swift modules
3. Week 9 - Content blocker support
4. Week 9 - Test with family controls API

---

### 4.4 Edge Extension

**Implementation Steps:**

1. Week 9 - Use Chrome codebase (90% compatible)
2. Week 9 - Minimal Edge-specific adaptations
3. Week 9 - Test and submit to Edge Add-ons store

---

## 5. Integration with Backend

### 5.1 New Backend Endpoints

**Endpoint 1: Fetch Website Policies**

```
gRPC Service: GuardianService
Method: FetchWebsitePolicies
Request:
  device_id: UUID
  auth_token: JWT

Response: stream WebsitePolicy
  message WebsitePolicy {
    string id = 1;
    string target = 2;           // hostname or domain
    PolicyAction action = 3;      // BLOCK, ALLOW, LIMIT_TIME
    string category = 4;          // social, gaming, education
    int32 limit_minutes = 5;      // if LIMIT_TIME
    google.protobuf.Timestamp updated_at = 6;
  }
```

**Endpoint 2: Ingest Website Usage**

```
gRPC Service: GuardianService
Method: IngestWebsiteUsage
Request: stream WebsiteUsageBatch
  message WebsiteUsageBatch {
    string device_id = 1;
    repeated WebsiteVisit visits = 2;
    google.protobuf.Timestamp timestamp = 3;
  }

  message WebsiteVisit {
    string hostname = 1;
    string title = 2;
    int32 duration_seconds = 3;
    string category = 4;
  }

Response: IngestResponse
  message IngestResponse {
    int32 events_processed = 1;
    bool success = 2;
  }
```

### 5.2 Database Schema Updates

**New ClickHouse Table:**

```sql
CREATE TABLE guardian.website_visits (
    event_time DateTime,
    device_id String,
    hostname String,
    page_title String,
    duration_seconds UInt16,
    category String,
    session_id String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time)
ORDER BY (device_id, event_time);
```

**New PostgreSQL Table:**

```sql
CREATE TABLE website_policies (
    id UUID PRIMARY KEY,
    device_id UUID REFERENCES devices(id),
    target VARCHAR(255),         -- hostname or domain
    action VARCHAR(50),          -- BLOCK, ALLOW, LIMIT_TIME
    category VARCHAR(50),        -- social, gaming, education
    limit_minutes INTEGER,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 5.3 Backend Service Updates

**PolicyService Extension:**

```java
@Component
public class PolicyService {

    // Get website policies for device
    public Promise<List<WebsitePolicy>> getWebsitePolicies(UUID deviceId) {
        return Promise.ofBlocking(executor, () -> {
            // Query from PostgreSQL website_policies table
            List<WebsitePolicy> policies = repository.findWebsitePolicies(deviceId);

            // Cache for 15 minutes
            cacheManager.put("website_policies:" + deviceId, policies, Duration.ofMinutes(15));

            return policies;
        });
    }

    // Check if hostname should be blocked
    public Promise<Boolean> shouldBlockWebsite(UUID deviceId, String hostname) {
        return getWebsitePolicies(deviceId)
            .then(policies -> {
                return policies.stream()
                    .anyMatch(p ->
                        p.getTarget().equals(hostname) &&
                        p.getAction() == PolicyAction.BLOCK
                    );
            });
    }
}
```

**ReportService Extension:**

```java
@Component
public class ReportService {

    // Generate website usage report
    public Promise<WebsiteReport> generateWebsiteReport(UUID childId, LocalDate date) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT hostname, SUM(duration_seconds) as total_seconds, COUNT(*) as sessions
                FROM guardian.website_visits
                WHERE device_id = ? AND date(event_time) = ?
                GROUP BY hostname, category
                ORDER BY total_seconds DESC
                """;

            List<WebsiteUsage> usage = clickHouseClient.query(sql,
                Arrays.asList(childId.toString(), date.toString()),
                WebsiteUsage.class
            );

            return new WebsiteReport()
                .setDate(date)
                .setWebsites(usage);
        });
    }
}
```

---

## 6. Implementation Timeline

### Phase 3A: Browser Extensions (Weeks 7-9)

**Week 7: Chrome Extension Foundation**

- [ ] Set up project structure with TypeScript
- [ ] Implement Manifest V3 configuration
- [ ] Build background service worker
- [ ] Create UsageTracker module
- [ ] Implement local storage (IndexedDB)

**Deliverables:**

- Chrome extension can track website visits
- Data persists in IndexedDB
- Background worker running

**Week 8: Website Blocking & Policy Sync**

- [ ] Implement WebsiteBlocker module
- [ ] Create blocked.html page with UI
- [ ] Build PolicySync module
- [ ] Implement gRPC client wrapper
- [ ] Add policy enforcement logic

**Deliverables:**

- Website blocking working
- Policies synced from backend
- Blocked page displays correctly

**Week 9: Firefox, Safari, Edge + Polish**

- [ ] Port to Firefox WebExtensions API
- [ ] Create Safari extension (Xcode)
- [ ] Port to Edge (minimal changes)
- [ ] Cross-browser testing
- [ ] Add offline queue retry logic
- [ ] Create store submission packages

**Deliverables:**

- Extensions for all 4 browsers
- All working in test environment
- Ready for app store submission

---

## 7. Security Considerations

### 7.1 Risks & Mitigations

| Risk                                            | Severity | Mitigation                                                             |
| ----------------------------------------------- | -------- | ---------------------------------------------------------------------- |
| Child disables extension                        | High     | Use device admin API to prevent uninstall, require password to disable |
| Extension code injection via compromised update | Critical | Sign extensions with developer certificate, verify signatures          |
| Sensitive data in localStorage                  | High     | Encrypt cached policies with AES-256                                   |
| Man-in-the-middle on gRPC                       | Critical | Use mTLS certificates, certificate pinning                             |
| Child tampering with blocked.html               | Medium   | Make HTML non-editable, use secure hash validation                     |

### 7.2 Security Best Practices

```typescript
// Encryption for stored data
class SecureStorage {
  private cipher = crypto.createCipheriv("aes-256-gcm", key, iv);

  async store(key: string, value: any) {
    const encrypted = this.cipher.update(JSON.stringify(value));
    await chrome.storage.local.set({
      [key]: encrypted.toString("hex"),
    });
  }

  async retrieve(key: string) {
    const result = await chrome.storage.local.get(key);
    const decipher = crypto.createDecipheriv("aes-256-gcm", key, iv);
    const decrypted = decipher.update(Buffer.from(result[key], "hex"));
    return JSON.parse(decrypted.toString());
  }
}

// Certificate pinning for gRPC
const grpcClient = new GuardianGrpcClient({
  certificatePath: chrome.runtime.getURL("certs/guardian-cert.pem"),
  verifyServerCertificate: true,
});
```

### 7.3 Privacy Considerations

- Websites are categorized locally when possible (no external leaks)
- Usage data sent in batches every 5 minutes (not real-time)
- Sensitive domains (banking, medical) not logged
- User can view/delete tracked data

---

## 8. Testing Strategy

### 8.1 Unit Tests

```typescript
// WebsiteBlocker tests
describe("WebsiteBlocker", () => {
  it("should block exact hostname matches", async () => {
    blocker.blockWebsite("tiktok.com", "Time limit");
    expect(await blocker.isBlocked("tiktok.com")).toBe(true);
  });

  it("should respect allowlist over block list", async () => {
    blocker.blockWebsite("*.youtube.com", "Category block");
    blocker.allowWebsite("youtube-learning.com");
    expect(await blocker.isBlocked("youtube-learning.com")).toBe(false);
  });
});

// UsageTracker tests
describe("UsageTracker", () => {
  it("should accumulate time for same site", () => {
    tracker.trackVisit("youtube.com", "YouTube");
    tracker.trackVisit("youtube.com", "YouTube");
    expect(tracker.getTotalTime("youtube.com")).toBeGreaterThan(0);
  });
});
```

### 8.2 Integration Tests

```typescript
// Extension + Backend integration
describe("Extension Integration", () => {
  it("should fetch policies from backend", async () => {
    const policies = await extension.syncPolicies();
    expect(policies.length).toBeGreaterThan(0);
  });

  it("should upload usage batch to backend", async () => {
    await extension.recordVisit("facebook.com", 300);
    const response = await extension.flushBatch();
    expect(response.eventsProcessed).toEqual(1);
  });
});
```

### 8.3 E2E Tests

```gherkin
Scenario: Child visits blocked website
  Given a policy blocks "tiktok.com"
  When child navigates to "tiktok.com"
  Then blocked page appears
  And usage is NOT recorded

Scenario: Parent views website usage report
  Given child visited "youtube.com" for 30 minutes
  And child visited "instagram.com" for 20 minutes
  When parent views daily report
  Then social media total shows 50 minutes
  And websites listed are: YouTube (30m), Instagram (20m)
```

---

## 9. Performance Considerations

### 9.1 Extension Performance

| Metric        | Target | Strategy                                    |
| ------------- | ------ | ------------------------------------------- |
| Startup time  | <500ms | Lazy load modules, async initialization     |
| Memory usage  | <50MB  | Batch processing, efficient data structures |
| CPU usage     | <5%    | Use native browser APIs, avoid polling      |
| Storage usage | <10MB  | Compress cached data, purge old records     |

### 9.2 Optimization Techniques

```typescript
// Batch processing to reduce overhead
class UsageTracker {
  private batchSize = 50; // Process 50 visits at a time
  private flushInterval = 5 * 60 * 1000; // 5 minutes

  // Efficient hostname comparison
  private hostnameCache = new Map<string, string>();

  getCachedCategory(hostname: string): string | undefined {
    return this.hostnameCache.get(hostname);
  }
}

// Lazy loading modules
async function initializeExtension() {
  // Load only critical modules first
  const blocker = await import("./websiteBlocker");
  const tracker = await import("./usageTracker");
  // Load optional modules later
  setTimeout(() => import("./analytics"), 5000);
}
```

---

## Summary: Current Status

✅ **Browser extension plan:** Complete with architecture, components, timelines  
✅ **Integration with backend:** New endpoints, schema, gRPC definitions  
✅ **Security strategy:** Encryption, certificate pinning, privacy controls  
✅ **Testing approach:** Unit, integration, E2E tests defined

**Total Effort:** 8,000 LOC (4 extensions × 2,000 LOC each)  
**Timeline:** 3 weeks (Phase 3A of Guardian implementation)  
**Team:** 2 engineers (1 Chrome/Firefox, 1 Safari/Edge)

---

**End of Browser Extension Monitoring Plan**
