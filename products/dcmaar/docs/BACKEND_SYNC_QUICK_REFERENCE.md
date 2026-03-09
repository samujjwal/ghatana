# Guardian Backend Sync - Quick Reference

## 🚀 Quick Start

### Enable Sync (Extension Settings)

1. Open Guardian Options
2. Scroll to **Backend Sync** section
3. Enter:
   - API URL: `http://localhost:3001`
   - Device ID: `device-001`
   - Child ID: (optional)
4. Enable toggle → **Save**

### Manual Sync

```javascript
chrome.runtime.sendMessage({ type: "SYNC_TO_BACKEND" }, (response) =>
  console.log(response.data)
);
```

### Check Status

```javascript
chrome.runtime.sendMessage({ type: "GET_BACKEND_SYNC_STATUS" }, (response) =>
  console.log(response.data)
);
```

---

## 📤 What Gets Sent

| Type   | Subtype              | Sent Automatically | Backend Request |
| ------ | -------------------- | ------------------ | --------------- |
| Usage  | domain_usage_summary | ✅ Every 30s       | ✅ On demand    |
| Usage  | page_visit           | ✅ Every 30s       | ✅ On demand    |
| Block  | page_blocked         | ✅ Every 30s       | ✅ On demand    |
| System | sync_started         | ✅ On sync         | ✅ Always       |
| System | sync_completed       | ✅ On sync         | ✅ Always       |

---

## 🔄 Backend → Extension: Request Data

### Request Sync (specific data)

```bash
POST /api/devices/{deviceId}/action

{
  "action": "request_data_sync",
  "params": {
    "since_timestamp": "2025-12-01T00:00:00Z",
    "data_types": ["usage", "blocks"],
    "reason": "recovery"
  }
}
```

### Request Snapshot (full history)

```bash
POST /api/devices/{deviceId}/action

{
  "action": "request_full_snapshot",
  "params": {
    "include_history": true,
    "reason": "audit"
  }
}
```

---

## 📡 APIs

### Events Ingestion

```
POST /api/events
✅ Receives usage, block, and system events
✅ Auto-batched and buffered
```

### Sync Poll

```
GET /api/devices/:id/sync
✅ Extension polls for commands
✅ Returns pending commands + policies
```

### Device Action

```
POST /api/devices/:id/action
✅ Request data sync
✅ Trigger force sync
✅ Send immediate commands
```

---

## 🔧 Configuration in Code

### GuardianConfig

```typescript
interface GuardianConfig {
  apiBaseUrl?: string; // Backend URL
  deviceId?: string; // Unique device ID
  childId?: string; // Child profile (optional)
  syncEnabled: boolean; // Enable/disable sync
}
```

### Initialize Programmatically

```typescript
const router = new BrowserMessageRouter();
await router.sendToBackground({
  type: "CONFIGURE_BACKEND_SYNC",
  payload: {
    apiBaseUrl: "http://localhost:3001",
    deviceId: "device-123",
    childId: "child-456",
    syncEnabled: true,
  },
});
```

---

## ⚠️ Error Handling

### All errors are **non-fatal**

- Network failures don't crash extension
- Backend communication fully async
- Errors logged to service worker console

### Check Logs

```
Open: chrome://extensions/
Find: Guardian extension
Click: "Service worker"
View: Console tab
```

### Common Issues

| Issue             | Check                                   |
| ----------------- | --------------------------------------- |
| Not syncing       | Backend URL correct? (`/health` works?) |
| No commands       | Device registered on backend?           |
| Errors in logs    | Network tab showing failures?           |
| Data not received | Extension actually enabled?             |

---

## 📊 Performance

| Metric          | Default    | Configurable |
| --------------- | ---------- | ------------ |
| Batch size      | 10 events  | Yes          |
| Flush interval  | 30 seconds | Yes          |
| Buffer size     | 100 events | Yes          |
| Poll interval   | 10 seconds | Yes          |
| Command timeout | 30 minutes | Yes          |

---

## 🔐 Security

✅ Bearer token authentication  
✅ Device ID validation  
✅ Command expiration  
✅ Privacy metadata tracking  
✅ PII level classification

---

## 📝 Sample Event

```json
{
  "schema_version": 1,
  "event_id": "evt-123",
  "kind": "usage",
  "subtype": "domain_usage_summary",
  "occurred_at": "2025-12-01T10:30:00Z",
  "source": {
    "agent_type": "browser_extension",
    "agent_version": "1.0.0",
    "device_id": "device-001",
    "child_id": "child-456"
  },
  "context": {
    "domain": "youtube.com",
    "time_spent_minutes": 45,
    "visits": 12,
    "blocked_attempts": 3,
    "content_risk": "high",
    "status": "allowed",
    "sync_reason": "automatic"
  },
  "privacy": {
    "pii_level": "low",
    "contains_raw_content": false
  }
}
```

---

## 🔗 Architecture

```
Extension                          Backend
├─ Settings UI                      ├─ Events Endpoint
├─ TelemetrySink                    ├─ Sync Endpoint
├─ CommandSyncSource      ◄────────►├─ Device Actions
└─ CommandExecutionSink             └─ Database
   └─ onDataRequest()
   └─ onSystemCommand()
```

---

## 📚 Related Docs

- Full guide: [BACKEND_SYNC_GUIDE.md](./BACKEND_SYNC_GUIDE.md)
- API reference: [API.md](./API.md)
- Event schema: [EVENTS_SCHEMA.md](./EVENTS_SCHEMA.md)
