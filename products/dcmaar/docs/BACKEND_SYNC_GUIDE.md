# Guardian Backend Sync Implementation Guide

## Overview

The Guardian browser extension now supports bidirectional synchronization with the backend:

1. **Extension → Backend**: Send local analytics, usage data, and blocked events
2. **Backend → Extension**: Request data sync and execute commands

This guide covers setup, configuration, and operational procedures.

---

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────────┐
│                    Browser Extension                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────────────┐      ┌──────────────────┐                  │
│  │ GuardianConfig │      │ BrowserMessageRouter                │
│  │ - apiBaseUrl   │      │ - Messages from UI                  │
│  │ - deviceId     │      └──────────────────┘                  │
│  │ - childId      │                                             │
│  │ - syncEnabled  │      ┌──────────────────────────┐           │
│  └────────────────┘      │ GuardianController       │           │
│                          │ - manages all sinks      │           │
│                          │ - handles async sync     │           │
│                          │ - processes commands     │           │
│                          └──────────────────────────┘           │
│                                     ▲                           │
│         ┌───────────────────────────┼───────────────────────┐  │
│         │                           │                       │  │
│    ┌────▼─────────────┐   ┌────────▼────────┐   ┌──────────▼──┐ │
│    │  TelemetrySink   │   │ CommandSyncSource│   │CommandExecution│
│    │ - batches events │   │ - polls backend  │   │Sink           │
│    │ - sends to /api/ │   │ - receives cmds  │   │- executes cmds │
│    │   events         │   │                  │   │- handles data  │
│    └────┬─────────────┘   └──────────────────┘   │  requests     │
│         │                                        └──────────────┘
│         └────────────────────────────┬──────────────────────────┘
│                                      │
└──────────────────────────────────────┼──────────────────────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                  │
          POST /api/events    GET /api/devices/:id/sync   │
          (telemetry)         (poll for commands)         │
                    │                  │                  │
┌───────────────────▼──────────────────▼──────────────────▼────────┐
│                        Guardian Backend (Fastify)                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │ Events Routes    │  │ Device Routes    │  │ Agent Sync   │  │
│  │ POST /events     │  │ GET /sync        │  │ Service      │  │
│  │ POST /action     │  │ POST /action     │  │ - command    │  │
│  │ (data ingest)    │  │ (queue commands) │  │   registry   │  │
│  └──────────────────┘  └──────────────────┘  └──────────────┘  │
│         │                      │                      │         │
│         └──────────────────────┼──────────────────────┘         │
│                                │                                │
│                       ┌────────▼────────┐                       │
│                       │  PostgreSQL DB  │                       │
│                       │ - events table  │                       │
│                       │ - commands queue│                       │
│                       │ - policies      │                       │
│                       └─────────────────┘                       │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Setup & Configuration

### 1. Enable Backend Sync in Extension

#### Via Settings UI

1. Open the Guardian browser extension
2. Click **Options** (or Settings)
3. Scroll to **Backend Sync** section
4. Fill in configuration:
   - **API Base URL**: `http://localhost:3001` (or your backend URL)
   - **Device ID**: Unique identifier (e.g., `laptop-chrome-001`)
   - **Child ID**: Associated child profile ID (optional)
5. Toggle **Enable Backend Sync** to ON
6. Click **Save Configuration**

#### Programmatically

Send a message to the background script:

```javascript
// From content script or popup
chrome.runtime.sendMessage(
  {
    type: "CONFIGURE_BACKEND_SYNC",
    payload: {
      apiBaseUrl: "http://localhost:3001",
      deviceId: "device-123",
      childId: "child-456",
      syncEnabled: true,
    },
  },
  (response) => {
    console.log("Backend sync configured:", response);
  }
);
```

### 2. Verify Configuration

Check the sync status:

```javascript
chrome.runtime.sendMessage({ type: "GET_BACKEND_SYNC_STATUS" }, (response) => {
  console.log("Sync status:", response.data);
  // {
  //   configured: true,
  //   enabled: true,
  //   apiBaseUrl: '...',
  //   deviceId: '...',
  //   telemetrySinkActive: true,
  //   commandSyncActive: true
  // }
});
```

---

## Data Sync Operations

### Manual Sync

Manually push extension data to backend:

```javascript
chrome.runtime.sendMessage({ type: "SYNC_TO_BACKEND" }, (response) => {
  console.log("Sync result:", response.data);
  // {
  //   success: true,
  //   eventsSent: 42,
  //   errors: []
  // }
});
```

### Automatic Sync

Once enabled, the extension automatically:

- Batches events (10 events per batch by default)
- Flushes every 30 seconds
- Sends on demand when backend requests data

### Data Sent

Each sync operation sends:

**1. Usage Events** (`kind: 'usage'`)

```json
{
  "kind": "usage",
  "subtype": "domain_usage_summary",
  "context": {
    "domain": "youtube.com",
    "time_spent_minutes": 45,
    "visits": 12,
    "blocked_attempts": 3,
    "content_risk": "high",
    "status": "allowed"
  }
}
```

**2. Page Visit Events** (`kind: 'usage'`)

```json
{
  "kind": "usage",
  "subtype": "page_visit",
  "context": {
    "url": "https://youtube.com/watch?v=...",
    "domain": "youtube.com",
    "title": "Video Title",
    "time_spent_seconds": 1200,
    "category": "entertainment",
    "last_visit": "2025-12-01T10:30:00Z"
  }
}
```

**3. Block Events** (`kind: 'block'`)

```json
{
  "kind": "block",
  "subtype": "page_blocked",
  "context": {
    "domain": "adult-content.com",
    "url": "https://adult-content.com/page",
    "reason": "policy_violation",
    "policy_id": "policy-456",
    "category": "adult"
  }
}
```

**4. Content Category Summary** (full snapshot only)

```json
{
  "kind": "usage",
  "subtype": "content_category_summary",
  "context": {
    "category": "entertainment",
    "time_minutes": 120,
    "percentage": 35,
    "trend": "up"
  }
}
```

---

## Backend Data Requests

### Request Data Sync

The backend can request that an extension sync specific data:

```bash
curl -X POST http://localhost:3001/api/devices/{deviceId}/action \
  -H "Authorization: Bearer {authToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "request_data_sync",
    "params": {
      "since_timestamp": "2025-12-01T00:00:00Z",
      "data_types": ["usage", "blocks"],
      "reason": "data_recovery"
    }
  }'
```

**Response:**

```json
{
  "success": true,
  "message": "Device action enqueued",
  "command_id": "cmd-xyz-123"
}
```

**Parameters:**

- `since_timestamp` (optional): Only send data after this timestamp
- `data_types` (optional): Array of ["usage", "blocks", "pages"]
- `reason` (optional): Audit trail reason (e.g., "data_recovery", "compliance_audit")

### Request Full Snapshot

Request complete analytics snapshot with history:

```bash
curl -X POST http://localhost:3001/api/devices/{deviceId}/action \
  -H "Authorization: Bearer {authToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "request_full_snapshot",
    "params": {
      "include_history": true,
      "reason": "monthly_audit"
    }
  }'
```

---

## Command Types & Handlers

### Data Request Commands

The extension receives commands via polling `/api/devices/:id/sync`:

```json
{
  "command_id": "cmd-xyz-123",
  "kind": "data_request",
  "action": "request_data_sync",
  "target": {
    "device_id": "device-123"
  },
  "params": {
    "since_timestamp": "2025-12-01T00:00:00Z",
    "data_types": ["usage", "blocks"],
    "reason": "data_recovery"
  },
  "created_at": "2025-12-01T10:00:00Z",
  "expires_at": "2025-12-01T10:30:00Z"
}
```

### System Commands

Force sync policies:

```json
{
  "command_id": "cmd-abc-456",
  "kind": "system",
  "action": "force_sync",
  "target": {
    "device_id": "device-123"
  },
  "created_at": "2025-12-01T10:00:00Z"
}
```

### Handler Flow

1. `CommandSyncSource` polls `/api/devices/:id/sync` (configurable interval)
2. Receives command, emits event
3. `CommandExecutionSink.executeCommands()` processes each command
4. Routes to appropriate handler based on `kind`:
   - `data_request` → `onDataRequest()` → triggers sync
   - `system` → `onSystemCommand()` → handles force_sync
   - `policy_update` → `onPolicyUpdate()` → refreshes policies
   - `immediate_action` → `onImmediateAction()` → device controls
   - `session_request` → `onSessionRequest()` → session extensions

---

## Error Handling & Resilience

### Error Isolation

All backend communication is **fire-and-forget** with error isolation:

```typescript
// Example: Command results sent asynchronously
sendCommandResultsAsync(results) {
  (async () => {
    for (const result of results) {
      try {
        await this.telemetrySink.sendCommandEvent(...);
      } catch (error) {
        // Logged but NEVER thrown
        this.log("Failed to send command result (non-fatal)");
      }
    }
  })().catch(...); // Final catch for safety
}
```

### Guarantees

✅ **Extension won't crash** - All errors caught and logged  
✅ **Data won't be lost** - Telemetry buffers offline data  
✅ **Commands won't block** - Async handlers with timeouts  
✅ **Graceful degradation** - Sync failures don't affect core functionality

### Monitoring

Check service worker console for diagnostic logs:

```
[TelemetrySink] Initialized
[CommandSyncSource] started
[GuardianController] Synced to backend (eventsSent: 42, errors: 0)
[GuardianController] Data request received from backend (action: request_data_sync)
[GuardianController] Async policy sync failed (non-fatal)
```

---

## Implementation Details

### TelemetrySink

**Purpose**: Batch and send events to `/api/events`

**Configuration**:

```typescript
{
  apiBaseUrl: string;        // Backend URL
  deviceId: string;          // Device identifier
  childId?: string;          // Child profile (optional)
  agentType?: string;        // Default: 'browser_extension'
  agentVersion?: string;     // Default: '1.0.0'
  batchSize?: number;        // Default: 10
  flushIntervalMs?: number;  // Default: 30000
  maxBufferSize?: number;    // Default: 100
  bufferWhenOffline?: boolean; // Default: true
}
```

**Key Methods**:

- `initialize()` - Start periodic flush
- `sendEvent(...)` - Queue event
- `flush()` - Send queued events
- `shutdown()` - Final flush before closing

### CommandSyncSource

**Purpose**: Poll backend for pending commands

**Configuration**:

```typescript
{
  apiBaseUrl: string;
  deviceId: string;
  getAuthToken: () => string | null;
  pollIntervalMs?: number;   // Default: 10000
}
```

**Key Methods**:

- `start()` - Begin polling
- `stop()` - Stop polling
- `onEvent(handler)` - Register command handler

### CommandExecutionSink

**Purpose**: Execute commands locally

**Handlers**:

```typescript
{
  onPolicyUpdate?: (cmd) => Promise<void>;
  onImmediateAction?: (cmd) => Promise<void>;
  onSessionRequest?: (cmd) => Promise<void>;
  onDataRequest?: (cmd) => Promise<void>;
  onSystemCommand?: (cmd) => Promise<void>;
}
```

---

## Backend Endpoints Reference

### Events Ingestion

```
POST /api/events
Content-Type: application/json
Authorization: Bearer {token}

Body: {
  "events": [
    {
      "schema_version": 1,
      "event_id": "evt-123",
      "kind": "usage",
      "subtype": "domain_usage_summary",
      "occurred_at": "2025-12-01T10:30:00Z",
      "source": {
        "agent_type": "browser_extension",
        "agent_version": "1.0.0",
        "device_id": "device-123",
        "child_id": "child-456"
      },
      "context": {...},
      "payload": {...},
      "privacy": {
        "pii_level": "none",
        "contains_raw_content": false
      }
    }
  ]
}

Response: 202 Accepted
{
  "accepted": 1
}
```

### Agent Sync Poll

```
GET /api/devices/:deviceId/sync
Authorization: Bearer {token}

Response: 200 OK
{
  "success": true,
  "data": {
    "schema_version": 1,
    "device_id": "device-123",
    "child_id": "child-456",
    "policies": [...],
    "commands": {
      "items": [...],
      "count": 2,
      "last_sync": "2025-12-01T10:00:00Z"
    },
    "next_sync_seconds": 10,
    "metadata": {...}
  }
}
```

### Device Action (Request Data Sync)

```
POST /api/devices/:deviceId/action
Content-Type: application/json
Authorization: Bearer {token}

Body: {
  "action": "request_data_sync",
  "params": {
    "since_timestamp": "2025-12-01T00:00:00Z",
    "data_types": ["usage", "blocks"],
    "reason": "data_recovery"
  }
}

Response: 202 Accepted
{
  "success": true,
  "message": "Device action enqueued",
  "command_id": "cmd-xyz-123"
}
```

---

## Troubleshooting

### Extension Not Syncing

1. **Check Configuration**

   ```javascript
   chrome.runtime.sendMessage({ type: "GET_BACKEND_SYNC_STATUS" }, console.log);
   ```

   Verify `enabled: true` and `configured: true`

2. **Check Service Worker Logs**
   - Open `chrome://extensions/`
   - Find Guardian, click "Service worker"
   - Check browser console for errors

3. **Check Network**

   ```bash
   curl -v http://localhost:3001/health
   ```

4. **Manual Trigger**
   ```javascript
   chrome.runtime.sendMessage({ type: "SYNC_TO_BACKEND" }, (response) =>
     console.log("Manual sync:", response)
   );
   ```

### Backend Not Receiving Data

1. **Verify Events Endpoint**

   ```bash
   curl -X POST http://localhost:3001/api/events \
     -H "Content-Type: application/json" \
     -d '{"events": []}'
   ```

2. **Check Device Registration**

   ```bash
   curl http://localhost:3001/api/devices \
     -H "Authorization: Bearer {token}"
   ```

3. **Check Database**
   ```sql
   SELECT COUNT(*) FROM guardian_events
   WHERE created_at > NOW() - INTERVAL '1 hour';
   ```

### Commands Not Being Executed

1. **Check Command Queue**

   ```bash
   curl "http://localhost:3001/api/devices/{deviceId}/sync" \
     -H "Authorization: Bearer {token}"
   ```

   Should show pending commands

2. **Check Polling**
   - Service worker logs should show sync polls
   - Look for `CommandSyncSource started` message

3. **Manual Test**
   ```bash
   curl -X POST "http://localhost:3001/api/devices/{deviceId}/action" \
     -H "Authorization: Bearer {token}" \
     -H "Content-Type: application/json" \
     -d '{"action": "force_sync"}'
   ```

---

## Performance Considerations

### Batching

- Events batched up to 10 per request (configurable)
- Flushed every 30 seconds (configurable)
- Offline events buffered (up to 100, configurable)

### Polling

- Backend polls synced every 10 seconds by default
- Configurable interval in `CommandSyncSource`
- Exponential backoff on repeated failures (optional)

### Data Size

Typical sync payloads:

- **Domain usage**: ~200 bytes per domain
- **Page visits**: ~150 bytes per page
- **Block events**: ~100 bytes per block
- **Full snapshot**: 5-50 KB depending on history

### Throughput

- Typical: 50-200 events per minute
- Peak: Up to 1000 events per minute (batched)
- Backend can handle 10,000+ events/sec at scale

---

## Security Considerations

### Authentication

- Device uses bearer token from login
- Token included in all API requests
- Backend validates token before sync

### Data Privacy

- Events include `privacy` metadata:
  ```json
  "privacy": {
    "pii_level": "none|low|medium|high",
    "contains_raw_content": false,
    "hashed_fields": []
  }
  ```
- URLs logged as full strings (contains PII)
- Backend can filter based on privacy level

### Command Validation

- All commands validated by `validateAction()`
- Device ID in command must match device
- Commands expire after 30 minutes (configurable)

---

## Examples

### Complete Setup Workflow

```javascript
// 1. Configure backend sync
chrome.runtime.sendMessage(
  {
    type: "CONFIGURE_BACKEND_SYNC",
    payload: {
      apiBaseUrl: "http://localhost:3001",
      deviceId: "chrome-laptop-001",
      childId: "child-xyz-123",
      syncEnabled: true,
    },
  },
  (response) => {
    console.log("Configured:", response);

    // 2. Wait 2 seconds for initialization
    setTimeout(() => {
      // 3. Check status
      chrome.runtime.sendMessage(
        { type: "GET_BACKEND_SYNC_STATUS" },
        (status) => console.log("Status:", status.data)
      );

      // 4. Trigger manual sync
      chrome.runtime.sendMessage({ type: "SYNC_TO_BACKEND" }, (result) =>
        console.log("Sync result:", result.data)
      );
    }, 2000);
  }
);
```

### Backend Requesting Data

```bash
# 1. Get device token
TOKEN=$(curl -X POST http://localhost:3001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"parent@example.com","password":"pass"}' | jq -r '.token')

# 2. Get device ID
DEVICE_ID=$(curl http://localhost:3001/api/devices \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data[0].id')

# 3. Request data sync
curl -X POST "http://localhost:3001/api/devices/$DEVICE_ID/action" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "request_data_sync",
    "params": {
      "since_timestamp": "2025-12-01T00:00:00Z",
      "data_types": ["usage", "blocks"],
      "reason": "audit_recovery"
    }
  }'

# 4. Check events received
curl "http://localhost:3001/api/events?limit=10" \
  -H "Authorization: Bearer $TOKEN" | jq '.data[0]'
```

---

## References

- [Event Schema](./GUARDIAN_EVENTS_SCHEMA.md)
- [Command Types](./COMMAND_REGISTRY.md)
- [API Documentation](./API.md)
- [Extension Architecture](./BROWSER_EXTENSION_ARCHITECTURE.md)
