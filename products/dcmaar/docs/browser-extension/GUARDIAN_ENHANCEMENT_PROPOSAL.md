# Guardian Browser Extension Enhancement Proposal

**Version:** 1.0.0
**Date:** November 24, 2025
**Status:** Draft

## 1. Executive Summary

The Guardian browser extension is a critical component of the DCMAAR ecosystem, providing on-device safety, monitoring, and policy enforcement for child users. While the current implementation establishes a solid foundation with basic blocking and monitoring capabilities, it relies on a monolithic controller pattern that underutilizes the available plugin architecture.

This proposal outlines a comprehensive enhancement plan to transition the extension to a fully modular, plugin-driven architecture aligned with the `@dcmaar/browser-extension-core` framework. Key improvements include a rich content script for granular page metrics, real-time parent-child synchronization, visual analytics, and a dedicated child-friendly user experience.

## 2. Current State Analysis

### 2.1 Architecture

The current architecture follows a "Controller-Service" pattern rather than the intended "Source-Processor-Sink" pipeline pattern.

- **Monolithic Controller**: `GuardianController.ts` (700+ lines) handles tab monitoring, storage, messaging, and analytics directly.
- **Direct API Usage**: Browser APIs (tabs, storage, alarms) are accessed directly in the controller rather than through abstracted sources.
- **Plugin System**: The `ExtensionPluginHost` is initialized, and plugins (`GuardianUsageCollectorPlugin`, `GuardianPolicyEvaluationPlugin`) exist, but the controller often bypasses them to perform logic inline.

### 2.2 Key Components

- **Background Service**: Handles all logic. Heavy reliance on `chrome.tabs` and `chrome.declarativeNetRequest`.
- **Website Blocker**: Robust implementation using Chrome DNR rules, but relies on static category lists.
- **UI**: React-based Dashboard and Settings. Functional but lacks engagement and visual depth.
- **Content Script**: Minimal implementation (10 lines of logging only). No page-level interaction tracking.

### 2.3 Data Flow

- **Current**: `Tabs API` -> `GuardianController` -> `Local Storage`
- **Target**: `EventSource` -> `EventPipeline` -> `Processors` -> `Sinks (Storage/API)`

## 3. Gap Analysis

| ID     | Area               | Gap Description                                                                     | Impact                                                                           | Severity     |
| ------ | ------------------ | ----------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- | ------------ |
| **G1** | **Content Script** | Minimal implementation (`console.debug` only). No DOM access or page-level metrics. | Cannot track time-on-element, specific video titles, or chat interactions.       | **CRITICAL** |
| **G2** | **Plugin System**  | Plugins exist but are underutilized. Controller duplicates plugin logic.            | Code duplication, difficult to test, hard to extend with new features.           | **HIGH**     |
| **G3** | **Architecture**   | Source-Processor-Sink pattern documented but not implemented.                       | Tightly coupled code, difficult to swap implementations or add new data sources. | **HIGH**     |
| **G4** | **Sync**           | No real-time parent-child sync. Relies on manual `SYNC_POLICIES` messages.          | Policy changes are not immediate. Parents cannot see live activity.              | **MEDIUM**   |
| **G5** | **Categorization** | Static `DOMAIN_CATEGORIES` map with ~40 hardcoded domains.                          | Limited coverage. New or obscure sites are not categorized correctly.            | **MEDIUM**   |
| **G6** | **Analytics**      | Text/Table-based stats only. No charts or trend visualization.                      | Hard to spot usage patterns or improvements over time.                           | **MEDIUM**   |
| **G7** | **UX**             | Child user experience is neglected. No dashboard access or feedback.                | Child feels policed rather than supported. Missed educational opportunity.       | **MEDIUM**   |
| **G8** | **Notifications**  | Basic browser notifications only.                                                   | Parents may miss critical alerts if not at the computer.                         | **LOW**      |

## 4. Proposed Architecture Enhancements

### 4.1 Event Pipeline Implementation

Transition from `GuardianController` to `EventPipeline`:

```typescript
// Target Architecture
const pipeline = new EventPipeline()
  .addSource(new TabActivitySource())
  .addSource(new ContentScriptSource())
  .addProcessor(new CategoryEnrichmentProcessor())
  .addProcessor(new PolicyEvaluationProcessor())
  .addSink(new LocalStorageSink())
  .addSink(new CloudSyncSink());
```

### 4.2 Rich Content Script

Implement a comprehensive content script to capture granular data:

- **Page Metadata**: Title, meta tags, OpenGraph data.
- **Interaction Tracking**: Scroll depth, clicks, form interactions.
- **Media Tracking**: Video play/pause events, duration (YouTube, Netflix).
- **DOM Analysis**: Keyword detection for safety scanning (client-side).

### 4.3 Real-Time Synchronization

Implement a **Real-Time Sync Sink** on top of the existing sink configuration (e.g. `sinks.realtime`) and expose it via a `RealTimeSyncPlugin` (`ICommunication`):

- **Transport**: WebSocket-based `RealTimeSyncSink` (or equivalent push channel) configured alongside other sinks (endpoints, auth, retry/backoff, enabled flag).
- **Downstream** (parent → extension): Instant policy and mode updates from the parent dashboard (e.g. enable/disable Focus Mode, adjust category blocks) pushed into the policy evaluation pipeline.
- **Upstream** (extension → parent): Live activity events for "Focus Mode" and high-sensitivity sessions, streamed as a real-time feed rather than batched analytics.
- **Heartbeat / Presence**: Lightweight heartbeat messages to track device online/offline status and "last seen" timestamps.

The `RealTimeSyncPlugin` is responsible for managing the WebSocket lifecycle (connect/reconnect), mapping pipeline events into sink messages, and applying downstream updates into the local policy state.

## 5. UX/UI Enhancements

### 5.1 Child-Centric Dashboard

Create a dedicated view for the child user (read-only):

- **My Stats**: "You've been productive for 2 hours today!"
- **Time Bank**: Visual representation of remaining screen time.
- **Achievements**: Badges for safe browsing and adhering to limits.
- **Request Access**: Streamlined flow to ask for unblocking.

### 5.2 Visual Analytics (Parent View)

Integrate `recharts` or `chart.js` to visualize:

- **Usage Trends**: Line chart of screen time over 7/30 days.
- **Category Distribution**: Donut chart of Time spent by category.
- **Blocked Attempts**: Bar chart of blocked requests by category.
- **Activity Heatmap**: Hour-of-day usage intensity.

### 5.3 Smart Notifications

- **Educational Block Page**: Instead of "Access Denied", show "This site is blocked because...".
- **Time Warnings**: "5 minutes remaining" non-intrusive toast.
- **Weekly Report**: Summary notification for parents.

## 6. Plugin System Evolution

We will strictly adhere to the `IPlugin` interface and Factory pattern.

### 6.1 New Plugins

| Plugin Name                | Purpose                                          | Type             |
| -------------------------- | ------------------------------------------------ | ---------------- |
| `ContentMetricsPlugin`     | Collects page-level metrics from content script. | `IDataCollector` |
| `CategoryEnrichmentPlugin` | Queries external API for domain categorization.  | `IProcessor`     |
| `RealTimeSyncPlugin`       | Manages WebSocket connection to parent backend.  | `ICommunication` |
| `GamificationPlugin`       | Tracks achievements and rewards.                 | `IGamification`  |
| `SmartAlertsPlugin`        | Analyzes patterns to trigger intelligent alerts. | `INotification`  |

### 6.2 Refactored Plugins

- **`GuardianUsageCollectorPlugin`**: Remove logic from Controller, move entirely to plugin `execute()` method.
- **`GuardianPolicyEvaluationPlugin`**: Ensure it acts as the single source of truth for policy decisions, called by the Pipeline.

## 7. Implementation Roadmap

### Phase 1: Foundation & Cleanup (Weeks 1-2)

- [ ] Refactor `GuardianController` to use `EventPipeline` pattern.
- [ ] Move direct API calls to `BrowserAdapters`.
- [ ] Implement basic `ContentScriptSource` (metadata extraction).

### Phase 2: Plugin Expansion (Weeks 3-4)

- [ ] Implement `ContentMetricsPlugin` and `CategoryEnrichmentPlugin`.
- [ ] Migrate `WebsiteBlocker` logic to `PolicyEvaluationProcessor`.
- [ ] Unit tests for all new plugins.

### Phase 3: UI/UX Overhaul (Weeks 5-6)

- [ ] Implement Visual Analytics (Charts) in Dashboard.
- [ ] Create Child Dashboard view.
- [ ] Redesign Block Page with educational context.

### Phase 4: Real-Time & Polish (Weeks 7-8)

- [ ] Implement `RealTimeSyncPlugin` (WebSocket).
- [ ] Add Gamification elements.
- [ ] End-to-end testing and performance optimization.

## 8. Technical Specifications

### 8.1 Content Script Communication

Use `runtime.connect` for long-lived connections for real-time media tracking, and `runtime.sendMessage` for discrete events.

### 8.2 Storage Schema Updates

```typescript
interface DailyUsage {
  date: string;
  totalTime: number;
  categories: Record<string, number>;
  domains: Record<string, DomainUsage>;
  hourlyActivity: number[]; // 0-23 array
}

interface DomainUsage {
  time: number;
  visits: number;
  category: string;
  icon?: string;
}
```

### 8.3 Security Considerations

- **Data Minimization**: Only collect URL/Title if enabled by policy.
- **Local Processing**: Perform keyword scanning locally; do not send page content to cloud.
- **Tamper Resistance**: Ensure background service validates all messages from content script.

---

**Adherence to Guidelines:**

- **Type Safety**: All new code will use strict TypeScript interfaces.
- **Linting**: ESLint rules will be enforced.
- **Architecture**: Follows `core/` and `libs/` reuse principles.
- **Testing**: Unit tests for all plugins using `vitest`.
