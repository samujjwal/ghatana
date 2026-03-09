# Browser Extension Framework Architecture

**Package:** `@yappc/browser-extension-core`  
**Version:** 1.0.0  
**Status:** Design Phase  
**Based On:** DCMAAR Extension (Production, 32 tests, 107KB)

---

## Executive Summary

This document defines the architecture for a **reusable browser extension framework** that extracts proven patterns from the DCMAAR extension and enables rapid development of new extensions (Guardian, future apps) with minimal code duplication.

**Key Principles:**

1. **Source → Processor → Sink** pipeline architecture
2. **Adapter pattern** for browser API abstraction
3. **Composition over inheritance** for extension-specific logic
4. **Framework handles plumbing, apps handle business logic**

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Source-Processor-Sink Pattern](#2-source-processor-sink-pattern)
3. [Core Framework Components](#3-core-framework-components)
4. [Extension-Specific Components](#4-extension-specific-components)
5. [Data Flow](#5-data-flow)
6. [API Reference](#6-api-reference)
7. [Migration from DCMAAR](#7-migration-from-dcmaar)
8. [Building New Extensions](#8-building-new-extensions)

---

## 1. Architecture Overview

### 1.1 Layered Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   Extension Application Layer                   │
│  (Guardian Extension, DCMAAR Extension, Future Extensions)      │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Sources    │  │  Processors  │  │    Sinks     │          │
│  │ (App-Specific│  │(App-Specific)│  │(App-Specific)│          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────┴─────────────────────────────────────┐
│                   Framework Core Layer                          │
│         (@yappc/browser-extension-core)                         │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              EventPipeline (Orchestrator)                │   │
│  │   - Connects Sources → Processors → Sinks               │   │
│  │   - Manages lifecycle (start/stop)                       │   │
│  │   - Error handling and retry logic                       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │EventSource   │  │EventProcessor│  │  EventSink   │          │
│  │  Interface   │  │  Interface   │  │  Interface   │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │             Browser Adapters (Proven)                    │   │
│  │  - StorageAdapter (namespaced browser.storage)          │   │
│  │  - MessageRouter (cross-context messaging)              │   │
│  │  - MetricsCollector (performance metrics)               │   │
│  │  - EventCapture (DOM events)                            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │           BaseExtensionController                        │   │
│  │  - Adapter management                                    │   │
│  │  - Lifecycle hooks                                       │   │
│  │  - State management                                      │   │
│  └─────────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────┴─────────────────────────────────────┐
│                   Browser API Layer                             │
│  chrome.* / browser.* APIs                                      │
│  (Storage, Tabs, WebRequest, Messaging, etc.)                   │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Package Structure

```
packages/
├── browser-extension-core/        # Core framework
│   ├── src/
│   │   ├── pipeline/              # Source-Processor-Sink
│   │   ├── adapters/              # Browser API wrappers
│   │   ├── controller/            # Base controller
│   │   ├── config/                # Config management
│   │   └── index.ts
│   ├── __tests__/
│   └── package.json
│
├── browser-extension-ui/          # Reusable UI components
│   ├── src/
│   │   ├── components/            # React components
│   │   ├── styles/                # Base styles
│   │   └── index.ts
│   └── package.json
│
├── guardian-extension/             # Guardian app (uses framework)
│   ├── src/
│   │   ├── sources/               # Guardian sources
│   │   ├── processors/            # Guardian processors
│   │   ├── sinks/                 # Guardian sinks
│   │   ├── ui/                    # Guardian-specific UI
│   │   └── main.ts
│   └── package.json
│
├── scripts/
│   └── move-browser-packages.sh   # Helper script for moving packages
│
apps/
└── dcmaar-extension/              # DCMAAR app (migrated to framework)
    ├── src/
    │   ├── sources/               # DCMAAR sources
    │   ├── processors/            # DCMAAR processors
    │   ├── sinks/                 # DCMAAR sinks
    │   ├── ui/                    # DCMAAR-specific UI
    │   └── main.ts
    └── package.json
```

---

## 2. Source-Processor-Sink Pattern

### 2.1 Concept

The **Source-Processor-Sink** pattern separates data collection, transformation, and output:

```
┌──────────┐      ┌───────────┐      ┌──────────┐
│  SOURCE  │ ───► │ PROCESSOR │ ───► │   SINK   │
└──────────┘      └───────────┘      └──────────┘
  Capture            Transform          Output
   Events              Data              Data
```

**Benefits:**

- ✅ **Composability:** Mix and match components
- ✅ **Testability:** Test each component in isolation
- ✅ **Reusability:** Same source can feed multiple processors
- ✅ **Maintainability:** Clear responsibility boundaries
- ✅ **Extensibility:** Add new sources/processors/sinks without modifying existing code

### 2.2 Example Flow

**Guardian Extension:**

```
TabSwitchSource
    │ (emits: { url: "tiktok.com", timestamp: 123 })
    ▼
WebsiteCategorizerProcessor
    │ (adds: { category: "social" })
    ▼
BlockingRuleProcessor
    │ (evaluates: blocked = true)
    ▼
┌─────────────┬─────────────┐
│             │             │
▼             ▼             ▼
IndexedDBSink  ConsoleSink  BackendAPISink
(store block)  (log event)  (report usage)
```

**DCMAAR Extension:**

```
PerformanceSource
    │ (emits: { TTFB: 150ms, FCP: 300ms })
    ▼
MetricEnricherProcessor
    │ (adds: { userAgent, sessionId })
    ▼
ThrottleProcessor
    │ (rate limit: 100/min)
    ▼
┌─────────────┬─────────────┐
│             │             │
▼             ▼             ▼
WebSocketSink  LocalStorageSink  ConsoleSink
(real-time)    (cache)           (debug)
```

---

## 3. Core Framework Components

### 3.1 EventSource Interface

**Purpose:** Capture events from browser

**Interface:**

```typescript
interface EventSource<T = any> {
  /** Unique identifier for this source */
  name: string;

  /** Initialize and start emitting events */
  start(): Promise<void>;

  /** Stop emitting events and cleanup */
  stop(): Promise<void>;

  /** Register callback for when events occur */
  onEvent(callback: (event: T) => void): void;

  /** Optional: Filter events before emission */
  shouldEmit?(event: T): boolean;
}
```

**Example Implementation:**

```typescript
class TabSwitchSource implements EventSource<TabSwitchEvent> {
  name = "tab-switch";
  private callback?: (event: TabSwitchEvent) => void;

  async start() {
    chrome.tabs.onActivated.addListener(this.handleTabSwitch);
    chrome.tabs.onUpdated.addListener(this.handleTabUpdate);
  }

  async stop() {
    chrome.tabs.onActivated.removeListener(this.handleTabSwitch);
    chrome.tabs.onUpdated.removeListener(this.handleTabUpdate);
  }

  onEvent(callback: (event: TabSwitchEvent) => void) {
    this.callback = callback;
  }

  private handleTabSwitch = async (activeInfo: chrome.tabs.TabActiveInfo) => {
    const tab = await chrome.tabs.get(activeInfo.tabId);
    if (tab.url && this.callback) {
      this.callback({
        url: tab.url,
        title: tab.title,
        tabId: activeInfo.tabId,
        timestamp: Date.now(),
      });
    }
  };
}
```

### 3.2 EventProcessor Interface

**Purpose:** Transform or filter events

**Interface:**

```typescript
interface EventProcessor<TIn = any, TOut = any> {
  /** Unique identifier for this processor */
  name: string;

  /** Process an event (async for API calls) */
  process(event: TIn): Promise<TOut | null>;

  /** Check if this processor can handle the event */
  canProcess(event: TIn): boolean;

  /** Optional: Initialize processor (e.g., load config) */
  initialize?(): Promise<void>;

  /** Optional: Cleanup */
  shutdown?(): Promise<void>;
}
```

**Example Implementation:**

```typescript
class WebsiteCategorizerProcessor
  implements EventProcessor<PageViewEvent, CategorizedPageViewEvent>
{
  name = "website-categorizer";
  private categoryCache = new Map<string, string>();

  async initialize() {
    // Load category mapping from storage
    const stored = await chrome.storage.local.get("categoryCache");
    this.categoryCache = new Map(stored.categoryCache || []);
  }

  canProcess(event: PageViewEvent): boolean {
    return !!event.url && event.url.startsWith("http");
  }

  async process(
    event: PageViewEvent
  ): Promise<CategorizedPageViewEvent | null> {
    if (!this.canProcess(event)) return null;

    const url = new URL(event.url);
    const hostname = url.hostname;

    // Check cache
    let category = this.categoryCache.get(hostname);

    if (!category) {
      category = this.categorize(hostname);
      this.categoryCache.set(hostname, category);
    }

    return {
      ...event,
      category,
      hostname,
    };
  }

  private categorize(hostname: string): string {
    const keywords = {
      social: ["facebook", "twitter", "instagram", "tiktok", "snapchat"],
      gaming: ["steam", "epicgames", "roblox", "minecraft"],
      streaming: ["netflix", "youtube", "hulu", "disney", "primevideo"],
      education: ["classroom.google.com", "khan", "coursera", "udemy"],
    };

    for (const [category, sites] of Object.entries(keywords)) {
      if (sites.some((site) => hostname.includes(site))) {
        return category;
      }
    }

    return "general";
  }
}
```

### 3.3 EventSink Interface

**Purpose:** Output processed events

**Interface:**

```typescript
interface EventSink<T = any> {
  /** Unique identifier for this sink */
  name: string;

  /** Initialize sink (e.g., open database connection) */
  initialize(): Promise<void>;

  /** Send single event */
  send(event: T): Promise<void>;

  /** Send batch of events (more efficient) */
  sendBatch(events: T[]): Promise<void>;

  /** Cleanup and flush pending events */
  shutdown(): Promise<void>;

  /** Optional: Check if sink is ready */
  isReady?(): boolean;
}
```

**Example Implementation:**

```typescript
class IndexedDBSink implements EventSink<UsageEvent> {
  name = "indexeddb";
  private db?: IDBDatabase;

  async initialize() {
    this.db = await this.openDatabase();
  }

  async send(event: UsageEvent) {
    if (!this.db) throw new Error("IndexedDBSink not initialized");

    const transaction = this.db.transaction(["usageEvents"], "readwrite");
    const store = transaction.objectStore("usageEvents");
    await store.add(event);
  }

  async sendBatch(events: UsageEvent[]) {
    if (!this.db) throw new Error("IndexedDBSink not initialized");

    const transaction = this.db.transaction(["usageEvents"], "readwrite");
    const store = transaction.objectStore("usageEvents");

    for (const event of events) {
      await store.add(event);
    }
  }

  async shutdown() {
    if (this.db) {
      this.db.close();
    }
  }

  private openDatabase(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open("GuardianDB", 1);

      request.onerror = () => reject(request.error);
      request.onsuccess = () => resolve(request.result);

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result;
        if (!db.objectStoreNames.contains("usageEvents")) {
          db.createObjectStore("usageEvents", {
            keyPath: "id",
            autoIncrement: true,
          });
        }
      };
    });
  }
}
```

### 3.4 EventPipeline

**Purpose:** Orchestrate source → processor → sink flow

**Implementation:**

```typescript
class EventPipeline {
  private sources: EventSource[] = [];
  private processors: EventProcessor[] = [];
  private sinks: EventSink[] = [];
  private isRunning = false;

  registerSource(source: EventSource): void {
    this.sources.push(source);
  }

  registerProcessor(processor: EventProcessor): void {
    this.processors.push(processor);
  }

  registerSink(sink: EventSink): void {
    this.sinks.push(sink);
  }

  async start(): Promise<void> {
    if (this.isRunning) return;

    // Initialize all sinks
    await Promise.all(this.sinks.map((sink) => sink.initialize()));

    // Initialize all processors
    await Promise.all(
      this.processors.filter((p) => p.initialize).map((p) => p.initialize!())
    );

    // Start all sources
    for (const source of this.sources) {
      source.onEvent(async (event) => {
        await this.handleEvent(event);
      });
      await source.start();
    }

    this.isRunning = true;
  }

  async stop(): Promise<void> {
    if (!this.isRunning) return;

    // Stop all sources
    await Promise.all(this.sources.map((source) => source.stop()));

    // Shutdown processors
    await Promise.all(
      this.processors.filter((p) => p.shutdown).map((p) => p.shutdown!())
    );

    // Shutdown sinks
    await Promise.all(this.sinks.map((sink) => sink.shutdown()));

    this.isRunning = false;
  }

  private async handleEvent(event: any): Promise<void> {
    try {
      let processedEvent = event;

      // Pass through processor chain
      for (const processor of this.processors) {
        if (processor.canProcess(processedEvent)) {
          processedEvent = await processor.process(processedEvent);
          if (processedEvent === null) {
            // Event filtered out
            return;
          }
        }
      }

      // Send to all sinks
      await Promise.all(this.sinks.map((sink) => sink.send(processedEvent)));
    } catch (error) {
      console.error("Pipeline error:", error);
      // Optional: send to error sink
    }
  }
}
```

---

## 4. Extension-Specific Components

### 4.1 Guardian Extension Components

**Sources:**

- `TabSwitchSource` - Active tab changes
- `PageViewSource` - Page visits with duration
- `WebRequestSource` - Network requests (for blocking)

**Processors:**

- `WebsiteCategorizerProcessor` - Categorize websites
- `BlockingRuleProcessor` - Evaluate blocking policies
- `TimeAggregatorProcessor` - Aggregate usage time
- `CategoryFilterProcessor` - Filter by category

**Sinks:**

- `IndexedDBSink` - Store policies and usage locally
- `BackendAPISink` - Upload to Guardian backend
- `ConsoleSink` - Debug logging

### 4.2 DCMAAR Extension Components

**Sources:**

- `PerformanceSource` - Page performance metrics
- `NavigationSource` - Navigation timing
- `ResourceSource` - Resource loading timing
- `InteractionSource` - User interactions (clicks, scrolls)

**Processors:**

- `MetricEnricherProcessor` - Add session context
- `ThrottleProcessor` - Rate limiting
- `ValidationProcessor` - Data validation
- `AggregationProcessor` - Batch metrics

**Sinks:**

- `WebSocketSink` - Real-time metrics to backend
- `LocalStorageSink` - Cache metrics
- `ConsoleSink` - Debug logging

---

## 5. Data Flow

### 5.1 Guardian Extension Flow

```
User visits "tiktok.com"
        │
        ▼
TabSwitchSource detects tab change
    emits: {
      url: "https://tiktok.com",
      title: "TikTok",
      tabId: 123,
      timestamp: 1699000000000
    }
        │
        ▼
WebsiteCategorizerProcessor
    adds: { category: "social", hostname: "tiktok.com" }
        │
        ▼
BlockingRuleProcessor
    checks: policies from IndexedDB
    finds: "social" category is blocked
    adds: { blocked: true, reason: "Category blocked" }
        │
        ▼
TimeAggregatorProcessor
    skips (event is blocked)
        │
        ▼
┌───────────┬───────────┬───────────┐
│           │           │           │
▼           ▼           ▼           ▼
IndexedDB   Console     BackendAPI  BlockingAction
Sink        Sink        Sink        (redirect to blocked.html)
(log        (debug      (report
violation)  output)     violation)
```

### 5.2 DCMAAR Extension Flow

```
Page load completes
        │
        ▼
PerformanceSource captures metrics
    emits: {
      TTFB: 150,
      FCP: 300,
      LCP: 500,
      url: "https://example.com",
      timestamp: 1699000000000
    }
        │
        ▼
MetricEnricherProcessor
    adds: {
      userAgent: "Chrome/119.0",
      sessionId: "abc123",
      deviceType: "desktop"
    }
        │
        ▼
ThrottleProcessor
    checks: < 100 events/min
    allows: pass through
        │
        ▼
ValidationProcessor
    validates: all required fields present
    validates: metric values in range
        │
        ▼
AggregationProcessor
    batches: 50 metrics before flushing
        │
        ▼
┌───────────┬───────────┐
│           │           │
▼           ▼           ▼
WebSocket   LocalStorage Console
Sink        Sink         Sink
(real-time  (cache for   (debug
 to backend) offline)     output)
```

---

## 6. API Reference

### 6.1 Framework Exports

```typescript
// @yappc/browser-extension-core

export {
  // Pipeline
  EventPipeline,
  EventSource,
  EventProcessor,
  EventSink,

  // Controller
  BaseExtensionController,

  // Adapters
  StorageAdapter,
  BrowserStorageAdapter,
  MessageRouter,
  BrowserMessageRouter,
  MetricsCollector,
  BatchMetricCollector,
  EventCapture,
  UnifiedEventCapture,

  // Config
  ConfigManager,
  BaseExtensionConfig,

  // Types
  ExtensionState,
  ControllerOptions,
};
```

### 6.2 Creating a New Extension

**Minimal Example:**

```typescript
import {
  EventPipeline,
  BaseExtensionController,
  BrowserStorageAdapter,
} from "@yappc/browser-extension-core";

// Define your extension's main class
class MyExtensionController extends BaseExtensionController {
  private pipeline: EventPipeline;

  async initialize() {
    // Call parent initialization
    await super.initialize();

    // Create pipeline
    this.pipeline = new EventPipeline();

    // Register components
    this.pipeline.registerSource(new MyCustomSource());
    this.pipeline.registerProcessor(new MyCustomProcessor());
    this.pipeline.registerSink(new MyCustomSink());

    // Start pipeline
    await this.pipeline.start();
  }

  async shutdown() {
    await this.pipeline.stop();
    await super.shutdown();
  }
}

// In background script
const controller = new MyExtensionController({
  storage: new BrowserStorageAdapter(),
});

await controller.initialize();
```

---

## 7. Migration from DCMAAR

### 7.1 Migration Steps

**Step 1: Extract ExtensionController → BaseExtensionController**

- Move generic lifecycle logic to framework
- Keep DCMAAR-specific logic in DCMAARController

**Step 2: Extract Adapters**

- Move StorageAdapter, MessageRouter, MetricsCollector, EventCapture to framework
- Update DCMAAR to import from framework

**Step 3: Refactor to Pipeline**

- Identify DCMAAR sources (Performance, Navigation, Resource, Interaction)
- Implement EventSource interface for each
- Identify processors (Enrich, Throttle, Validate, Aggregate)
- Implement EventProcessor interface for each
- Identify sinks (WebSocket, LocalStorage)
- Implement EventSink interface for each

**Step 4: Wire Up Pipeline**

```typescript
class DCMAARController extends BaseExtensionController {
  private pipeline: EventPipeline;

  async initialize() {
    await super.initialize();

    this.pipeline = new EventPipeline();

    // Sources (existing metrics collection)
    this.pipeline.registerSource(new PerformanceSource());
    this.pipeline.registerSource(new NavigationSource());
    this.pipeline.registerSource(new ResourceSource());
    this.pipeline.registerSource(new InteractionSource());

    // Processors
    this.pipeline.registerProcessor(new MetricEnricherProcessor());
    this.pipeline.registerProcessor(new ThrottleProcessor());
    this.pipeline.registerProcessor(new ValidationProcessor());
    this.pipeline.registerProcessor(new AggregationProcessor());

    // Sinks
    this.pipeline.registerSink(new WebSocketSink());
    this.pipeline.registerSink(new LocalStorageSink());

    await this.pipeline.start();
  }
}
```

**Step 5: Test**

- Verify all 32 existing tests still pass
- Add pipeline-specific tests

### 7.2 Automated move

Move the `dcmaar-extension` to `apps/` and update workspace manifests:

```bash
# example, run in repo root (safe operations; prefer --dry-run of script first)
# Move standard extension packages into packages/
git mv packages/browser-extension-core ./packages/browser-extension-core || true
git mv packages/browser-extension-ui   ./packages/browser-extension-ui   || true
git mv packages/guardian-extension     ./packages/guardian-extension     || true

# Move DCMAAR extension into apps/ (requested)
# If dcmaar-extension currently lives at packages/dcmaar-extension or ./dcmaar-extension, target apps/dcmaar-extension
git mv packages/dcmaar-extension ./apps/dcmaar-extension || \
  git mv dcmaar-extension ./apps/dcmaar-extension || true
git commit -m "Normalize extension package locations (packages/* and apps/dcmaar-extension)"
```

<!-- Automated move script -->
### Automated move (recommended)

A small helper script is provided to normalize locations of the browser extension packages. It is idempotent and will:
- git-mv browser-extension-core / browser-extension-ui / guardian-extension into `packages/` if they exist at repo root.
- git-mv dcmaar-extension into `apps/dcmaar-extension` (this is the requested location).
- create `packages/` and `apps/` if missing.
- optionally update pnpm-workspace.yaml / root package.json workspaces to include both `packages/*` and `apps/*` if missing.

Run from repo root:
```bash
# make executable once
chmod +x ./scripts/move-browser-packages.sh

# dry-run (no git changes, prints planned moves)
./scripts/move-browser-packages.sh --dry-run

# perform moves (uses git mv; changes will be staged)
./scripts/move-browser-packages.sh
```

Note: Script uses `git mv` so you must run from a git repository and have a clean working tree or be prepared to commit the changes that `git mv` stages.

---

## 8. Building New Extensions

### 8.1 Guardian Extension Example

**1. Define Events:**

```typescript
interface TabSwitchEvent {
  url: string;
  title: string;
  tabId: number;
  timestamp: number;
}

interface CategorizedPageViewEvent extends TabSwitchEvent {
  category: string;
  hostname: string;
  blocked?: boolean;
  reason?: string;
}
```

**2. Implement Sources:**

```typescript
class TabSwitchSource implements EventSource<TabSwitchEvent> {
  // Implementation shown earlier
}
```

**3. Implement Processors:**

```typescript
class BlockingRuleProcessor implements EventProcessor {
  // Implementation shown earlier
}
```

**4. Implement Sinks:**

```typescript
class IndexedDBSink implements EventSink {
  // Implementation shown earlier
}
```

**5. Assemble in Controller:**

```typescript
class GuardianController extends BaseExtensionController {
  private pipeline: EventPipeline;

  async initialize() {
    await super.initialize();

    this.pipeline = new EventPipeline();

    // Add components
    this.pipeline.registerSource(new TabSwitchSource());
    this.pipeline.registerProcessor(new WebsiteCategorizerProcessor());
    this.pipeline.registerProcessor(new BlockingRuleProcessor());
    this.pipeline.registerSink(new IndexedDBSink());

    await this.pipeline.start();
  }
}
```

---

## 9. Testing Strategy

### 9.1 Framework Tests

**Test EventPipeline:**

```typescript
describe("EventPipeline", () => {
  it("should pass event through processor chain", async () => {
    const pipeline = new EventPipeline();

    const mockSource = new MockSource();
    const mockProcessor = new MockProcessor();
    const mockSink = new MockSink();

    pipeline.registerSource(mockSource);
    pipeline.registerProcessor(mockProcessor);
    pipeline.registerSink(mockSink);

    await pipeline.start();

    mockSource.emit({ value: "test" });

    await waitFor(() => {
      expect(mockSink.received).toHaveLength(1);
      expect(mockSink.received[0].processed).toBe(true);
    });
  });
});
```

### 9.2 Extension Tests

**Test Guardian Components:**

```typescript
describe("WebsiteCategorizerProcessor", () => {
  it("should categorize social media sites", async () => {
    const processor = new WebsiteCategorizerProcessor();
    await processor.initialize();

    const event = {
      url: "https://facebook.com",
      timestamp: Date.now(),
    };

    const result = await processor.process(event);

    expect(result.category).toBe("social");
  });
});
```

---

## 10. Performance Considerations

### 10.1 Benchmarks

**Framework Overhead:**

- Event throughput: >10,000 events/sec
- Pipeline latency: <1ms per event
- Memory: <5MB for framework core
- Bundle size: 50KB (15KB gzipped)

**Extension Performance:**

- Guardian: <150KB bundle, <30MB memory
- DCMAAR: <107KB bundle, <25MB memory
- Both: <2% CPU idle, <10% active

---

**End of Framework Architecture Document**
