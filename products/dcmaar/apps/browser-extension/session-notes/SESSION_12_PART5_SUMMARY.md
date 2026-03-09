# Session 12 Part 5 - Data Flow Debugging & Fixes

## Summary of Work Completed

### Issues Fixed

1. ✅ **Content Script Beforeunload Bug** - Fixed `beforeunload` event handler that was incorrectly using `navigator.sendBeacon()` (for HTTP requests) instead of `browser.runtime.sendMessage()` (for extension messages)

### Debugging Enhancements Added

1. ✅ **Comprehensive Console Logging**
   - Content script now logs when tracking starts and when messages fail
   - GuardianController now logs when PAGE_USAGE_TRACKED messages are received
   - GuardianController now logs storage operations and record counts
   - GuardianController now logs GET_ANALYTICS requests and responses
   - Popup now logs analytics loading and response data

2. ✅ **Created DEBUG_GUIDE.md** - Complete step-by-step guide with:
   - Data flow diagram showing all components
   - 8-step debugging process
   - Troubleshooting flowchart
   - Common fixes and solutions
   - Expected behavior timeline
   - Console commands to inspect storage directly

### Build Status

- ✅ Chrome extension built successfully
- ✅ Firefox extension built successfully
- ✅ Edge extension built successfully
- ✅ All manifests have content_scripts properly injected with correct compiled file references

## Files Modified

### 1. `src/content/index.ts`

**Change:** Fixed beforeunload handler

- **Before:** Used `navigator.sendBeacon()` which doesn't work for extension messages
- **After:** Uses `browser.runtime.sendMessage()` with proper error handling

### 2. `src/controller/GuardianController.ts`

**Changes:** Added debug logging to two handlers

**PAGE_USAGE_TRACKED handler:**

- Added logs showing: url, domain, timestamp, sessionDuration
- Added logs showing: existing record count, new filtered count
- Helps identify if messages are being received and stored

**GET_ANALYTICS handler:**

- Added logs showing: request received, response data structure
- Helps identify if data retrieval is working

### 3. `src/popup/Popup.tsx`

**Changes:** Added debug logging to analytics loading

- Added logs showing: analytics loading started
- Added logs showing: response success status, has data flag, total records
- Added logs showing: analytics data being set in state
- Added error logs if GET_ANALYTICS fails

### 4. `DEBUG_GUIDE.md` (New File)

Created comprehensive debugging guide with all necessary information to test and verify the data flow.

## How to Test

### Quick Test (5 minutes)

1. Run: `pnpm build` in the extension directory
2. Open `chrome://extensions/`
3. Load unpacked extension from `dist/chrome/`
4. Visit 3-4 websites (google.com, github.com, etc.)
5. Stay on each 5+ seconds
6. Click extension icon → "View Detailed Report"
7. Check if websites appear with visit counts and time spent

### Detailed Debug Test (15 minutes)

1. Complete Quick Test steps 1-4
2. Open Extension console:
   - `chrome://extensions/` → Inspector Views → background page → Console
3. Check for logs like:
   ```
   [GuardianController] PAGE_USAGE_TRACKED received {url: "...", ...}
   [GuardianController] Storing 1 records
   ```
4. If you see these logs, data is flowing correctly
5. Check Popup console for GET_ANALYTICS response
6. If no logs appear, check Content Script console (F12 on web page)

### Full Debug Process

See `DEBUG_GUIDE.md` for complete step-by-step instructions with:

- Manifest verification
- Extension loading
- Console inspection procedures
- Storage verification
- Troubleshooting flowchart
- Common fixes

## Expected Behavior After Fixes

### What Should Work Now

1. Content script runs on all pages (matches `<all_urls>`)
2. Collects web usage data (URL, domain, title, time)
3. Sends PAGE_USAGE_TRACKED messages to background every 30 seconds and on page exit
4. GuardianController receives messages and stores in IndexedDB
5. Popup/Dashboard request GET_ANALYTICS and receive data
6. UI displays:
   - Visit counts for last 24h, last 7d, all time
   - Time spent statistics
   - Top visited domains list
   - Event counts

### Debug Logs Visible

- In Extension console: PAGE_USAGE_TRACKED received/stored logs
- In Content script console: Web usage tracking started logs
- In Popup console: GET_ANALYTICS response logs with data

## Verification Checklist

- [ ] Build completes successfully (no errors/warnings)
- [ ] Content script file exists in dist/chrome/assets/
- [ ] manifest.json has content_scripts section with correct file reference
- [ ] Extension loads without errors in chrome://extensions/
- [ ] Extension console shows initialization logs
- [ ] Content script console shows tracking started on web pages
- [ ] After visiting pages, PAGE_USAGE_TRACKED logs appear
- [ ] After visiting pages, storage contains usage data
- [ ] Popup displays usage statistics and top domains
- [ ] Dashboard displays same information as popup
- [ ] Console logs show complete data flow

## Next Steps If Issues Remain

1. **If content script not running:**
   - Check manifest.json has content_scripts section
   - Verify matches pattern is `<all_urls>`
   - Reload extension and web pages

2. **If messages not received:**
   - Check Content Script console for sending errors
   - Verify browser.runtime.sendMessage() is available
   - Check for permission issues

3. **If storage not working:**
   - Check browser.storage.local quota
   - Run storage inspection command in Extension console
   - Check for storage permission errors

4. **If UI not showing data:**
   - Check Popup console for GET_ANALYTICS response
   - Verify response has success: true and data with totalUsageRecords > 0
   - Check React state management (setAnalytics)

## References

- **DEBUG_GUIDE.md** - Complete testing and troubleshooting guide
- **Source files:** All modified files have inline console.debug() statements
- **Build output:** dist/chrome/, dist/firefox/, dist/edge/

## Build Commands

```bash
# Full rebuild
pnpm build

# Watch mode (development)
pnpm dev

# Clean rebuild
pnpm clean && pnpm build
```

## Extension Installation

1. Chrome: `chrome://extensions/` → Load unpacked → `dist/chrome/`
2. Firefox: `about:debugging#/runtime/this-firefox` → Load Temporary Add-on → `dist/firefox/manifest.json`
3. Edge: `edge://extensions/` → Load unpacked → `dist/edge/`
