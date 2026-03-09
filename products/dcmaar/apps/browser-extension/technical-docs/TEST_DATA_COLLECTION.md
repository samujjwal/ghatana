# Data Collection & Dashboard Test Guide

## Problem Summary

Dashboard not showing page names, domain names, or collected data.

## Root Cause Analysis

1. **Content script** was collecting data but NOT including `status` field
2. **GET_USAGE_SUMMARY** handler was delegating to plugin instead of using stored data
3. **Dashboard** was loading data but not properly displaying domain/page info

## Fixes Applied

### 1. Content Script (src/content/index.ts)

✅ Added `status: 'allowed'` field to WebUsageData interface
✅ Now includes status in all PAGE_USAGE_TRACKED messages
✅ Default status is 'allowed', blocking system can update if needed

### 2. GuardianController (src/controller/GuardianController.ts)

✅ Fixed GET_USAGE_SUMMARY to read from storage instead of plugin
✅ Properly aggregates data by domain:

- totalVisits per domain
- blockedVisits per domain
- totalDurationMs per domain
- lastVisitTimestamp per domain

### 3. Dashboard Display

The Dashboard.tsx already has full support for:

- Domain names: Shows in "Top Visited Domains" section
- Page titles: Shows in "Detailed Activity Log"
- URLs: Shows in activity log with full URL
- Session duration: Shows time spent per page
- Status badges: Shows allowed/blocked/temporarily blocked status

## Quick Testing Steps

### Step 1: Load Extension

```bash
# Build the extension
pnpm build

# In Chrome: chrome://extensions/
# Load unpacked → select dist/chrome folder
# Enable "Developer mode" to see background errors
```

### Step 2: Browse Websites

1. Open new tab
2. Visit: google.com
3. Visit: github.com
4. Visit: youtube.com
5. Let each page load for 5-10 seconds

### Step 3: Open Dashboard

1. Right-click extension icon → "Inspect popup"
2. Open Dashboard: chrome://extensions/[extension-id]/dashboard.html
   - OR: In extension popup, click Settings → Opens dashboard

### Step 4: Check Dashboard Shows:

✅ Collection Status: Should show "✓ Active" (green dot)
✅ Total Visits: Should show count > 0
✅ Top Visited Domains: Should show google.com, github.com, youtube.com with:

- Domain names ✓
- Visit counts ✓
- Time spent ✓
- Last visited timestamp ✓

✅ Web Usage Overview: Should show:

- Last 24h page visits
- Total time spent

✅ Detailed Activity Log: Should show recent pages with:

- Page title
- URL
- Time on page
- Domain name
- Relative time (e.g., "2m ago")

## Data Flow Verification

### Content Script → Background

```
URL: https://google.com
Domain: google.com
Title: Google
Status: allowed
Duration: 5000ms (updated every 30s)
  ↓
Sent to background via PAGE_USAGE_TRACKED message
  ↓
Stored in browser.storage.local["guardian-usage"]
```

### Dashboard Request → Backend Response

```
Dashboard calls: GET_ANALYTICS
  ↓
GuardianController.getAnalyticsSummary()
  ↓
Reads from storage["guardian-usage"]
  ↓
Aggregates by domain:
  - Counts visits
  - Sums duration
  - Tracks status
  ↓
Returns topDomains with full stats
  ↓
Dashboard renders domain list with metrics
```

## Common Issues & Fixes

### Issue: Dashboard shows "Collection Status: ✗ Inactive"

**Fix**: Controller state might not be initialized

- Check extension console: `chrome://extensions/[id]` → Service Worker errors
- Reload extension in Chrome

### Issue: "No domain data yet" message

**Fix**: Give it time to collect data

- Content script tracks on page load (immediate)
- Updates every 30 seconds
- Dashboard refreshes every 5 seconds
- Wait 1-2 minutes for first data to appear

### Issue: Domains showing but no page names

**Fix**: Page titles should auto-populate

- Check if `document.title` is available
- Some pages might have empty titles
- Look in "Detailed Activity Log" tab at bottom

### Issue: Time spent shows as 0

**Fix**: Content script recalculates on each update

- Initial track: sessionDuration = 0 (just started)
- After 30s: Shows ~30000ms
- After 1m: Shows ~60000ms
- On page exit: Shows final duration

## Expected Data Structure

### After visiting google.com for 30 seconds:

```json
{
  "analytics": {
    "topDomains": [
      {
        "domain": "google.com",
        "visitCount": 1,
        "totalTimeSpent": 30000,
        "lastVisited": 1732027845000,
        "status": "allowed",
        "averageSessionDuration": 30000
      }
    ],
    "webUsage": {
      "last24h": 1,
      "last7d": 1,
      "allTime": 1
    },
    "recentUsage": [
      {
        "url": "https://www.google.com/",
        "domain": "google.com",
        "title": "Google",
        "timestamp": 1732027845000,
        "sessionDuration": 30000,
        "status": "allowed"
      }
    ]
  }
}
```

## Debugging Steps

### Check if content script is running:

1. Open DevTools on any webpage: F12
2. Console tab
3. Should see: `[Guardian Content Script] Web usage tracking started`

### Check if background is receiving data:

1. Go to: chrome://extensions/[guardian-id]
2. Click "Service Worker"
3. Look for logs: `[GuardianController] PAGE_USAGE_TRACKED received`

### Check stored data:

1. DevTools on dashboard page
2. Console:

```javascript
const storage = await chrome.storage.local.get("guardian-usage");
console.log(storage);
```

### Check analytics summary:

```javascript
const { BrowserMessageRouter } = await import(
  "/@dcmaar/browser-extension-core"
);
const router = new BrowserMessageRouter();
const result = await router.sendToBackground({
  type: "GET_ANALYTICS",
  payload: {},
});
console.log(result);
```

## Build Status

✅ **All 3 browsers built successfully**

- Chrome: 2.84s
- Firefox: Built
- Edge: Built

✅ **No TypeScript errors**
✅ **Manifests updated with content scripts**

## Next Steps

1. ✅ Load extension in browser
2. ✅ Visit 3-5 websites
3. ✅ Open dashboard
4. ✅ Verify data appears with domain/page names
5. ✅ Check time tracking is accurate

If data still doesn't appear, see debugging steps above.
