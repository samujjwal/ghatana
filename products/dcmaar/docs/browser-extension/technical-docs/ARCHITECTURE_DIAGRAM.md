# Guardian Browser Extension - Metrics Collection Architecture

## Problem: What Was Broken ❌

```
┌─────────────────────────────────────────────┐
│  Background Service Worker                  │
│                                             │
│  ┌─────────────────────────────────────┐  │
│  │ GuardianController                  │  │
│  │                                     │  │
│  │  this.metrics = new              │  │
│  │  BatchPageMetricsCollector()       │  │
│  │                                     │  │
│  │  ❌ window is undefined            │  │
│  │  ❌ document is undefined          │  │
│  │  ❌ isInPageContext() = false      │  │
│  │  ❌ Collection FAILS               │  │
│  └─────────────────────────────────────┘  │
│                                             │
└─────────────────────────────────────────────┘

Result: ZERO metrics collected, popup shows 0 samples
```

---

## Solution: What We Built ✅

```
┌──────────────────────────────────────────────────────────────────┐
│                        Web Pages                                 │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Content Script (index.ts)                                 │ │
│  │                                                             │ │
│  │  ✅ window object exists                                   │ │
│  │  ✅ document object exists                                 │ │
│  │                                                             │ │
│  │  const collector = new BatchPageMetricsCollector()         │ │
│  │  collector.startAutoCollect(30000, async (metrics) => {   │ │
│  │    await browser.runtime.sendMessage({                    │ │
│  │      type: 'PAGE_METRICS_COLLECTED',                      │ │
│  │      payload: {                                            │ │
│  │        page: {                                             │ │
│  │          url: '...',                                       │ │
│  │          metrics: { lcp, cls, fid, inp, tbt, tti },       │ │
│  │          ratings: { lcp, cls, fid, inp },                 │ │
│  │          timestamp                                         │ │
│  │        }                                                   │ │
│  │      }                                                     │ │
│  │    })                                                      │ │
│  │  })                                                        │ │
│  │                                                             │ │
│  │  ✅ Collects: LCP, CLS, FID, INP, TBT, TTI               │ │
│  │  ✅ Every 30 seconds                                       │ │
│  │  ✅ All Web Vitals measured                                │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
                              ↓
                    Message (every 30 seconds)
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│              Background Service Worker                           │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  GuardianController                                        │ │
│  │                                                             │ │
│  │  router.onMessageType('PAGE_METRICS_COLLECTED', async (...) => {│
│  │    const pageMetrics = message.payload.page              │ │
│  │    const existing = await storage.get('guardian-metrics')│ │
│  │    await storage.set('guardian-metrics', [               │ │
│  │      ...existing,                                        │ │
│  │      pageMetrics  // ✅ Store received metrics           │ │
│  │    ])                                                    │ │
│  │    updateState({ totalMetricsCollected: +1 })           │ │
│  │  })                                                      │ │
│  │                                                             │ │
│  │  ✅ Receives metrics from content script                  │ │
│  │  ✅ Stores in persistent storage                          │ │
│  │  ✅ Updates collection counter                            │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
                              ↓
                    Browser Storage
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│  Browser Storage (IndexedDB/LocalStorage)                        │
│                                                                   │
│  'guardian-metrics': [                                           │
│    {                                                              │
│      "url": "https://example.com",                              │
│      "timestamp": 1700000000000,                                │
│      "metrics": {                                               │
│        "lcp": 2400,      ✅ Largest Contentful Paint            │
│        "cls": 0.05,      ✅ Cumulative Layout Shift             │
│        "fid": 85,        ✅ First Input Delay                   │
│        "inp": 150,       ✅ Interaction to Next Paint           │
│        "tbt": 200,       ✅ Total Blocking Time                 │
│        "tti": 3200       ✅ Time to Interactive                 │
│      },                                                          │
│      "ratings": {        ✅ Performance ratings                 │
│        "lcp": "good",                                           │
│        "cls": "good",                                           │
│        "fid": "good",                                           │
│        "inp": "good"                                            │
│      }                                                           │
│    },                                                            │
│    { ... },              ✅ More collections                    │
│    { ... }               ✅ 7-day retention                     │
│  ]                                                               │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
                              ↓
                    User opens popup
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│  Popup UI (Popup.tsx)                                            │
│                                                                   │
│  useEffect(() => {                                              │
│    router.sendToBackground({ type: 'GET_ANALYTICS' })          │
│      ↓ (requests analytics summary)                             │
│    GuardianController.getAnalyticsSummary()                     │
│      ↓ (reads from 'guardian-metrics' storage)                  │
│    Returns: {                                                    │
│      totalMetrics: 42,  // ✅ Shows sample count                │
│      webVitals: {                                               │
│        lcp: { average: 2400, rating: 'good' },  ✅ Shows LCP   │
│        cls: { average: 0.05, rating: 'good' },  ✅ Shows CLS   │
│        fid: { average: 85, rating: 'good' },    ✅ Shows FID   │
│        inp: { average: 150, rating: 'good' }    ✅ Shows INP   │
│      },                                                          │
│      state: {                                                    │
│        metricsCollecting: true  ✅ Shows "Monitoring Active"   │
│      }                                                           │
│    }                                                             │
│    setAnalytics(response.data)                                  │
│  }, [])                                                          │
│                                                                   │
│  return (                                                        │
│    <div>                                                         │
│      <Status>                                                    │
│        {analytics.state.metricsCollecting                       │
│          ? '✅ Monitoring Active'                               │
│          : '⭕ Monitoring Inactive'}                            │
│        {analytics.totalMetrics} samples                         │
│      </Status>                                                   │
│      <WebVitals>                                                │
│        LCP: {analytics.webVitals.lcp.average}ms [good]         │
│        CLS: {analytics.webVitals.cls.average} [good]           │
│        FID: {analytics.webVitals.fid.average}ms [good]         │
│        INP: {analytics.webVitals.inp.average}ms [good]         │
│      </WebVitals>                                               │
│    </div>                                                        │
│  )                                                               │
│                                                                   │
│  ✅ Displays all metrics to user                                │
│  ✅ Shows collection status                                     │
│  ✅ Shows sample count > 0                                      │
│  ✅ Shows Web Vitals values and ratings                         │
└──────────────────────────────────────────────────────────────────┘
```

---

## Data Flow Sequence

```
Time: 0 seconds
┌─────────────────┐
│ User navigates  │
│ to website      │
└────────┬────────┘
         ↓
┌─────────────────────────────────────┐
│ Extension injects content script    │
│ (manifest.json content_scripts)     │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Content script initializes          │
│ BatchPageMetricsCollector           │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ PerformanceObservers created for:   │
│  - LCP (Largest Contentful Paint)  │
│  - CLS (Cumulative Layout Shift)   │
│  - FID (First Input Delay)          │
│  - INP (Interaction to Next Paint) │
│  - TBT (Total Blocking Time)        │
│  - TTI (Time to Interactive)        │
└────────┬────────────────────────────┘
         ↓
         ↓ 30 seconds pass
         ↓
Time: 30 seconds
┌─────────────────────────────────────┐
│ collectAll() called                 │
│ Metrics measured from observers:    │
│  LCP: 2400ms                        │
│  CLS: 0.05                          │
│  FID: 85ms                          │
│  INP: 150ms                         │
│  TBT: 200ms                         │
│  TTI: 3200ms                        │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Send message:                       │
│ type: 'PAGE_METRICS_COLLECTED'      │
│ payload: { page: { ... } }          │
└────────┬────────────────────────────┘
         ↓
Time: 30.1 seconds
┌─────────────────────────────────────┐
│ Background receives message         │
│ PAGE_METRICS_COLLECTED handler      │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Store metrics in browser storage:   │
│ 'guardian-metrics' = [              │
│   { url, metrics, ratings, ... }    │
│ ]                                   │
└────────┬────────────────────────────┘
         ↓
         ↓ User opens popup
         ↓
Time: 31 seconds (or any time after)
┌─────────────────────────────────────┐
│ Popup requests GET_ANALYTICS        │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Background retrieves from storage   │
│ Calculates summary statistics       │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Send response with:                 │
│  - totalMetrics: 1                  │
│  - webVitals: {...}                 │
│  - state.metricsCollecting: true    │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Popup displays:                     │
│ ✅ Monitoring Active                │
│ 1 sample                            │
│ LCP: 2400ms [good]                  │
│ CLS: 0.05 [good]                    │
│ FID: 85ms [good]                    │
│ INP: 150ms [good]                   │
└─────────────────────────────────────┘

✅ SUCCESS! User sees metrics!
```

---

## Build Process

```
Source Code
    ↓
Vite Build (3 browsers: Chrome, Firefox, Edge)
    ├─ Compiles TypeScript → JavaScript
    ├─ Bundles dependencies
    ├─ Generates service-worker-loader.js
    ├─ Generates UI bundles (popup.js, dashboard.js, etc.)
    ├─ Generates content script: assets/index.ts-XXXXX.js
    └─ Generates manifest.json (without content_scripts)
    ↓
Post-Build Script (scripts/update-manifest.mjs)
    ├─ Reads dist/[browser]/.vite/manifest.json
    ├─ Finds content script entry: "src/content/index.ts"
    ├─ Gets compiled file: "assets/index.ts-DQPXBVFO.js"
    └─ Injects into dist/[browser]/manifest.json:
       {
         "content_scripts": [{
           "matches": ["<all_urls>"],
           "js": ["assets/index.ts-DQPXBVFO.js"],
           "run_at": "document_start"
         }]
       }
    ↓
Ready for Distribution
    ├─ dist/chrome/         (Chrome Web Store)
    ├─ dist/firefox/        (Firefox Add-ons)
    └─ dist/edge/           (Edge Add-ons)

✅ All 3 browsers ready!
```

---

## Key Insight

The fundamental change: **Move metrics collection from service worker context to page context**

| Aspect              | Before (❌ Broken) | After (✅ Working) |
| ------------------- | ------------------ | ------------------ |
| Location            | Service Worker     | Content Script     |
| `window`            | ❌ Undefined       | ✅ Available       |
| `document`          | ❌ Undefined       | ✅ Available       |
| Performance API     | ❌ Not accessible  | ✅ Accessible      |
| PerformanceObserver | ❌ Cannot observe  | ✅ Can observe     |
| Collection          | ❌ Never happens   | ✅ Every 30s       |
| Metrics             | ❌ Always zero     | ✅ Real Web Vitals |
| Popup Display       | ❌ Nothing         | ✅ Full metrics    |

---

## Multi-Browser Support

```
Chrome Extension          Firefox Addon              Edge Extension
├─ dist/chrome/          ├─ dist/firefox/           ├─ dist/edge/
├─ manifest.json         ├─ manifest.json           ├─ manifest.json
├─ content_scripts ✅    ├─ content_scripts ✅      ├─ content_scripts ✅
├─ service-worker        ├─ background script       ├─ service-worker
└─ Fully functional      └─ Fully functional        └─ Fully functional
```

---

## What Wasn't Changed

✅ **Popup UI** - Works as-is, just needed data
✅ **Message Router** - Already supported PAGE_METRICS_COLLECTED
✅ **Storage Adapter** - Already capable of storing metrics
✅ **Web Vitals calculation** - Already in getAnalyticsSummary()

**We only added the missing piece: the metrics source!**

---

_For implementation details, see SESSION_12_COMPLETE_SUMMARY.md_
