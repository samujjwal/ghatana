# Data Fabric API Contracts

## Base URL

```
/api/v1/data-fabric
```

## Storage Profiles Endpoints

### List All Storage Profiles

**Endpoint:** `GET /profiles`

**Response:**
```typescript
{
  data: StorageProfile[];
  total: number;
  page: number;
  pageSize: number;
}
```

**Status Codes:**
- `200` - Success
- `400` - Invalid query parameters
- `401` - Unauthorized
- `500` - Server error

---

### Get Single Storage Profile

**Endpoint:** `GET /profiles/:id`

**Path Parameters:**
- `id` (string, required) - Profile ID

**Response:**
```typescript
{
  data: StorageProfile;
}
```

**Status Codes:**
- `200` - Success
- `404` - Profile not found
- `401` - Unauthorized
- `500` - Server error

---

### Create Storage Profile

**Endpoint:** `POST /profiles`

**Request Body:**
```typescript
{
  name: string;                                    // 1-255 chars
  type: StorageType;                              // S3, AZURE_BLOB, GCS, etc.
  description?: string;
  config: Record<string, unknown>;                // Type-specific configuration
  encryption: { 
    type: EncryptionType; 
    keyId?: string; 
  };
  compression: { 
    type: CompressionType; 
  };
  isDefault?: boolean;
}
```

**Response:**
```typescript
{
  data: StorageProfile;
}
```

**Status Codes:**
- `201` - Created
- `400` - Invalid input
- `401` - Unauthorized
- `409` - Duplicate name
- `500` - Server error

---

### Update Storage Profile

**Endpoint:** `PUT /profiles/:id`

**Path Parameters:**
- `id` (string, required) - Profile ID

**Request Body:**
```typescript
{
  name?: string;
  description?: string;
  config?: Record<string, unknown>;
  encryption?: { 
    type: EncryptionType; 
    keyId?: string; 
  };
  compression?: { 
    type: CompressionType; 
  };
  isActive?: boolean;
}
```

**Response:**
```typescript
{
  data: StorageProfile;
}
```

**Status Codes:**
- `200` - Updated
- `400` - Invalid input
- `401` - Unauthorized
- `404` - Profile not found
- `409` - Duplicate name
- `500` - Server error

---

### Delete Storage Profile

**Endpoint:** `DELETE /profiles/:id`

**Path Parameters:**
- `id` (string, required) - Profile ID

**Response:**
```typescript
{
  message: string;
}
```

**Status Codes:**
- `200` - Deleted
- `401` - Unauthorized
- `404` - Profile not found
- `409` - Profile in use (has active connectors)
- `500` - Server error

---

### Set Default Storage Profile

**Endpoint:** `PATCH /profiles/:id/set-default`

**Path Parameters:**
- `id` (string, required) - Profile ID

**Response:**
```typescript
{
  data: StorageProfile;
}
```

**Status Codes:**
- `200` - Updated
- `401` - Unauthorized
- `404` - Profile not found
- `500` - Server error

---

### Get Storage Profile Metrics

**Endpoint:** `GET /profiles/:id/metrics`

**Path Parameters:**
- `id` (string, required) - Profile ID

**Response:**
```typescript
{
  data: StorageMetrics;
}
```

**StorageMetrics:**
```typescript
{
  profileId: string;
  totalCapacity: number;              // Bytes
  usedCapacity: number;               // Bytes
  availableCapacity: number;          // Bytes
  lastUpdated: string;                // ISO 8601 timestamp
}
```

**Status Codes:**
- `200` - Success
- `401` - Unauthorized
- `404` - Profile not found
- `503` - Metrics unavailable (storage service down)
- `500` - Server error

---

## Data Connectors Endpoints

### List All Data Connectors

**Endpoint:** `GET /connectors`

**Query Parameters:**
- `storageProfileId?` (string) - Filter by storage profile
- `status?` (string) - Filter by status (active, inactive, error, testing)
- `sourceType?` (string) - Filter by source type

**Response:**
```typescript
{
  data: DataConnector[];
  total: number;
  page: number;
  pageSize: number;
}
```

**Status Codes:**
- `200` - Success
- `400` - Invalid query parameters
- `401` - Unauthorized
- `500` - Server error

---

### Get Single Data Connector

**Endpoint:** `GET /connectors/:id`

**Path Parameters:**
- `id` (string, required) - Connector ID

**Response:**
```typescript
{
  data: DataConnector;
}
```

**Status Codes:**
- `200` - Success
- `404` - Connector not found
- `401` - Unauthorized
- `500` - Server error

---

### Create Data Connector

**Endpoint:** `POST /connectors`

**Request Body:**
```typescript
{
  name: string;                              // 1-255 chars
  sourceType: string;                        // Database, API, File, etc.
  storageProfileId: string;                  // Must exist
  connectionConfig: Record<string, unknown>; // Type-specific config
  syncSchedule?: string;                     // Cron expression (e.g., "0 0 * * *")
  isEnabled?: boolean;
}
```

**Response:**
```typescript
{
  data: DataConnector;
}
```

**Status Codes:**
- `201` - Created
- `400` - Invalid input
- `401` - Unauthorized
- `404` - Storage profile not found
- `409` - Duplicate name
- `500` - Server error

---

### Update Data Connector

**Endpoint:** `PUT /connectors/:id`

**Path Parameters:**
- `id` (string, required) - Connector ID

**Request Body:**
```typescript
{
  name?: string;
  connectionConfig?: Record<string, unknown>;
  syncSchedule?: string;
  isEnabled?: boolean;
  storageProfileId?: string;
}
```

**Response:**
```typescript
{
  data: DataConnector;
}
```

**Status Codes:**
- `200` - Updated
- `400` - Invalid input
- `401` - Unauthorized
- `404` - Connector or storage profile not found
- `409` - Duplicate name
- `500` - Server error

---

### Delete Data Connector

**Endpoint:** `DELETE /connectors/:id`

**Path Parameters:**
- `id` (string, required) - Connector ID

**Response:**
```typescript
{
  message: string;
}
```

**Status Codes:**
- `200` - Deleted
- `401` - Unauthorized
- `404` - Connector not found
- `500` - Server error

---

### Test Data Connector Connection

**Endpoint:** `POST /connectors/:id/test`

**Path Parameters:**
- `id` (string, required) - Connector ID

**Response:**
```typescript
{
  success: boolean;
  message?: string;
  details?: Record<string, unknown>;
}
```

**Status Codes:**
- `200` - Test completed (check `success` field)
- `401` - Unauthorized
- `404` - Connector not found
- `500` - Server error

**Example Response (Success):**
```json
{
  "success": true,
  "message": "Connection established",
  "details": {
    "latency_ms": 45,
    "records_available": 15000
  }
}
```

**Example Response (Failure):**
```json
{
  "success": false,
  "message": "Connection refused",
  "details": {
    "error_code": "ECONNREFUSED",
    "host": "db.example.com",
    "port": 5432
  }
}
```

---

### Trigger Connector Sync

**Endpoint:** `POST /connectors/:id/sync`

**Path Parameters:**
- `id` (string, required) - Connector ID

**Request Body (Optional):**
```typescript
{
  full?: boolean;          // Full sync vs incremental (default: false)
  notify?: boolean;        // Send notifications (default: true)
}
```

**Response:**
```typescript
{
  jobId: string;
  status: "queued" | "running";
  startedAt: string;        // ISO 8601 timestamp
  estimatedDuration?: number; // Seconds (if available)
}
```

**Status Codes:**
- `202` - Accepted (job queued)
- `401` - Unauthorized
- `404` - Connector not found
- `409` - Sync already in progress
- `500` - Server error

---

### Get Sync Statistics

**Endpoint:** `GET /connectors/:id/sync-statistics`

**Path Parameters:**
- `id` (string, required) - Connector ID

**Response:**
```typescript
{
  data: SyncStatistics;
}
```

**SyncStatistics:**
```typescript
{
  connectorId: string;
  totalRecords: number;           // All-time total
  lastSyncRecords: number;        // Last sync count
  totalDuration: number;          // Seconds (all-time)
  lastSyncDuration: number;       // Seconds (last sync)
  errorCount: number;             // Total errors
  lastError?: string;             // Last error message
}
```

**Status Codes:**
- `200` - Success
- `401` - Unauthorized
- `404` - Connector not found
- `500` - Server error

---

### Get Connectors by Storage Profile

**Endpoint:** `GET /connectors/by-profile/:profileId`

**Path Parameters:**
- `profileId` (string, required) - Storage profile ID

**Response:**
```typescript
{
  data: DataConnector[];
  total: number;
}
```

**Status Codes:**
- `200` - Success
- `401` - Unauthorized
- `404` - Profile not found
- `500` - Server error

---

## Error Response Format

All error responses follow this format:

```typescript
{
  error: {
    code: string;           // Machine-readable error code
    message: string;        // User-friendly message
    details?: Record<string, unknown>; // Additional debugging info
    timestamp: string;      // ISO 8601 timestamp
    requestId: string;      // For tracking/support
  }
}
```

**Example:**
```json
{
  "error": {
    "code": "STORAGE_PROFILE_NOT_FOUND",
    "message": "Storage profile with ID 'prof_123' does not exist",
    "timestamp": "2024-11-05T10:30:45.123Z",
    "requestId": "req_abc123def456"
  }
}
```

---

## Common Error Codes

| Code | HTTP | Meaning |
|------|------|---------|
| `UNAUTHORIZED` | 401 | Missing or invalid authentication |
| `FORBIDDEN` | 403 | Insufficient permissions |
| `NOT_FOUND` | 404 | Resource does not exist |
| `VALIDATION_ERROR` | 400 | Invalid request body or parameters |
| `DUPLICATE_NAME` | 409 | Name already exists |
| `RESOURCE_IN_USE` | 409 | Cannot delete resource (in use) |
| `SYNC_IN_PROGRESS` | 409 | Sync already running |
| `STORAGE_SERVICE_ERROR` | 503 | Storage backend unavailable |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected server error |

---

## Rate Limiting

All endpoints are rate limited:

- **Standard**: 100 requests per minute per user
- **Bulk Operations**: 10 requests per minute
- **Test Connections**: 5 requests per minute

**Rate Limit Headers:**
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 1730775045
```

---

## Pagination

List endpoints support pagination via query parameters:

- `page` (default: 1) - Page number
- `pageSize` (default: 20, max: 100) - Items per page

**Response:**
```typescript
{
  data: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}
```

---

## Authentication

All endpoints require authentication via:

1. **Bearer Token** (recommended):
   ```
   Authorization: Bearer <token>
   ```

2. **API Key**:
   ```
   X-API-Key: <key>
   ```

3. **Session Cookie** (web):
   ```
   Cookie: session=<session_id>
   ```

---

## Examples

### Create and Configure Storage Profile

```typescript
// Step 1: Create S3 storage profile
const profileResponse = await axios.post("/api/v1/data-fabric/profiles", {
  name: "Production S3",
  type: "S3",
  config: {
    bucket: "my-data-bucket",
    region: "us-west-2",
    prefix: "prod/",
  },
  encryption: {
    type: "AES_256",
    keyId: "arn:aws:kms:us-west-2:123456789:key/12345",
  },
  compression: {
    type: "GZIP",
  },
  isDefault: true,
});

const profile = profileResponse.data.data;

// Step 2: Create connector for this profile
const connectorResponse = await axios.post("/api/v1/data-fabric/connectors", {
  name: "PostgreSQL to S3",
  sourceType: "PostgreSQL",
  storageProfileId: profile.id,
  connectionConfig: {
    host: "db.example.com",
    port: 5432,
    database: "production",
    query: "SELECT * FROM events WHERE created_at > ?",
  },
  syncSchedule: "0 */6 * * *", // Every 6 hours
  isEnabled: true,
});

// Step 3: Test connection
const testResponse = await axios.post(
  `/api/v1/data-fabric/connectors/${connectorResponse.data.data.id}/test`
);

console.log("Connection test:", testResponse.data);

// Step 4: Trigger first sync
const syncResponse = await axios.post(
  `/api/v1/data-fabric/connectors/${connectorResponse.data.data.id}/sync`,
  { full: true }
);

console.log("Sync job ID:", syncResponse.data.jobId);
```

### Monitor Sync Progress

```typescript
async function getSyncStats(connectorId: string) {
  const response = await axios.get(
    `/api/v1/data-fabric/connectors/${connectorId}/sync-statistics`
  );

  const stats = response.data.data;
  console.log(`Total records synced: ${stats.totalRecords}`);
  console.log(`Last sync: ${stats.lastSyncRecords} records in ${stats.lastSyncDuration}s`);

  if (stats.lastError) {
    console.error(`Last error: ${stats.lastError}`);
  }
}
```

### List All Active Connectors

```typescript
const response = await axios.get("/api/v1/data-fabric/connectors", {
  params: {
    status: "active",
    page: 1,
    pageSize: 50,
  },
});

console.log(`Found ${response.data.total} active connectors`);
console.log(response.data.data);
```
