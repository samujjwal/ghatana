# Guardian Backend Sync - API Reference

## Table of Contents

- [Extension Messages](#extension-messages)
- [Backend REST Endpoints](#backend-rest-endpoints)
- [Event Schema](#event-schema)
- [Command Schema](#command-schema)

---

## Extension Messages

### CONFIGURE_BACKEND_SYNC

Configure backend synchronization.

**Request**

```javascript
chrome.runtime.sendMessage({
  type: 'CONFIGURE_BACKEND_SYNC',
  payload: {
    apiBaseUrl: string,      // Backend URL (required)
    deviceId: string,        // Device ID (required)
    childId?: string,        // Child profile ID (optional)
    syncEnabled: boolean,    // Enable/disable sync (required)
  }
})
```

**Response**

```javascript
{
  success: boolean,
  data?: {
    apiBaseUrl: string,
    deviceId: string,
    childId?: string,
    syncEnabled: boolean,
  },
  error?: string,
}
```

**Example**

```javascript
chrome.runtime.sendMessage(
  {
    type: "CONFIGURE_BACKEND_SYNC",
    payload: {
      apiBaseUrl: "http://localhost:3001",
      deviceId: "device-001",
      childId: "child-xyz",
      syncEnabled: true,
    },
  },
  (response) => {
    if (response.success) {
      console.log("Configured:", response.data);
    } else {
      console.error("Failed:", response.error);
    }
  }
);
```

---

### SYNC_TO_BACKEND

Manually trigger data sync to backend.

**Request**

```javascript
chrome.runtime.sendMessage({
  type: "SYNC_TO_BACKEND",
});
```

**Response**

```javascript
{
  success: boolean,
  data?: {
    success: boolean,
    eventsSent: number,
    errors: string[],
  },
  error?: string,
}
```

**Example**

```javascript
chrome.runtime.sendMessage({ type: "SYNC_TO_BACKEND" }, (response) => {
  console.log(`Sent ${response.data.eventsSent} events`);
  if (response.data.errors.length > 0) {
    console.warn("Errors:", response.data.errors);
  }
});
```

---

### GET_BACKEND_SYNC_STATUS

Get current sync configuration and status.

**Request**

```javascript
chrome.runtime.sendMessage({
  type: "GET_BACKEND_SYNC_STATUS",
});
```

**Response**

```javascript
{
  success: boolean,
  data?: {
    configured: boolean,           // Has URL + deviceId
    enabled: boolean,              // Sync enabled in config
    apiBaseUrl?: string,
    deviceId?: string,
    childId?: string,
    telemetrySinkActive: boolean,  // Events sink initialized
    commandSyncActive: boolean,    // Command polling active
  },
  error?: string,
}
```

**Example**

```javascript
chrome.runtime.sendMessage({ type: "GET_BACKEND_SYNC_STATUS" }, (response) => {
  if (response.data.enabled) {
    console.log("Backend sync is active");
    console.log("URL:", response.data.apiBaseUrl);
    console.log("Device:", response.data.deviceId);
  }
});
```

---

## Backend REST Endpoints

### POST /api/events

Receive telemetry events from devices.

**Request**

```bash
POST /api/events
Content-Type: application/json
Authorization: Bearer {token}

{
  "events": [
    {
      "schema_version": 1,
      "event_id": "evt-123",
      "kind": "usage|block|policy|system|alert",
      "subtype": "string",
      "occurred_at": "2025-12-01T10:30:00Z",
      "received_at": "2025-12-01T10:30:01Z",  // Optional, set by server
      "source": {
        "agent_type": "browser_extension",
        "agent_version": "1.0.0",
        "device_id": "device-001",
        "child_id": "child-456",
        "session_id": "session-xyz"
      },
      "context": {
        "domain": "example.com",
        "time_spent_minutes": 45,
        "visits": 12,
        // ... additional context fields
      },
      "payload": {
        // ... event-specific payload
      },
      "ai": {
        "risk_score": 0.8,
        "risk_bucket": "high",
        "labels": ["inappropriate", "violence"],
        "model_version": "v1.2.3",
        "explanation": "Contains violent content"
      },
      "privacy": {
        "pii_level": "none|low|medium|high",
        "contains_raw_content": false,
        "hashed_fields": ["url"]
      },
      "metadata": {
        "source": "browser_sync",
        "batch_id": "batch-123"
      }
    }
  ]
}
```

**Response - 202 Accepted**

```json
{
  "accepted": 1
}
```

**Response - 400 Bad Request**

```json
{
  "error": "Invalid event payload",
  "details": [
    {
      "path": ["events", 0, "kind"],
      "message": "Invalid enum value"
    }
  ]
}
```

**Response - 500 Internal Server Error**

```json
{
  "error": "Failed to process events"
}
```

---

### GET /api/devices/:id/sync

Poll for pending commands and current policies (Agent Sync).

**Request**

```bash
GET /api/devices/{deviceId}/sync
Authorization: Bearer {token}
```

**Response - 200 OK**

```json
{
  "success": true,
  "data": {
    "schema_version": 1,
    "device_id": "device-001",
    "child_id": "child-456",

    "policies": [
      {
        "id": "policy-123",
        "name": "Block Adult Content",
        "policy_type": "content_filter",
        "priority": 1,
        "enabled": true,
        "config": {
          "categories": ["adult", "violence"],
          "action": "block",
          "notify_parent": true
        },
        "scope": "child"
      }
    ],

    "commands": {
      "items": [
        {
          "schema_version": 1,
          "command_id": "cmd-123",
          "kind": "data_request",
          "action": "request_data_sync",
          "target": {
            "device_id": "device-001",
            "child_id": "child-456"
          },
          "params": {
            "since_timestamp": "2025-12-01T00:00:00Z",
            "data_types": ["usage", "blocks"],
            "reason": "recovery"
          },
          "issued_by": {
            "actor_type": "system",
            "user_id": "user-123"
          },
          "created_at": "2025-12-01T10:00:00Z",
          "expires_at": "2025-12-01T10:30:00Z"
        }
      ],
      "count": 1,
      "last_sync": "2025-12-01T10:00:00Z"
    },

    "next_sync_seconds": 10,
    "metadata": {
      "version": "v1.0.0",
      "timestamp": "2025-12-01T10:00:00Z"
    }
  }
}
```

**Response - 404 Not Found**

```json
{
  "success": false,
  "error": "Device not found"
}
```

---

### POST /api/devices/:id/action

Queue an action/command for a device (parent-initiated).

**Request**

```bash
POST /api/devices/{deviceId}/action
Content-Type: application/json
Authorization: Bearer {token}

{
  "action": "request_data_sync | request_full_snapshot | force_sync | lock_device | ...",
  "params": {
    "since_timestamp": "2025-12-01T00:00:00Z",
    "data_types": ["usage", "blocks", "pages"],
    "reason": "data_recovery",
    // ... action-specific params
  }
}
```

**Response - 202 Accepted**

```json
{
  "success": true,
  "message": "Device action enqueued",
  "command_id": "cmd-xyz-123"
}
```

**Response - 400 Bad Request**

```json
{
  "success": false,
  "error": "Unsupported action: invalid_action",
  "supported_actions": [
    "request_data_sync",
    "request_full_snapshot",
    "force_sync",
    "lock_device",
    "unlock_device",
    "sound_alarm",
    "request_location"
  ]
}
```

**Response - 404 Not Found**

```json
{
  "success": false,
  "error": "Device not found"
}
```

---

### POST /api/devices/:id/commands/:commandId/ack

Acknowledge command execution (device-initiated).

**Request**

```bash
POST /api/devices/{deviceId}/commands/{commandId}/ack
Content-Type: application/json
Authorization: Bearer {token}

{
  "status": "processed|failed|expired|unsupported",
  "error_reason": "Command timed out",
  "executed_at": "2025-12-01T10:05:00Z"
}
```

**Response - 200 OK**

```json
{
  "success": true,
  "message": "Command acknowledged"
}
```

---

## Event Schema

### Event Envelope

```typescript
interface GuardianEvent {
  schema_version: number; // 1
  event_id: string; // "evt-{timestamp}-{random}"
  kind: "usage" | "block" | "policy" | "system" | "alert";
  subtype: string; // Event type
  occurred_at: string; // ISO 8601 timestamp
  received_at?: string; // Server timestamp
  source: {
    agent_type: string; // "browser_extension"
    agent_version: string; // "1.0.0"
    device_id?: string;
    child_id?: string;
    org_id?: string;
    session_id?: string;
  };
  context?: Record<string, unknown>;
  payload?: Record<string, unknown>;
  ai?: {
    risk_score?: number; // 0-1
    risk_bucket?: string; // "low|medium|high"
    labels?: string[];
    model_version?: string;
    explanation?: string;
  };
  privacy?: {
    pii_level: "none" | "low" | "medium" | "high";
    contains_raw_content: boolean;
    hashed_fields?: string[];
  };
  metadata?: Record<string, unknown>;
}
```

### Usage Event - Domain Summary

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
    "status": "allowed",
    "sync_reason": "automatic"
  }
}
```

### Usage Event - Page Visit

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
    "last_visit": "2025-12-01T10:30:00Z",
    "sync_reason": "automatic"
  }
}
```

### Block Event

```json
{
  "kind": "block",
  "subtype": "page_blocked",
  "context": {
    "domain": "adult-content.com",
    "url": "https://adult-content.com/page",
    "reason": "policy_violation",
    "policy_id": "policy-456",
    "category": "adult",
    "sync_reason": "automatic"
  }
}
```

### System Event - Sync Started

```json
{
  "kind": "system",
  "subtype": "sync_started",
  "context": {
    "reason": "manual_sync|automatic|backend_request",
    "requested_data_types": ["usage", "blocks"],
    "since_timestamp": "2025-12-01T00:00:00Z",
    "full_snapshot": false
  }
}
```

### System Event - Sync Completed

```json
{
  "kind": "system",
  "subtype": "sync_completed",
  "context": {
    "reason": "manual_sync",
    "events_sent": 42,
    "errors_count": 0,
    "full_snapshot": false
  }
}
```

---

## Command Schema

### Command Envelope

```typescript
interface GuardianCommand {
  schema_version: number; // 1
  command_id: string; // Unique ID
  kind:
    | "data_request"
    | "system"
    | "policy_update"
    | "immediate_action"
    | "session_request";
  action: string; // Specific action
  target?: {
    device_id?: string;
    child_id?: string;
    org_id?: string;
  };
  params?: Record<string, unknown>;
  issued_by: {
    actor_type: "parent" | "child" | "system";
    user_id?: string;
  };
  created_at: string; // ISO 8601
  expires_at?: string; // ISO 8601
  metadata?: Record<string, unknown>;
}
```

### Data Request Commands

**request_data_sync**

```json
{
  "kind": "data_request",
  "action": "request_data_sync",
  "target": { "device_id": "device-001" },
  "params": {
    "since_timestamp": "2025-12-01T00:00:00Z",
    "data_types": ["usage", "blocks"],
    "reason": "data_recovery"
  },
  "created_at": "2025-12-01T10:00:00Z",
  "expires_at": "2025-12-01T10:30:00Z"
}
```

**request_full_snapshot**

```json
{
  "kind": "data_request",
  "action": "request_full_snapshot",
  "target": { "device_id": "device-001" },
  "params": {
    "include_history": true,
    "reason": "monthly_audit"
  },
  "created_at": "2025-12-01T10:00:00Z",
  "expires_at": "2025-12-01T10:30:00Z"
}
```

### System Commands

**force_sync**

```json
{
  "kind": "system",
  "action": "force_sync",
  "target": { "device_id": "device-001" },
  "created_at": "2025-12-01T10:00:00Z"
}
```

---

## Error Codes

| Code | Status              | Description                          |
| ---- | ------------------- | ------------------------------------ |
| 202  | Accepted            | Event/action received and queued     |
| 400  | Bad Request         | Invalid request format or parameters |
| 401  | Unauthorized        | Missing or invalid token             |
| 403  | Forbidden           | Permission denied                    |
| 404  | Not Found           | Device or resource not found         |
| 409  | Conflict            | Device already registered            |
| 500  | Server Error        | Internal server error                |
| 503  | Service Unavailable | Database or service down             |

---

## Rate Limits

| Endpoint                     | Limit        | Window     |
| ---------------------------- | ------------ | ---------- |
| POST /api/events             | 1000 req/min | Per device |
| GET /api/devices/:id/sync    | 100 req/min  | Per device |
| POST /api/devices/:id/action | 100 req/min  | Per device |

---

## Timeouts

| Operation         | Timeout    |
| ----------------- | ---------- |
| Event flush       | 5 seconds  |
| Command sync poll | 10 seconds |
| Command execution | 30 seconds |
| API request       | 30 seconds |
| Command expiry    | 30 minutes |
