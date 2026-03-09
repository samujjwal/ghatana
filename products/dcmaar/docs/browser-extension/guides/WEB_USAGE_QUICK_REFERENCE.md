# Guardian Extension - Web Usage Tracking Quick Reference

## What Changed?

**Before:** Web performance metrics (LCP, CLS, FID, INP)  
**After:** Web usage patterns (visits, time spent, domain stats)

## Key Metrics Now Tracked

| Metric               | Description            | Example       |
| -------------------- | ---------------------- | ------------- |
| **Domain**           | Website visited        | `github.com`  |
| **Visit Count**      | How many times visited | `24 visits`   |
| **Time Spent**       | Total time on domain   | `30 minutes`  |
| **Session Duration** | Average time per visit | `1m 15s`      |
| **Last Visited**     | When you last visited  | `2 hours ago` |

## Popup Display

```
Guardian - Web Usage Monitor

✅ Monitoring Active (156 tracked)

Web Usage
┌─ 24 Hours: 42 visits, 1h 45m spent
├─ 7 Days: 156 visits, 3h 30m spent
└─ All Time: 432 visits, 12h spent

Top Websites
1. github.com (24 visits • 30m)
2. stackoverflow.com (18 visits • 28m)
3. google.com (15 visits • 5m)
...
```

## Storage

- **Key:** `guardian-usage`
- **Size:** ~500 bytes per domain visit
- **Retention:** 7 days
- **Total:** Usually 10-50 KB

## Content Script Behavior

1. Page loads → Records URL, domain, title, time
2. Every 30 seconds → Updates session duration
3. User leaves page → Records final time spent
4. Sends `PAGE_USAGE_TRACKED` message to background

## Message Flow

```
Content Script
    ↓ PAGE_USAGE_TRACKED
Background Service Worker
    ↓ stores in 'guardian-usage'
Browser Storage (IndexedDB/LocalStorage)
    ↓ GET_ANALYTICS request
Popup UI
    ↓ displays stats
User sees web usage report
```

## Time Format Examples

- `500ms` - milliseconds
- `30s` - seconds
- `5m` - minutes
- `2h` - hours

## Browser Compatibility

✅ Chrome  
✅ Firefox  
✅ Edge

## Testing

1. Load extension: `chrome://extensions` → Load unpacked → `dist/chrome`
2. Visit 5-10 different websites
3. Spend different amounts of time on each (30 seconds minimum)
4. Click extension icon to open popup
5. Verify:
   - Visit counts > 0
   - Time spent > 0
   - Top domains listed
   - Status shows "Monitoring Active"

## API Messages

### PAGE_USAGE_TRACKED (Content → Background)

```json
{
  "type": "PAGE_USAGE_TRACKED",
  "payload": {
    "url": "https://github.com/...",
    "domain": "github.com",
    "title": "Page Title",
    "timestamp": 1700000000000,
    "sessionDuration": 120000,
    "entryTime": 1699999880000
  }
}
```

### GET_ANALYTICS (Popup → Background)

```json
{
  "type": "GET_ANALYTICS",
  "payload": {}
}
```

**Response:**

```json
{
  "success": true,
  "data": {
    "totalUsageRecords": 432,
    "webUsage": {
      "last24h": 42,
      "last7d": 156,
      "allTime": 432
    },
    "timeSpent": {
      "last24h": 3600000,
      "last7d": 12600000,
      "allTime": 43200000
    },
    "topDomains": [
      {
        "domain": "github.com",
        "visitCount": 24,
        "totalTimeSpent": 1800000,
        "lastVisited": 1700000000000,
        "averageSessionDuration": 75000
      }
    ]
  }
}
```

## Files Modified

- ✅ `src/content/index.ts` - Web usage collector
- ✅ `src/controller/GuardianController.ts` - Analytics engine
- ✅ `src/popup/Popup.tsx` - UI redesign

## Build Status

```
Chrome:  ✅ 1908 modules, 2.62s
Firefox: ✅ 1908 modules, 2.59s
Edge:    ✅ 1908 modules, 2.56s
Manifest: ✅ Updated for all 3 browsers
```

## Privacy

- ✅ No content tracking
- ✅ No form data collection
- ✅ No password recording
- ✅ URL + domain + title only
- ✅ 7-day auto-purge
- ✅ Stored locally only

## Troubleshooting

| Issue                     | Solution                                   |
| ------------------------- | ------------------------------------------ |
| No data showing           | Wait 30+ seconds, visit multiple sites     |
| Extension shows 0 records | Refresh popup, wait for PAGE_USAGE_TRACKED |
| Popup loads slowly        | Clear browser cache, reload                |
| Top domains empty         | Visit more websites, return later          |
| Time shows 0m             | Need 30+ second sessions                   |

## Next: Production Deployment

1. Test in all 3 browsers
2. Verify data accuracy
3. Submit to Chrome Web Store
4. Submit to Firefox Add-ons
5. Submit to Microsoft Edge Add-ons

---

**Status:** ✅ Ready for testing and deployment
