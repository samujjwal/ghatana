# Guardian Backend Sync - Testing & Troubleshooting Guide

## Table of Contents

- [Setup Verification](#setup-verification)
- [Testing Scenarios](#testing-scenarios)
- [Troubleshooting](#troubleshooting)
- [Debug Commands](#debug-commands)
- [Performance Testing](#performance-testing)

---

## Setup Verification

### 1. Backend Service Check

**Is the backend running?**

```bash
curl -v http://localhost:3001/health
```

Expected response:

```
HTTP/1.1 200 OK
```

**Check API endpoints:**

```bash
# Events endpoint
curl -X POST http://localhost:3001/api/events \
  -H "Content-Type: application/json" \
  -d '{"events": []}'

# Should return 202 Accepted or similar
```

### 2. Database Check

**Connect to PostgreSQL:**

```bash
psql -h localhost -U guardian -d guardian_db -c "SELECT version();"
```

**Check tables:**

```bash
psql -h localhost -U guardian -d guardian_db -c "\dt guardian_*"
```

**Verify tables exist:**

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema='public'
AND table_name LIKE 'guardian%';
```

### 3. Extension Installation

**Load extension:**

1. Open `chrome://extensions/`
2. Enable "Developer mode"
3. Click "Load unpacked"
4. Select `/browser-extension/dist/chrome/`

**Verify loaded:**

- Extension appears in list
- Icon visible in toolbar
- Status shows "Enabled"

### 4. Initial Configuration

**Configure in extension:**

```javascript
// Open DevTools console on extension options page
chrome.runtime.sendMessage(
  {
    type: "CONFIGURE_BACKEND_SYNC",
    payload: {
      apiBaseUrl: "http://localhost:3001",
      deviceId: "test-device-001",
      childId: "test-child-001",
      syncEnabled: true,
    },
  },
  (response) => {
    console.log("Response:", response);
  }
);
```

**Verify configuration:**

```javascript
chrome.runtime.sendMessage({ type: "GET_BACKEND_SYNC_STATUS" }, (response) => {
  console.log("Status:", response.data);
  // Should show:
  // - configured: true
  // - enabled: true
  // - telemetrySinkActive: true
  // - commandSyncActive: true
});
```

---

## Testing Scenarios

### Scenario 1: Manual Sync

**Objective**: Verify extension can send data to backend

**Steps**:

1. Open extension options
2. Enable Backend Sync
3. Navigate to a few websites
4. Open console in options page
5. Run:
   ```javascript
   chrome.runtime.sendMessage({ type: "SYNC_TO_BACKEND" }, (response) =>
     console.log("Result:", response.data)
   );
   ```
6. Check backend database:
   ```bash
   curl -X GET http://localhost:3001/api/events \
     -H "Authorization: Bearer {token}" | jq '.data | length'
   ```

**Expected**:

- Response shows `eventsSent > 0`
- No errors in response
- Backend database contains events

---

### Scenario 2: Backend Data Request

**Objective**: Backend requests data from extension

**Steps**:

1. **Get authentication token:**

   ```bash
   RESP=$(curl -s -X POST http://localhost:3001/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{
       "email":"parent@example.com",
       "password":"password123"
     }')
   TOKEN=$(echo $RESP | jq -r '.token')
   echo "Token: $TOKEN"
   ```

2. **Get device ID:**

   ```bash
   DEVICES=$(curl -s http://localhost:3001/api/devices \
     -H "Authorization: Bearer $TOKEN")
   DEVICE_ID=$(echo $DEVICES | jq -r '.data[0].id')
   echo "Device ID: $DEVICE_ID"
   ```

3. **Request data sync:**

   ```bash
   curl -X POST http://localhost:3001/api/devices/$DEVICE_ID/action \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "action": "request_data_sync",
       "params": {
         "since_timestamp": "'$(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ)'",
         "data_types": ["usage", "blocks"],
         "reason": "test_request"
       }
     }' | jq '.'
   ```

4. **Check service worker logs:**
   - Open `chrome://extensions/`
   - Find Guardian, click "Service worker"
   - Look for: `Data request received from backend`

5. **Verify data sent:**
   ```bash
   sleep 5
   curl -s "http://localhost:3001/api/events?limit=20" \
     -H "Authorization: Bearer $TOKEN" | jq '.data[] | select(.context.reason == "backend_request")'
   ```

**Expected**:

- Command queued successfully (command_id returned)
- Service worker logs show data request received
- New events appear in backend within 5 seconds

---

### Scenario 3: Command Timeout

**Objective**: Verify command expiration handling

**Steps**:

1. **Queue a command with short expiry:**

   ```bash
   CMD=$(curl -s -X POST http://localhost:3001/api/devices/$DEVICE_ID/action \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "action": "request_data_sync",
       "params": {"reason": "timeout_test"}
     }')
   CMD_ID=$(echo $CMD | jq -r '.command_id')
   echo "Command ID: $CMD_ID"
   ```

2. **Poll sync endpoint and let command expire:**

   ```bash
   # Command should be returned
   curl -s http://localhost:3001/api/devices/$DEVICE_ID/sync \
     -H "Authorization: Bearer $TOKEN" | jq '.data.commands.items[0]'

   # Wait for expiry (30 minutes or mock it)
   # Then check service worker logs for "expired" status
   ```

**Expected**:

- Command initially received
- After expiry, command marked as "expired" in logs

---

### Scenario 4: Network Failure Resilience

**Objective**: Verify extension continues working during network issues

**Steps**:

1. **Enable backend sync normally**

2. **Simulate network failure:**

   ```bash
   # Block backend port (requires sudo)
   sudo iptables -A OUTPUT -p tcp --dport 3001 -j DROP
   ```

3. **Navigate websites, use extension normally**
   - Extension should continue working
   - No errors or crashes

4. **Restore network:**

   ```bash
   sudo iptables -D OUTPUT -p tcp --dport 3001 -j DROP
   ```

5. **Check service worker logs:**
   - Should see error messages marked "(non-fatal)"
   - No crashes or exceptions

**Expected**:

- Extension remains functional during network outage
- Errors logged but never thrown
- Automatic recovery when network restored

---

### Scenario 5: Batch Processing

**Objective**: Verify event batching and flushing

**Steps**:

1. **Enable detailed logging:**

   ```javascript
   // In service worker console
   localStorage.setItem("DEBUG_TELEMETRY", "true");
   ```

2. **Generate events:**

   ```javascript
   // Navigate to 50+ different pages
   // Or use automation
   for (let i = 0; i < 50; i++) {
     chrome.tabs.create({ url: `https://example.com?page=${i}` });
   }
   ```

3. **Check service worker logs:**
   - Look for batch size messages
   - Check flush intervals
   - Verify buffer management

4. **Verify in backend:**
   ```bash
   curl -s "http://localhost:3001/api/events?limit=100" \
     -H "Authorization: Bearer $TOKEN" | \
     jq '[.data[].context.domain] | group_by(.) | map({domain: .[0], count: length})'
   ```

**Expected**:

- Events batched in groups of 10
- Flushed within 30 seconds
- No data loss
- Proper batch metadata

---

## Troubleshooting

### Issue: Extension Not Syncing

**Symptoms**:

- Manual sync shows 0 events sent
- No data appearing in backend

**Diagnostics**:

1. **Check configuration:**

   ```javascript
   chrome.runtime.sendMessage({ type: "GET_BACKEND_SYNC_STATUS" }, console.log);
   ```

   - Verify `enabled: true`
   - Verify `configured: true`
   - Check `apiBaseUrl` format

2. **Check service worker logs:**
   - Open `chrome://extensions/`
   - Click "Service worker" for Guardian
   - Look for initialization messages
   - Check for errors

3. **Manual test:**

   ```javascript
   chrome.runtime.sendMessage(
     {
       type: "CONFIGURE_BACKEND_SYNC",
       payload: {
         apiBaseUrl: "http://localhost:3001",
         deviceId: "manual-test-" + Date.now(),
         syncEnabled: true,
       },
     },
     (resp) => console.log("Configure:", resp)
   );
   ```

4. **Check backend accessibility:**
   ```bash
   curl -v http://localhost:3001/health
   # Should return 200 OK
   ```

**Solutions**:

- Verify backend is running: `ps aux | grep node`
- Check backend logs: `npm run dev` output
- Restart backend service
- Clear extension data: `chrome://extensions/` → Clear data
- Reload extension

---

### Issue: Backend Not Receiving Data

**Symptoms**:

- Manual sync reports success but no events in backend
- Backend API responding but no data stored

**Diagnostics**:

1. **Check backend logs:**

   ```bash
   # Terminal where backend is running
   # Look for POST /api/events logs
   # Should show "events accepted" messages
   ```

2. **Test API directly:**

   ```bash
   curl -X POST http://localhost:3001/api/events \
     -H "Content-Type: application/json" \
     -d '{
       "events": [{
         "schema_version": 1,
         "event_id": "test-1",
         "kind": "usage",
         "subtype": "test",
         "occurred_at": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
         "source": {
           "agent_type": "test",
           "agent_version": "1.0.0",
           "device_id": "test"
         }
       }]
     }'
   ```

3. **Check database:**

   ```bash
   psql -U guardian -d guardian_db -c \
     "SELECT COUNT(*) FROM guardian_events;"
   ```

4. **Check recent events:**
   ```bash
   psql -U guardian -d guardian_db -c \
     "SELECT event_id, kind, subtype, created_at FROM guardian_events ORDER BY created_at DESC LIMIT 10;"
   ```

**Solutions**:

- Check backend is actually receiving requests (proxy logs)
- Verify event schema matches backend expectations
- Check database connection: `psql` connectivity
- Review backend error logs
- Restart backend and database

---

### Issue: Commands Not Executing

**Symptoms**:

- Backend queues command successfully
- Extension doesn't process it

**Diagnostics**:

1. **Verify command in queue:**

   ```bash
   curl -s http://localhost:3001/api/devices/$DEVICE_ID/sync \
     -H "Authorization: Bearer $TOKEN" | jq '.data.commands'
   ```

2. **Check service worker logs for polls:**
   - Should see "CommandSyncSource" messages
   - Check poll interval (default 10 seconds)

3. **Manually trigger sync:**

   ```javascript
   chrome.runtime.sendMessage({ type: "PING" }, console.log);
   ```

4. **Check extension storage:**
   ```javascript
   chrome.storage.local.get(null, (items) => {
     console.log("Storage:", items);
   });
   ```

**Solutions**:

- Ensure device registered: `GET /api/devices`
- Check device ID matches exactly
- Restart extension
- Check command isn't expired (30 min default)
- Verify authentication token valid

---

### Issue: Performance - High CPU/Memory

**Symptoms**:

- Extension using excessive resources
- System slow during sync

**Diagnostics**:

1. **Check batch size:**

   ```javascript
   // Look in GuardianController.ts
   // TelemetrySink config batchSize: 10
   ```

2. **Monitor memory:**
   - Task Manager → Extensions column
   - Should be 20-50 MB

3. **Check buffer size:**
   ```bash
   # In service worker
   localStorage.getItem('telemetry_buffer_size')
   ```

**Solutions**:

- Reduce batch size to 5
- Reduce flush interval to 10 seconds
- Reduce max buffer size to 50
- Disable offline buffering if not needed

---

## Debug Commands

### Enable Service Worker Verbose Logging

```javascript
// In service worker console
localStorage.setItem("GUARDIAN_DEBUG", "true");
localStorage.setItem("DEBUG_SYNC", "true");
localStorage.setItem("DEBUG_TELEMETRY", "true");
localStorage.setItem("DEBUG_COMMANDS", "true");
```

### Disable Service Worker Verbose Logging

```javascript
localStorage.removeItem("GUARDIAN_DEBUG");
localStorage.removeItem("DEBUG_SYNC");
localStorage.removeItem("DEBUG_TELEMETRY");
localStorage.removeItem("DEBUG_COMMANDS");
```

### Check Full Sync Status

```javascript
chrome.runtime.sendMessage({ type: "GET_BACKEND_SYNC_STATUS" }, (r) => {
  console.table(r.data);
});
```

### Force Manual Sync with Logging

```javascript
console.log("=== STARTING MANUAL SYNC ===");
chrome.runtime.sendMessage({ type: "SYNC_TO_BACKEND" }, (response) => {
  console.log("=== SYNC COMPLETE ===");
  console.table(response.data);
  if (response.data.errors.length > 0) {
    console.error("Errors:", response.data.errors);
  }
});
```

### Test Backend Connectivity

```bash
# From terminal
for i in {1..10}; do
  time curl -s http://localhost:3001/health | jq '.'
  sleep 1
done
```

### Monitor Events in Real-time

```bash
# From terminal
watch -n 1 'psql -U guardian -d guardian_db -c "SELECT COUNT(*) FROM guardian_events WHERE created_at > NOW() - INTERVAL '\'1 minute'\';"'
```

### Check Command Queue

```bash
# From terminal with TOKEN set
curl -s "http://localhost:3001/api/devices/$DEVICE_ID/sync" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.commands | length'
```

---

## Performance Testing

### Load Test Setup

**Generate high volume of events:**

```javascript
// In content script
for (let i = 0; i < 1000; i++) {
  chrome.runtime.sendMessage({
    type: "RECORD_EVENT",
    payload: {
      domain: `site${i % 100}.com`,
      category: "test",
      timeSpent: Math.random() * 3600,
    },
  });
}
```

### Measure Throughput

```bash
# Measure events per second
BEFORE=$(psql -t -U guardian -d guardian_db -c \
  "SELECT COUNT(*) FROM guardian_events;")

sleep 60

AFTER=$(psql -t -U guardian -d guardian_db -c \
  "SELECT COUNT(*) FROM guardian_events;")

EPS=$(( ($AFTER - $BEFORE) / 60 ))
echo "Events per second: $EPS"
```

### Memory Profiling

```javascript
// In service worker
performance.memory &&
  console.log({
    usedJSHeapSize: performance.memory.usedJSHeapSize,
    totalJSHeapSize: performance.memory.totalJSHeapSize,
    jsHeapSizeLimit: performance.memory.jsHeapSizeLimit,
  });
```

### Network Profiling

```bash
# Capture network traffic
tcpdump -i lo port 3001 -w guardian.pcap

# Analyze
tcpdump -r guardian.pcap -A | grep -i "post\|get"
```

---

## Checklist

- [ ] Backend running on port 3001
- [ ] Database connected and initialized
- [ ] Extension loaded in Chrome
- [ ] Backend sync configured with valid URL
- [ ] Device ID set and unique
- [ ] Manual sync triggers successfully
- [ ] Events appear in backend database
- [ ] Service worker logs are clean
- [ ] Network connectivity verified
- [ ] No memory/CPU issues
- [ ] Commands execute when queued
- [ ] Error handling works (non-fatal)
