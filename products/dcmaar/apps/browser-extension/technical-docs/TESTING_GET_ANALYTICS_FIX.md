# Testing the Popup GET_ANALYTICS Fix

## Quick Start Test

### Step 1: Rebuild Extension

```bash
cd products/dcmaar/apps/guardian/apps/browser-extension
pnpm build
```

✅ Expected: Build completes successfully in ~2.7 seconds

### Step 2: Load in Chrome

1. Open `chrome://extensions/`
2. Enable "Developer mode" (top right)
3. Click "Load unpacked"
4. Select: `products/dcmaar/apps/guardian/apps/browser-extension/dist/chrome`
5. Verify Guardian appears in extensions list

### Step 3: Verify Background Service Worker

1. In Chrome extensions page, find Guardian
2. Click "Inspect views: service worker"
3. Service Worker console should show:
   ```
   [Guardian] Starting immediate initialization...
   [Guardian] Initializing Guardian...
   [Guardian] Controller initialization complete
   [Guardian] Initializing plugins from manifest...
   [Guardian] Plugin host initialization complete
   ```

✅ Expected: All initialization logs visible, no errors

### Step 4: Test Popup

1. Click Guardian extension icon
2. Popup opens
3. Popup console should show (F12 → Console):
   ```
   [Popup] Loading analytics...
   [Popup] GET_ANALYTICS response {
     success: true,
     hasData: true,
     totalUsageRecords: ...
   }
   ```

✅ Expected: Analytics data displays (not empty)
✅ Expected: Popup shows:

- Status (Monitoring Active/Inactive)
- Web Usage (24h, 7d, All-time counts)
- Time Spent (24h, 7d, All-time durations)
- Top Websites (if any data collected)
- Events Captured count

### Step 5: Test Dashboard

1. In popup, click "View Detailed Report"
2. Dashboard opens
3. Dashboard should show all analytics data:
   - ✅ Allowed/Blocked/Temp-Blocked counts
   - ✅ Top domains with status badges
   - ✅ Access Status Summary sections

✅ Expected: All sections populated with real data

---

## Detailed Verification

### Background Service Worker Health Check

Open Service Worker console (`chrome://extensions/` → Guardian → Inspect views)

**Test 1: Check Initialization**

```javascript
// Copy-paste in Service Worker console:
guardianController.getState();
```

Expected output:

```json
{
  "initialized": true,
  "metricsCollecting": true,
  "eventsCapturing": true,
  "totalMetricsCollected": 0,
  "totalEventsCollected": 0
}
```

**Test 2: Check Router is Ready**

```javascript
// Check if router has handlers
guardianController.router.typeHandlers;
```

Expected: Map with handlers for GET_ANALYTICS, GET_METRICS, GET_EVENTS, etc.

**Test 3: Manually Test GET_ANALYTICS**

```javascript
// Send GET_ANALYTICS message (mimics what popup does)
await guardianController.router.sendToBackground({
  type: "GET_ANALYTICS",
  payload: {},
});
```

Expected response:

```json
{
  "success": true,
  "data": {
    "totalUsageRecords": 0,
    "totalEvents": 0,
    "webUsage": { "last24h": 0, "last7d": 0, "allTime": 0 },
    "timeSpent": { "last24h": 0, "last7d": 0, "allTime": 0 },
    "topDomains": [],
    "state": { "metricsCollecting": true, "eventsCapturing": true }
  }
}
```

---

## Popup Console Debugging

Right-click on popup → Inspect → Console tab

### Expected Logs on First Load

```
[Popup] Loading analytics...
[Popup] GET_ANALYTICS response {
  success: true,
  hasData: true,
  totalUsageRecords: 0
}
[Popup] Setting analytics {...}
```

### If You See Error Messages

**Error 1**: `[Popup] GET_ANALYTICS returned no response`

- **Cause**: Handler not registered in time
- **Fix**: Wait 1 second and reopen popup
- **Verify**: Check Service Worker console for initialization errors

**Error 2**: `[Popup] GET_ANALYTICS failed with error: ...`

- **Cause**: Handler threw an exception
- **Inspect**: Look at handler implementation in GuardianController.ts
- **Debug**: Add console.debug() calls around getAnalyticsSummary()

**Error 3**: No logs at all

- **Cause**: BrowserMessageRouter not created or listening
- **Fix**: Verify Guard manifest has proper permissions and service_worker

---

## Collecting Real Data for Testing

To see non-empty analytics:

1. **Visit Some Websites**
   - After extension loads, visit google.com, github.com, etc.
   - Spend ~10 seconds on each site
   - Navigate between sites

2. **Check Content Script Is Running**
   - Open any website
   - Right-click → Inspect → Console
   - You should NOT see errors (Guardian content script runs silently)

3. **View Collected Data**
   - Open Guardian popup after visiting sites
   - You should see:
     - visitCount > 0
     - timeSpent > 0
     - topDomains populated

4. **Example Analytics After Testing**
   ```json
   {
     "totalUsageRecords": 3,
     "totalEvents": 0,
     "webUsage": { "last24h": 3, "last7d": 3, "allTime": 3 },
     "timeSpent": { "last24h": 45000, "last7d": 45000, "allTime": 45000 },
     "topDomains": [
       {
         "domain": "google.com",
         "visitCount": 1,
         "totalTimeSpent": 15000,
         "lastVisited": 1732380000000,
         "averageSessionDuration": 15000
       }
     ]
   }
   ```

---

## Test Checklist

- [ ] Build succeeds: `pnpm build` completes in ~2.7s
- [ ] Extension loads: Shows in `chrome://extensions/`
- [ ] Service Worker logs: Initialization logs visible
- [ ] getState() returns initialized: true
- [ ] Popup opens without errors
- [ ] Popup GET_ANALYTICS response: success: true
- [ ] Popup displays analytics data (not empty)
- [ ] Status shows "Monitoring Active"
- [ ] Dashboard loads: "View Detailed Report" works
- [ ] Dashboard shows allowed/blocked/temp-blocked counts
- [ ] Visit websites and see updated analytics

---

## Firefox Testing

Same steps, but:

1. Open `about:debugging#/runtime/this-firefox`
2. Click "Load Temporary Add-on"
3. Select `dist/firefox/manifest.json`
4. Right-click extension → Manage extension
5. Click "Inspect"

---

## Edge Testing

Same as Chrome:

1. Open `edge://extensions/`
2. Enable "Developer mode"
3. Click "Load unpacked"
4. Select `dist/edge`

---

## If Tests Fail

### Debugging Steps

1. **Check background service worker console**
   - Look for initialization errors
   - Verify all imports are working
   - Check plugin manifest loading

2. **Check popup console**
   - Look for GET_ANALYTICS errors
   - Verify BrowserMessageRouter is created
   - Check for timeout errors

3. **Verify files are loaded**
   - Service Worker: `chrome://extensions/` → Service worker inspector
   - Popup: Right-click popup → Inspect
   - Content Script: Open any website → Console (should be silent)

4. **Manual message test**

   ```javascript
   // In Service Worker console:
   const response = await browser.runtime.sendMessage({
     type: "GET_ANALYTICS",
     payload: {},
   });
   console.log(response);
   ```

5. **Check storage**
   ```javascript
   // In Service Worker console:
   const storage = await browser.storage.local.get(null);
   console.log(storage);
   ```

---

## Success Indicators

✅ **All Green When:**

- Service Worker shows initialization logs
- Popup receives GET_ANALYTICS response with success: true
- Dashboard displays analytics data with correct counts
- Popup and dashboard both update in real-time

✅ **Performance Targets:**

- Service Worker initialization: <100ms
- GET_ANALYTICS response time: <10ms
- Popup first load time: <500ms
- Dashboard load time: <1000ms

---

## Quick Troubleshooting

| Issue                         | Cause                      | Solution                      |
| ----------------------------- | -------------------------- | ----------------------------- |
| Popup shows "empty analytics" | Handler not registered     | Wait 1s, reopen popup         |
| Service Worker shows errors   | Import/plugin issue        | Check manifest, rebuild       |
| No websites tracked           | Content script not running | Check content-script.js loads |
| Dashboard shows all zeros     | No storage data collected  | Visit websites first, wait    |
| Popup takes long to load      | Service worker slow        | Check for background errors   |

---

## Notes

- Service worker may suspend after 5 minutes of inactivity
- Keepalive alarm runs every 1 minute to prevent suspension
- First popup open may take 1-2 seconds (initialization time)
- Subsequent popup opens are <500ms
- All times are approximate and browser-dependent
