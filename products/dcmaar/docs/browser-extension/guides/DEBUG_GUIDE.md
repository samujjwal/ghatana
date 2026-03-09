# Guardian Extension - Data Flow Debugging Guide

## Problem Statement

Web usage data is not appearing in the UI even though the extension is installed and content scripts should be running.

## Data Flow

```
1. Content Script (page context)
   └→ Collects web usage (URL, domain, title, time spent)
   └→ Sends PAGE_USAGE_TRACKED message via browser.runtime.sendMessage()

2. Background Service Worker (GuardianController)
   └→ Receives PAGE_USAGE_TRACKED message in router
   └→ Stores data in browser.storage.local["guardian-usage"]
   └→ Updates totalMetricsCollected counter

3. Popup/Dashboard UI (React components)
   └→ Sends GET_ANALYTICS message to background
   └→ Receives AnalyticsSummary with webUsage, topDomains, etc.
   └→ Displays in UI
```

## Debug Steps

### Step 1: Verify Content Script is Injected

**File to check:** `dist/chrome/manifest.json`

```bash
grep -A5 "content_scripts" dist/chrome/manifest.json
```

**Expected output:**

```json
"content_scripts": [
  {
    "matches": [
      "<all_urls>"
    ],
    "js": [
      "assets/index.ts-XXXXXXXX.js"
    ],
    "run_at": "document_start"
  }
]
```

✅ If you see this, content script is properly injected.

### Step 2: Load Extension in Chrome

1. Open `chrome://extensions/`
2. Enable "Developer mode" (toggle on top-right)
3. Click "Load unpacked"
4. Navigate to: `products/dcmaar/apps/guardian/apps/browser-extension/dist/chrome`
5. Extension should appear in the list

### Step 3: Open Extension Console (Background Logs)

1. In `chrome://extensions/`, find Guardian extension
2. Click "Inspect Views" → "background page"
3. This opens DevTools for the background service worker
4. Go to "Console" tab

**You should see logs like:**

```
[Guardian] Extension installed
[Guardian] Plugin host initialization...
[Guardian] Initializing Guardian...
[Guardian] Guardian initialized successfully
```

### Step 4: Trigger Page Tracking

1. Open a few different websites in new tabs (e.g., google.com, github.com, stackoverflow.com)
2. Stay on each page for 5+ seconds
3. Go back to Extension console (Step 3)

**You should see logs like:**

```
[GuardianController] PAGE_USAGE_TRACKED received {
  url: "https://www.google.com/",
  domain: "www.google.com",
  timestamp: 1700000000000,
  sessionDuration: 5000
}
[GuardianController] Existing records: 0
[GuardianController] Storing 1 records
```

If you DON'T see these logs, the problem is:

- Content script not running on pages
- OR messages not being received by background

### Step 5: Check Content Script Console

1. Right-click on a web page
2. Select "Inspect" (or press F12)
3. Go to "Sources" tab
4. In the left panel under "top", find and click the content script file (something like `assets/index.ts-XXXXXXXX.js`)
5. Go back to "Console" tab

**You should see logs like:**

```
[Guardian Content Script] Web usage tracking started {
  url: "https://www.google.com/",
  domain: "www.google.com",
  title: "Google"
}
```

If you DON'T see this:

- Content script not running on this page
- Check manifest permissions (`<all_urls>`)

### Step 6: Verify Storage Has Data

In the Extension console (Step 3), run:

```javascript
// Check stored web usage data
await chrome.storage.local.get("guardian-usage", (result) => {
  console.log("Stored usage data:", result["guardian-usage"]);
});
```

Or use shorthand (if your extension exposes controller):

```javascript
// If controller is exposed to global scope
const analytics = await guardianController.getAnalyticsSummary();
console.log("Analytics:", analytics);
```

**Expected output:**

```javascript
{
  webUsage: { last24h: 3, last7d: 3, allTime: 3 },
  timeSpent: { last24h: 15000, last7d: 15000, allTime: 15000 },
  topDomains: [
    { domain: 'www.google.com', visitCount: 1, totalTimeSpent: 5000, ... },
    { domain: 'github.com', visitCount: 1, totalTimeSpent: 5000, ... }
  ],
  totalUsageRecords: 3,
  ...
}
```

If storage is empty, the problem is:

- PAGE_USAGE_TRACKED handler not called
- OR storage.set() operation failing
- OR data being filtered out (check retention days: 7 days by default)

### Step 7: Check Popup Console

1. Click Guardian extension icon in toolbar
2. Popup should appear
3. Right-click popup and select "Inspect"
4. Go to "Console" tab

**You should see logs like:**

```
[Popup] Loading analytics...
[Popup] GET_ANALYTICS response {
  success: true,
  hasData: true,
  totalUsageRecords: 3
}
[Popup] Setting analytics { webUsage: {...}, topDomains: [...], ... }
```

If you see `success: false` or `hasData: false`:

- Analytics data not being retrieved from storage
- GET_ANALYTICS handler might be failing

### Step 8: Check Dashboard Console

1. Click Guardian extension icon
2. Click "View Detailed Report" button
3. New tab opens with dashboard
4. Right-click and select "Inspect"
5. Go to "Console" tab

**Should show same logs as Popup (Step 7)**

## Troubleshooting Flowchart

```
UI shows "No usage data yet"
├─ YES: Check Step 3 (Extension console)
│  ├─ Do you see PAGE_USAGE_TRACKED logs?
│  │  ├─ NO: Go to Step 5 (Content script console)
│  │  │  ├─ Do you see tracking started logs?
│  │  │  │  ├─ NO: Content script not running
│  │  │  │  │  └─ Check: manifest.json has content_scripts section
│  │  │  │  │  └─ Check: <all_urls> matches current page
│  │  │  │  │  └─ Check: extension not disabled/uninstalled
│  │  │  │  │
│  │  │  │  └─ YES: Content script running, but messages not sent
│  │  │  │     └─ Check: browser.runtime.sendMessage() in content script
│  │  │  │     └─ Check: browser console for errors
│  │  │  │
│  │  │  └─ YES: Go to Step 6 (check storage)
│  │  │
│  │  └─ YES: Handler being called
│  │     └─ Do you see "Storing N records" log?
│  │        ├─ NO: Data not stored
│  │        │  └─ Check: storage.set() errors
│  │        │
│  │        └─ YES: Go to Step 6 (verify storage)
│  │           └─ Does storage have data?
│  │              ├─ NO: storage.set() failing silently
│  │              │  └─ Check storage quota
│  │              │  └─ Check browser permissions
│  │              │
│  │              └─ YES: Go to Step 7 (check Popup)
│  │                 └─ Does GET_ANALYTICS return data?
│  │                    ├─ NO: Analytics retrieval failing
│  │                    │  └─ Check: getAnalyticsSummary() method
│  │                    │  └─ Check: storage.get() working
│  │                    │
│  │                    └─ YES: UI issue (state management)
│  │                       └─ Check: React state management
│  │                       └─ Check: data flowing to JSX
```

## Common Fixes

### Fix 1: Content Script Not Running

- [ ] Rebuild extension: `pnpm build`
- [ ] Reload extension in chrome://extensions (click reload icon)
- [ ] Reload web pages

### Fix 2: Messages Not Being Received

- [ ] Check manifest has content_scripts section
- [ ] Verify matches pattern: should be `<all_urls>`
- [ ] Check for multiple onMessage listeners conflicting

### Fix 3: Data Not Storing

- [ ] Check browser.storage.local is available
- [ ] Check for permission errors in console
- [ ] Verify storage quota not exceeded

### Fix 4: Data Not Showing in UI

- [ ] Check GET_ANALYTICS returns success: true
- [ ] Check React state management (setAnalytics)
- [ ] Check for null/undefined checks in JSX

## Debug Code Additions

### Enable Extra Logging in GuardianController

Add to PAGE_USAGE_TRACKED handler:

```typescript
console.log("[GuardianController] Message received:", {
  type: message.type,
  hasUrl: !!message.payload?.url,
  payloadKeys: Object.keys(message.payload || {}),
});
```

### Enable Extra Logging in Content Script

Add to initializeWebUsageTracking:

```typescript
console.log("[Guardian] Extension ID:", browser.runtime.id);
console.log("[Guardian] Can send messages:", !!browser.runtime.sendMessage);
```

### Check Storage Directly

In Extension console:

```javascript
// List all keys in storage
chrome.storage.local.get(null, (items) => {
  console.log("All storage keys:", Object.keys(items));
  console.log("All storage data:", items);
});
```

## Files to Review

1. **Content Script:** `src/content/index.ts`
   - Should send PAGE_USAGE_TRACKED messages

2. **Background Controller:** `src/controller/GuardianController.ts`
   - Should have onMessageType('PAGE_USAGE_TRACKED', ...) handler
   - Should call storage.set('guardian-usage', ...)

3. **UI Components:**
   - `src/popup/Popup.tsx` - requests GET_ANALYTICS
   - `src/dashboard/Dashboard.tsx` - requests GET_ANALYTICS

4. **Message Router:** `libs/typescript/browser-extension-core/src/adapters/BrowserMessageRouter.ts`
   - Should route messages from content to background

5. **Storage Adapter:** `libs/typescript/browser-extension-core/src/adapters/BrowserStorageAdapter.ts`
   - Should save/retrieve data from browser.storage.local

## Expected Behavior Timeline

1. **On extension load (0s)**
   - Extension installed/loaded
   - Background service worker starts
   - GuardianController initializes
   - Message handlers registered

2. **On page load (0-1s)**
   - Content script injects into page
   - Web usage tracking initializes
   - Initial PAGE_USAGE_TRACKED sent

3. **Every 30 seconds after**
   - PAGE_USAGE_TRACKED sent with updated sessionDuration

4. **On page exit**
   - PAGE_USAGE_TRACKED sent with final sessionDuration

5. **On Popup open**
   - GET_ANALYTICS request sent
   - Storage queried
   - Data aggregated
   - UI renders with data

## Notes

- Default retention: 7 days (records older than 7 days are filtered out)
- Tracking interval: 30 seconds
- Storage area: browser.storage.local (not sync)
- Storage key: "guardian-usage"
- Message types: "PAGE_USAGE_TRACKED", "GET_ANALYTICS", "EVALUATE_POLICY"
