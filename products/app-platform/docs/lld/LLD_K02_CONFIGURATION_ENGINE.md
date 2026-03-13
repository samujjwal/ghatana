# LOW-LEVEL DESIGN: K-02 CONFIGURATION ENGINE

**Module**: K-02 Configuration Engine  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Configuration Engine serves as the **single source of truth** for all runtime configuration across Project Siddhanta. It provides hierarchical configuration resolution, hot-reload capabilities, and tamper-evident audit trails.

**Core Responsibilities**:

- Schema-validated configuration storage and retrieval
- Hierarchical resolution: `Global → Jurisdiction → Operator → Tenant → Account → User`
- Hot-reload delivery via Event Bus (K-05)
- Cryptographic verification for air-gapped deployments
- Dual-calendar effective date activation
- CQRS-based read/write separation for performance
- Versioned metadata catalogs for dynamic value sets, task schemas, and process-template parameters

**Invariants**:

1. All configurations MUST validate against registered schemas
2. Resolution hierarchy MUST be deterministic and reproducible
3. Configuration changes MUST be immutable (append-only history)
4. Hot-reload MUST NOT cause service restarts
5. Effective dates MUST respect dual-calendar (BS + Gregorian)
6. Metadata catalogs MUST be schema-validated, versioned, and auditable like any other controlled configuration asset

### 1.2 Explicit Non-Goals

- ❌ Business data storage (e.g., actual tax rates belong in T1 Config Packs, not engine logic)
- ❌ Policy evaluation (delegated to K-03 Rules Engine)
- ❌ Real-time data streaming (use K-05 Event Bus directly)
- ❌ User preference storage (use dedicated user settings service)

### 1.3 Dependencies (Kernel Gates)

| Dependency           | Purpose                     | Readiness Gate |
| -------------------- | --------------------------- | -------------- |
| K-05 Event Bus       | Hot-reload event delivery   | K-05 stable    |
| K-07 Audit Framework | Tamper-evident audit trail  | K-07 stable    |
| K-15 Dual-Calendar   | Effective date resolution   | K-15 stable    |
| K-01 IAM             | Maker-checker authorization | K-01 stable    |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

#### 2.1.1 Write Operations (Admin API)

```yaml
POST /api/v1/config/schemas
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "schema_id": "fee_schedule",
  "version": "2.0.0",
  "json_schema": {
    "type": "object",
    "properties": {
      "brokerage_rate": {"type": "number", "minimum": 0, "maximum": 1},
      "minimum_fee": {"type": "number", "minimum": 0}
    },
    "required": ["brokerage_rate"]
  },
  "backward_compatible": true
}

Response 201:
{
  "schema_id": "fee_schedule",
  "version": "2.0.0",
  "status": "ACTIVE",
  "registered_at": "2025-03-02T10:30:00Z"
}

Error 400:
{
  "error": "SCHEMA_INCOMPATIBLE",
  "message": "Removed required field 'minimum_fee' breaks backward compatibility",
  "code": "CFG_E001"
}
```

```yaml
POST /api/v1/config/packs
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "pack_id": "np_fee_schedule_v2",
  "type": "JURISDICTION",
  "context_key": "NP",
  "schema_id": "fee_schedule",
  "schema_version": "2.0.0",
  "payload": {
    "brokerage_rate": 0.004,
    "minimum_fee": 25.0
  },
  "effective_date": {
    "bs": "2081-11-15",
    "gregorian": "2025-03-01T00:00:00Z"
  },
  "maker_id": "user_123",
  "reason": "SEBON directive 2081/11/10"
}

Response 202:
{
  "pack_id": "np_fee_schedule_v2",
  "status": "PENDING_APPROVAL",
  "approval_required": true,
  "workflow_id": "wf_456"
}
```

```yaml
PUT /api/v1/config/packs/{pack_id}/approve
Authorization: Bearer {checker_token}

Request:
{
  "checker_id": "user_789",
  "approval_comment": "Verified against SEBON circular"
}

Response 200:
{
  "pack_id": "np_fee_schedule_v2",
  "status": "APPROVED",
  "activated_at": "2025-03-01T00:00:00Z"
}
```

```yaml
POST /api/v1/config/metadata-assets
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "asset_id": "workflow.task-schema.kyc-review.v1",
  "asset_type": "TASK_SCHEMA",
  "scope": "JURISDICTION",
  "context_key": "NP",
  "schema_id": "kyc_review_task",
  "schema_version": "1.0.0",
  "ui_schema": {
    "layout": "two-column",
    "sections": ["identity", "risk", "decision"]
  },
  "payload": {
    "title": "KYC Review",
    "fields": [
      {"name": "risk_decision", "type": "select", "catalog_ref": "risk_decision_codes"},
      {"name": "review_notes", "type": "textarea", "required": true}
    ]
  },
  "effective_date": {
    "bs": "2082-01-01",
    "gregorian": "2026-04-14T00:00:00Z"
  },
  "maker_id": "user_123",
  "reason": "Align review task with revised onboarding policy"
}

Response 202:
{
  "asset_id": "workflow.task-schema.kyc-review.v1",
  "status": "PENDING_APPROVAL",
  "approval_required": true,
  "workflow_id": "wf_789"
}
```

```yaml
GET /api/v1/config/value-catalogs/{catalog_key}/resolve
Authorization: Bearer {service_token}
Query Parameters:
  - tenant_id: uuid (required)
  - jurisdiction: string (optional)
  - as_of_date: ISO8601 (optional)

Response 200:
{
  "catalog_key": "risk_decision_codes",
  "version": "3.1.0",
  "scope": "TENANT",
  "values": [
    {"code": "APPROVE", "label": "Approve", "order": 1},
    {"code": "EDD", "label": "Enhanced Due Diligence", "order": 2},
    {"code": "REJECT", "label": "Reject", "order": 3}
  ],
  "constraints": {
    "allows_custom_value": false,
    "deprecated_codes": []
  },
  "resolution_path": [
    {"level": "GLOBAL", "asset_id": "catalog.risk_decision.global.v1"},
    {"level": "JURISDICTION", "asset_id": "catalog.risk_decision.np.v2"},
    {"level": "TENANT", "asset_id": "catalog.risk_decision.tenant_42.v3"}
  ]
}
```

#### 2.1.2 Read Operations (Query API)

```yaml
GET /api/v1/config/resolve
Authorization: Bearer {service_token}
Query Parameters:
  - tenant_id: uuid (required)
  - jurisdiction: string (optional, derived from tenant if omitted)
  - account_id: uuid (optional)
  - user_id: uuid (optional)
  - schema_id: string (required)
  - as_of_date: ISO8601 (optional, defaults to now)

Response 200:
{
  "schema_id": "fee_schedule",
  "resolved_config": {
    "brokerage_rate": 0.004,
    "minimum_fee": 25.0
  },
  "resolution_path": [
    {"level": "GLOBAL", "pack_id": "global_defaults"},
    {"level": "JURISDICTION", "pack_id": "np_fee_schedule_v2"},
    {"level": "TENANT", "pack_id": null}
  ],
  "effective_as_of": "2025-03-02T10:30:00Z",
  "cache_ttl": 300
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.config.v1;

service ConfigService {
  rpc ResolveConfig(ResolveConfigRequest) returns (ResolveConfigResponse);
  rpc WatchConfig(WatchConfigRequest) returns (stream ConfigChangeEvent);
  rpc ValidateConfig(ValidateConfigRequest) returns (ValidateConfigResponse);
}

message ResolveConfigRequest {
  string tenant_id = 1;
  string schema_id = 2;
  optional string jurisdiction = 3;
  optional string account_id = 4;
  optional string user_id = 5;
  optional google.protobuf.Timestamp as_of_date = 6;
}

message ResolveConfigResponse {
  string schema_id = 1;
  google.protobuf.Struct resolved_config = 2;
  repeated ResolutionStep resolution_path = 3;
  google.protobuf.Timestamp effective_as_of = 4;
  int32 cache_ttl_seconds = 5;
}

message ResolutionStep {
  string level = 1;  // GLOBAL, JURISDICTION, TENANT, etc.
  optional string pack_id = 2;
}

message WatchConfigRequest {
  repeated string schema_ids = 1;
  string tenant_id = 2;
}

message ConfigChangeEvent {
  string pack_id = 1;
  string schema_id = 2;
  string event_type = 3;  // ACTIVATED, DEPRECATED, ROLLED_BACK
  google.protobuf.Timestamp timestamp = 4;
}
```

### 2.3 AsyncAPI Topics (Event Bus Integration)

```yaml
asyncapi: 2.6.0
info:
  title: Configuration Engine Events
  version: 1.0.0

channels:
  config.pack.activated:
    subscribe:
      message:
        name: ConfigPackActivatedEvent
        payload:
          type: object
          properties:
            event_id:
              type: string
              format: uuid
            pack_id:
              type: string
            type:
              type: string
              enum: [GLOBAL, JURISDICTION, OPERATOR, TENANT, ACCOUNT, USER]
            context_key:
              type: string
            schema_id:
              type: string
            effective_date_bs:
              type: string
            effective_date_gregorian:
              type: string
              format: date-time
            activated_by:
              type: string
          required:
            - event_id
            - pack_id
            - type
            - schema_id
            - effective_date_gregorian

  config.pack.rolled_back:
    subscribe:
      message:
        name: ConfigPackRolledBackEvent
        payload:
          type: object
          properties:
            event_id:
              type: string
              format: uuid
            pack_id:
              type: string
            previous_version:
              type: string
            rolled_back_to_version:
              type: string
            reason:
              type: string
            rolled_back_by:
              type: string
```

### 2.4 SDK Method Signatures (Language-Agnostic)

```typescript
interface ConfigClient {
  /**
   * Resolve configuration for given context
   * @throws ConfigSchemaNotFoundError
   * @throws ConfigResolutionError
   */
  resolve<T>(request: ResolveRequest): Promise<ResolvedConfig<T>>;

  /**
   * Watch for configuration changes
   * @returns AsyncIterator that yields config updates
   */
  watch(
    schemaIds: string[],
    tenantId: string,
  ): AsyncIterator<ConfigChangeEvent>;

  /**
   * Validate configuration payload against schema
   * @returns Validation result with errors if invalid
   */
  validate(schemaId: string, payload: unknown): ValidationResult;

  /**
   * Get cached configuration (non-blocking)
   * @returns Cached config or null if not in cache
   */
  getCached<T>(cacheKey: string): ResolvedConfig<T> | null;
}

interface ResolveRequest {
  tenantId: string;
  schemaId: string;
  jurisdiction?: string;
  accountId?: string;
  userId?: string;
  asOfDate?: Date;
}

interface ResolvedConfig<T> {
  schemaId: string;
  config: T;
  resolutionPath: ResolutionStep[];
  effectiveAsOf: Date;
  cacheTtl: number;
}
```

### 2.5 Error Model

| Error Code | HTTP Status | Retryable | Idempotent | Description                     |
| ---------- | ----------- | --------- | ---------- | ------------------------------- |
| CFG_E001   | 400         | No        | Yes        | Schema validation failed        |
| CFG_E002   | 404         | No        | Yes        | Schema not found                |
| CFG_E003   | 409         | No        | Yes        | Schema version conflict         |
| CFG_E004   | 400         | No        | Yes        | Invalid effective date          |
| CFG_E005   | 403         | No        | Yes        | Maker-checker approval required |
| CFG_E006   | 500         | Yes       | Yes        | Resolution engine failure       |
| CFG_E007   | 503         | Yes       | Yes        | Event bus unavailable           |
| CFG_E008   | 409         | No        | Yes        | Duplicate pack_id               |

**Retry Strategy**:

- Retryable errors: Exponential backoff (100ms, 200ms, 400ms, max 3 retries)
- Non-retryable errors: Fail fast, return error to caller
- Idempotency: All write operations use `command_id` for deduplication

---

## 3. DATA MODEL

### 3.1 Event Schemas (Versioned)

#### ConfigPackActivatedEvent v1.0.0

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "event_id": { "type": "string", "format": "uuid" },
    "event_version": { "type": "string", "const": "1.0.0" },
    "pack_id": { "type": "string" },
    "type": {
      "type": "string",
      "enum": [
        "GLOBAL",
        "JURISDICTION",
        "OPERATOR",
        "TENANT",
        "ACCOUNT",
        "USER"
      ]
    },
    "context_key": { "type": "string" },
    "schema_id": { "type": "string" },
    "schema_version": { "type": "string" },
    "effective_date_bs": {
      "type": "string",
      "pattern": "^\\d{4}-\\d{2}-\\d{2}$"
    },
    "effective_date_gregorian": { "type": "string", "format": "date-time" },
    "activated_by": { "type": "string" },
    "timestamp": { "type": "string", "format": "date-time" }
  },
  "required": [
    "event_id",
    "event_version",
    "pack_id",
    "type",
    "schema_id",
    "effective_date_gregorian",
    "timestamp"
  ]
}
```

### 3.2 Command Schemas

#### UpdateConfigCommand v1.0.0

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "command_id": { "type": "string", "format": "uuid" },
    "command_version": { "type": "string", "const": "1.0.0" },
    "pack_id": { "type": "string" },
    "type": {
      "type": "string",
      "enum": [
        "GLOBAL",
        "JURISDICTION",
        "OPERATOR",
        "TENANT",
        "ACCOUNT",
        "USER"
      ]
    },
    "context_key": { "type": "string" },
    "schema_id": { "type": "string" },
    "schema_version": { "type": "string" },
    "payload": { "type": "object" },
    "effective_date": {
      "type": "object",
      "properties": {
        "bs": { "type": "string" },
        "gregorian": { "type": "string", "format": "date-time" }
      },
      "required": ["bs", "gregorian"]
    },
    "maker_id": { "type": "string" },
    "reason": { "type": "string" }
  },
  "required": [
    "command_id",
    "pack_id",
    "type",
    "schema_id",
    "payload",
    "effective_date",
    "maker_id"
  ]
}
```

### 3.3 Storage Tables (Logical Schema)

#### config_schemas

```sql
CREATE TABLE config_schemas (
  schema_id VARCHAR(255) NOT NULL,
  version VARCHAR(50) NOT NULL,
  json_schema JSONB NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'DEPRECATED')),
  backward_compatible BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by VARCHAR(255) NOT NULL,
  deprecated_at TIMESTAMPTZ,
  PRIMARY KEY (schema_id, version)
);

CREATE INDEX idx_config_schemas_status ON config_schemas(status) WHERE status = 'ACTIVE';
```

#### config_packs

```sql
CREATE TABLE config_packs (
  pack_id VARCHAR(255) PRIMARY KEY,
  type VARCHAR(20) NOT NULL CHECK (type IN ('GLOBAL', 'JURISDICTION', 'OPERATOR', 'TENANT', 'ACCOUNT', 'USER')),
  context_key VARCHAR(255) NOT NULL,
  schema_id VARCHAR(255) NOT NULL,
  schema_version VARCHAR(50) NOT NULL,
  payload JSONB NOT NULL,
  effective_date_bs VARCHAR(10) NOT NULL,
  effective_date_gregorian TIMESTAMPTZ NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'DEPRECATED', 'ROLLED_BACK')),
  maker_id VARCHAR(255) NOT NULL,
  checker_id VARCHAR(255),
  approved_at TIMESTAMPTZ,
  activated_at TIMESTAMPTZ,
  reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (schema_id, schema_version) REFERENCES config_schemas(schema_id, version)
);

CREATE INDEX idx_config_packs_context ON config_packs(type, context_key, schema_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_config_packs_effective ON config_packs(effective_date_gregorian) WHERE status IN ('APPROVED', 'ACTIVE');
```

#### metadata_assets

```sql
CREATE TABLE metadata_assets (
  asset_id VARCHAR(255) PRIMARY KEY,
  asset_type VARCHAR(50) NOT NULL CHECK (asset_type IN ('PROCESS_TEMPLATE', 'STEP_TEMPLATE', 'TASK_SCHEMA', 'VALUE_CATALOG', 'ROUTING_POLICY')),
  scope VARCHAR(20) NOT NULL CHECK (scope IN ('GLOBAL', 'JURISDICTION', 'OPERATOR', 'TENANT')),
  context_key VARCHAR(255) NOT NULL,
  schema_id VARCHAR(255) NOT NULL,
  schema_version VARCHAR(50) NOT NULL,
  payload JSONB NOT NULL,
  ui_schema JSONB,
  compatibility_mode VARCHAR(20) NOT NULL DEFAULT 'STRICT' CHECK (compatibility_mode IN ('STRICT', 'ADDITIVE_ONLY', 'MIGRATABLE')),
  effective_date_bs VARCHAR(10) NOT NULL,
  effective_date_gregorian TIMESTAMPTZ NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'DEPRECATED', 'ROLLED_BACK')),
  maker_id VARCHAR(255) NOT NULL,
  checker_id VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_metadata_assets_lookup ON metadata_assets(asset_type, scope, context_key, schema_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_metadata_assets_effective ON metadata_assets(effective_date_gregorian) WHERE status IN ('APPROVED', 'ACTIVE');
```

#### value_catalog_entries

```sql
CREATE TABLE value_catalog_entries (
  asset_id VARCHAR(255) NOT NULL REFERENCES metadata_assets(asset_id),
  code VARCHAR(100) NOT NULL,
  label VARCHAR(255) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  is_deprecated BOOLEAN NOT NULL DEFAULT false,
  PRIMARY KEY (asset_id, code)
);

CREATE INDEX idx_value_catalog_active ON value_catalog_entries(asset_id, sort_order) WHERE is_deprecated = false;
```

### 3.4 Metadata Asset Taxonomy

| Asset Type         | Purpose                                                      | Primary Consumer      | Example                         |
| ------------------ | ------------------------------------------------------------ | --------------------- | ------------------------------- |
| `PROCESS_TEMPLATE` | Defines a reusable process profile or workflow variant       | W-01, O-01            | Nepal broker onboarding profile |
| `STEP_TEMPLATE`    | Defines typed handler contract and reusable step defaults    | W-01, K-17            | `maker_checker_approval`        |
| `TASK_SCHEMA`      | Defines human-task form fields, validation, and UI hints     | W-01, K-13            | KYC review task form            |
| `VALUE_CATALOG`    | Defines controlled option sets or bounded domains            | K-13, domain services | risk decision codes             |
| `ROUTING_POLICY`   | Defines assignment/escalation or rule-based branching inputs | W-01, O-01            | high-risk onboarding escalation |

### 3.5 Example Value Catalog Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ValueCatalog",
  "type": "object",
  "properties": {
    "catalog_key": { "type": "string" },
    "version": { "type": "string" },
    "value_type": {
      "type": "string",
      "enum": ["STRING", "NUMBER", "BOOLEAN", "OBJECT"]
    },
    "allows_custom_value": { "type": "boolean" },
    "entries": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "code": { "type": "string" },
          "label": { "type": "string" },
          "order": { "type": "integer" },
          "metadata": { "type": "object" },
          "deprecated": { "type": "boolean" }
        },
        "required": ["code", "label", "order"]
      }
    }
  },
  "required": ["catalog_key", "version", "value_type", "entries"]
}
```

#### resolved_config_cache (Materialized View)

```sql
CREATE MATERIALIZED VIEW resolved_config_cache AS
SELECT
  md5(tenant_id || schema_id || COALESCE(account_id, '') || COALESCE(user_id, '')) AS cache_key,
  tenant_id,
  schema_id,
  account_id,
  user_id,
  resolved_payload JSONB,
  resolution_path JSONB,
  effective_as_of TIMESTAMPTZ,
  expires_at TIMESTAMPTZ
FROM (
  -- Complex hierarchical resolution query
  -- Details in Section 5.1
) AS resolved_configs;

CREATE UNIQUE INDEX idx_resolved_cache_key ON resolved_config_cache(cache_key);
CREATE INDEX idx_resolved_cache_expires ON resolved_config_cache(expires_at);
```

### 3.6 Retention & Residency Tags

| Table                 | Retention Policy               | Residency Rule                                    |
| --------------------- | ------------------------------ | ------------------------------------------------- |
| config_schemas        | Permanent                      | Global (replicated)                               |
| config_packs          | Permanent                      | Per jurisdiction (if context_key is jurisdiction) |
| resolved_config_cache | 24 hours (auto-refresh)        | Same as tenant                                    |
| audit_trail           | Per K-07 policy (min 10 years) | Per jurisdiction                                  |

**Residency Enforcement**:

- Jurisdiction-level packs (type=JURISDICTION) stored in jurisdiction-specific region
- Tenant-level packs inherit tenant's data residency settings from K-02 itself
- Global packs replicated to all regions

---

## 4. CONTROL FLOW / SEQUENCE DIAGRAMS

### 4.1 Happy Path: Configuration Resolution

```
Client Service → ConfigClient.resolve(tenantId, schemaId)
  ↓
ConfigClient → Check local cache (in-memory)
  ↓ [Cache Miss]
ConfigClient → gRPC: ResolveConfig(request)
  ↓
ConfigService → Query resolved_config_cache (materialized view)
  ↓ [Cache Hit]
ConfigService → Return cached result
  ↓
ConfigClient → Store in local cache (TTL=300s)
  ↓
ConfigClient → Return ResolvedConfig to caller
```

### 4.2 Configuration Update Flow (Maker-Checker)

```
Admin (Maker) → POST /api/v1/config/packs
  ↓
ConfigCommandHandler → Validate payload against schema
  ↓
ConfigCommandHandler → Check if maker-checker required (via K-03 Rules Engine)
  ↓ [Approval Required]
ConfigCommandHandler → Create pack with status=PENDING_APPROVAL
  ↓
ConfigCommandHandler → Publish ConfigPackPendingApprovalEvent
  ↓
ConfigCommandHandler → Return 202 Accepted {workflow_id}
  ↓
Workflow Service → Notify checker via K-05
  ↓
Admin (Checker) → PUT /api/v1/config/packs/{pack_id}/approve
  ↓
ConfigCommandHandler → Verify checker != maker
  ↓
ConfigCommandHandler → Update status=APPROVED
  ↓
ConfigCommandHandler → Schedule activation at effective_date
  ↓
Scheduler (at effective_date) → Update status=ACTIVE
  ↓
Scheduler → Publish ConfigPackActivatedEvent to K-05
  ↓
All Subscribers → Receive event, invalidate local caches
  ↓
ConfigService → Rebuild resolved_config_cache (async)
```

### 4.3 Hot Reload Flow

```
ConfigPackActivatedEvent published to K-05
  ↓
ConfigClient (in each service) → Receive event via watch()
  ↓
ConfigClient → Invalidate local cache for affected schema_id
  ↓
ConfigClient → Trigger callback: onConfigChanged(schemaId, newConfig)
  ↓
Domain Service → Reload configuration
  ↓
Domain Service → Apply new config (e.g., update fee rates)
  ↓
Domain Service → Log reload success to K-07
```

### 4.4 Rollback Flow

```
Admin → POST /api/v1/config/packs/{pack_id}/rollback
  ↓
ConfigCommandHandler → Validate rollback target version exists
  ↓
ConfigCommandHandler → Create new pack_id with previous payload
  ↓
ConfigCommandHandler → Mark current pack as ROLLED_BACK
  ↓
ConfigCommandHandler → Activate rollback pack immediately
  ↓
ConfigCommandHandler → Publish ConfigPackRolledBackEvent
  ↓
All Subscribers → Invalidate caches, reload config
  ↓
K-07 Audit → Record rollback action with reason
```

### 4.5 Air-Gapped Deployment Flow

```
Offline Admin → Generate signed config bundle (CLI tool)
  ↓
CLI Tool → Bundle: {packs[], schemas[], signature}
  ↓
CLI Tool → Sign with platform private key
  ↓
Offline Admin → Transfer bundle to air-gapped environment
  ↓
On-Prem Admin → Load bundle via CLI: config-loader --bundle=bundle.tar.gz
  ↓
ConfigLoader → Verify signature with platform public key
  ↓ [Signature Valid]
ConfigLoader → Import schemas to config_schemas table
  ↓
ConfigLoader → Import packs to config_packs table
  ↓
ConfigLoader → Trigger cache rebuild
  ↓
ConfigLoader → Return success report
```

### 4.6 Correlation ID Propagation

All operations propagate `correlation_id` (trace ID) through:

1. HTTP Header: `X-Correlation-ID`
2. gRPC Metadata: `correlation-id`
3. Event Payload: `correlation_id` field
4. Database: Logged in audit trail

```
Client Request [correlation_id: abc-123]
  ↓
API Gateway → Inject/forward correlation_id
  ↓
ConfigService → Log with correlation_id
  ↓
Event Published → Include correlation_id in payload
  ↓
Subscriber → Extract correlation_id, continue trace
  ↓
K-07 Audit → Record with correlation_id
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Configuration Resolution Algorithm

```python
def resolve_config(tenant_id: str, schema_id: str, account_id: Optional[str] = None,
                   user_id: Optional[str] = None, as_of_date: datetime = None) -> ResolvedConfig:
    """
    Hierarchical configuration resolution with override semantics.

    Resolution order (later overrides earlier):
    1. GLOBAL
    2. JURISDICTION (derived from tenant)
    3. OPERATOR (if tenant belongs to operator group)
    4. TENANT
    5. ACCOUNT (if account_id provided)
    6. USER (if user_id provided)
    """
    as_of_date = as_of_date or datetime.utcnow()

    # Step 1: Fetch tenant metadata to derive jurisdiction
    tenant = get_tenant(tenant_id)
    jurisdiction = tenant.jurisdiction_code
    operator_id = tenant.operator_id

    # Step 2: Query all applicable config packs
    packs = query_config_packs(
        schema_id=schema_id,
        filters=[
            ("type", "=", "GLOBAL"),
            ("type", "=", "JURISDICTION", "context_key", "=", jurisdiction),
            ("type", "=", "OPERATOR", "context_key", "=", operator_id) if operator_id else None,
            ("type", "=", "TENANT", "context_key", "=", tenant_id),
            ("type", "=", "ACCOUNT", "context_key", "=", account_id) if account_id else None,
            ("type", "=", "USER", "context_key", "=", user_id) if user_id else None,
        ],
        effective_before=as_of_date,
        status="ACTIVE"
    )

    # Step 3: Sort by hierarchy level
    hierarchy_order = ["GLOBAL", "JURISDICTION", "OPERATOR", "TENANT", "ACCOUNT", "USER"]
    packs.sort(key=lambda p: hierarchy_order.index(p.type))

    # Step 4: Deep merge payloads (later overrides earlier)
    resolved_payload = {}
    resolution_path = []

    for pack in packs:
        resolved_payload = deep_merge(resolved_payload, pack.payload)
        resolution_path.append({
            "level": pack.type,
            "pack_id": pack.pack_id,
            "effective_date": pack.effective_date_gregorian
        })

    # Step 5: Validate merged result against schema
    schema = get_schema(schema_id, latest_version=True)
    validate_against_schema(resolved_payload, schema.json_schema)

    # Step 6: Return resolved config with metadata
    return ResolvedConfig(
        schema_id=schema_id,
        config=resolved_payload,
        resolution_path=resolution_path,
        effective_as_of=as_of_date,
        cache_ttl=300  # 5 minutes
    )

def deep_merge(base: dict, override: dict) -> dict:
    """
    Deep merge two dictionaries. Override values take precedence.
    Arrays are replaced, not merged.
    """
    result = base.copy()
    for key, value in override.items():
        if key in result and isinstance(result[key], dict) and isinstance(value, dict):
            result[key] = deep_merge(result[key], value)
        else:
            result[key] = value
    return result
```

### 5.2 Hot Reload Trigger Logic

```python
def activate_config_pack(pack_id: str):
    """
    Activate a config pack and trigger hot reload across all services.
    """
    # Step 1: Update pack status
    pack = get_config_pack(pack_id)
    pack.status = "ACTIVE"
    pack.activated_at = datetime.utcnow()
    save(pack)

    # Step 2: Invalidate materialized view cache
    refresh_materialized_view("resolved_config_cache", where=f"schema_id = '{pack.schema_id}'")

    # Step 3: Publish activation event
    event = ConfigPackActivatedEvent(
        event_id=uuid4(),
        pack_id=pack.pack_id,
        type=pack.type,
        context_key=pack.context_key,
        schema_id=pack.schema_id,
        effective_date_bs=pack.effective_date_bs,
        effective_date_gregorian=pack.effective_date_gregorian,
        activated_by=pack.checker_id or pack.maker_id,
        timestamp=datetime.utcnow()
    )
    event_bus.publish("config.pack.activated", event)

    # Step 4: Audit log
    audit_client.record(
        action="CONFIG_PACK_ACTIVATED",
        resource_uri=f"/config/packs/{pack_id}",
        actor_id=pack.checker_id or pack.maker_id,
        before_state={"status": "APPROVED"},
        after_state={"status": "ACTIVE"},
        timestamp_bs=convert_to_bs(datetime.utcnow()),
        timestamp_gregorian=datetime.utcnow()
    )
```

### 5.3 Canary Rollout Strategy

```python
def canary_rollout(pack_id: str, canary_percentage: int = 10):
    """
    Gradually roll out config changes to a percentage of tenants.
    """
    pack = get_config_pack(pack_id)

    # Step 1: Identify canary cohort (deterministic hash-based)
    all_tenants = get_all_tenants()
    canary_tenants = [
        t for t in all_tenants
        if (hash(t.tenant_id + pack_id) % 100) < canary_percentage
    ]

    # Step 2: Create tenant-specific override packs
    for tenant in canary_tenants:
        canary_pack = create_config_pack(
            pack_id=f"{pack_id}_canary_{tenant.tenant_id}",
            type="TENANT",
            context_key=tenant.tenant_id,
            schema_id=pack.schema_id,
            payload=pack.payload,
            effective_date=datetime.utcnow()
        )
        activate_config_pack(canary_pack.pack_id)

    # Step 3: Monitor metrics for canary cohort
    monitor_canary_metrics(pack_id, canary_tenants, duration_minutes=30)

    # Step 4: If metrics healthy, promote to full rollout
    if canary_metrics_healthy():
        activate_config_pack(pack_id)  # Activate for all tenants
        cleanup_canary_packs(pack_id)
    else:
        rollback_canary(pack_id, canary_tenants)
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation                | P50   | P95   | P99   | P99.9 | Timeout |
| ------------------------ | ----- | ----- | ----- | ----- | ------- |
| resolve() - cache hit    | 0.5ms | 1ms   | 2ms   | 5ms   | 10ms    |
| resolve() - cache miss   | 5ms   | 15ms  | 30ms  | 50ms  | 100ms   |
| update() - write         | 10ms  | 25ms  | 50ms  | 100ms | 500ms   |
| approve() - workflow     | 50ms  | 100ms | 200ms | 500ms | 2s      |
| watch() - event delivery | N/A   | 50ms  | 100ms | 200ms | N/A     |

**Budget Allocation**:

- Local cache lookup: 0.5ms
- gRPC call overhead: 2ms
- Database query (indexed): 5ms
- Schema validation: 2ms
- Deep merge algorithm: 1ms
- Event publishing: 5ms

### 6.2 Throughput Targets

| Operation                    | Target TPS | Peak TPS | Sustained TPS |
| ---------------------------- | ---------- | -------- | ------------- |
| resolve()                    | 20,000     | 50,000   | 15,000        |
| update()                     | 100        | 500      | 50            |
| watch() (concurrent streams) | 10,000     | 20,000   | 8,000         |

### 6.3 Resource Limits

**Per Instance**:

- CPU: 2 cores (request), 4 cores (limit)
- Memory: 4GB (request), 8GB (limit)
- Disk: 50GB (SSD for cache)
- Network: 1Gbps

**Horizontal Scaling**:

- Min replicas: 3
- Max replicas: 20
- Scale-up trigger: CPU > 70% or request queue > 1000
- Scale-down trigger: CPU < 30% for 10 minutes

### 6.4 SLOs & Alert Thresholds

| SLO                   | Target  | Warning Threshold | Critical Threshold |
| --------------------- | ------- | ----------------- | ------------------ |
| Availability          | 99.999% | 99.99%            | 99.9%              |
| P99 latency (resolve) | < 5ms   | > 10ms            | > 20ms             |
| Error rate            | < 0.01% | > 0.1%            | > 1%               |
| Cache hit rate        | > 95%   | < 90%             | < 80%              |
| Event delivery lag    | < 100ms | > 500ms           | > 2s               |

---

## 7. SECURITY DESIGN

### 7.1 AuthN/AuthZ Boundaries

**Authentication**:

- Admin API: OAuth 2.0 Bearer tokens (issued by K-01 IAM)
- Service-to-service: mTLS with service identity certificates
- gRPC: TLS 1.3 with mutual authentication

**Authorization**:

- Admin operations: RBAC with roles `config_admin`, `config_maker`, `config_checker`
- Read operations: Service identity verification (any authenticated service can read)
- Maker-checker: Enforced via K-03 Rules Engine policy

```yaml
# Example RBAC Policy (evaluated by K-03)
package config.authorization

default allow = false

allow {
  input.action == "read"
  input.service_identity != null
}

allow {
  input.action == "create_pack"
  input.user.roles[_] == "config_maker"
}

allow {
  input.action == "approve_pack"
  input.user.roles[_] == "config_checker"
  input.pack.maker_id != input.user.id  # Checker cannot be maker
}
```

### 7.2 Zero-Trust & mTLS

**Service Identity**:

- Each service issued unique X.509 certificate by internal CA
- Certificate CN format: `service-name.namespace.cluster.local`
- Certificate rotation: Every 30 days (automated)

**mTLS Enforcement**:

```yaml
# Istio/Envoy configuration
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: config-service-mtls
spec:
  selector:
    matchLabels:
      app: config-service
  mtls:
    mode: STRICT
```

### 7.3 Tenant Isolation Enforcement

**Row-Level Security**:

```sql
-- PostgreSQL RLS policy
ALTER TABLE config_packs ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON config_packs
  USING (
    type = 'GLOBAL' OR
    (type = 'TENANT' AND context_key = current_setting('app.tenant_id')) OR
    current_setting('app.service_role') = 'admin'
  );
```

**Application-Level Enforcement**:

```python
def resolve_config(tenant_id: str, schema_id: str, **kwargs):
    # Set tenant context for RLS
    db.execute("SET app.tenant_id = %s", [tenant_id])

    # All subsequent queries automatically filtered by RLS
    packs = query_config_packs(schema_id=schema_id, ...)

    # Reset context
    db.execute("RESET app.tenant_id")
```

### 7.4 Signing & Verification (Air-Gapped)

**Bundle Signing**:

```python
def sign_config_bundle(bundle_path: str, private_key_path: str) -> str:
    """
    Sign config bundle with Ed25519 private key.
    """
    with open(bundle_path, 'rb') as f:
        bundle_data = f.read()

    with open(private_key_path, 'rb') as f:
        private_key = Ed25519PrivateKey.from_private_bytes(f.read())

    signature = private_key.sign(bundle_data)

    # Append signature to bundle
    signed_bundle = bundle_data + b'\n---SIGNATURE---\n' + signature

    signed_path = bundle_path + '.signed'
    with open(signed_path, 'wb') as f:
        f.write(signed_bundle)

    return signed_path
```

**Bundle Verification**:

```python
def verify_config_bundle(signed_bundle_path: str, public_key_path: str) -> bool:
    """
    Verify config bundle signature.
    """
    with open(signed_bundle_path, 'rb') as f:
        signed_data = f.read()

    bundle_data, signature = signed_data.split(b'\n---SIGNATURE---\n')

    with open(public_key_path, 'rb') as f:
        public_key = Ed25519PublicKey.from_public_bytes(f.read())

    try:
        public_key.verify(signature, bundle_data)
        return True
    except InvalidSignature:
        return False
```

**Supply Chain Protection**:

- Config bundles stored in artifact registry with SHA-256 checksums
- All schema changes require code review + approval
- Automated vulnerability scanning of dependencies

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Required Metrics

```yaml
# Prometheus metrics
metrics:
  - name: config_resolve_latency_seconds
    type: histogram
    help: Configuration resolution latency
    labels: [schema_id, cache_hit, tenant_id]
    buckets: [0.001, 0.005, 0.01, 0.05, 0.1, 0.5]

  - name: config_cache_hit_rate
    type: gauge
    help: Cache hit rate percentage
    labels: [schema_id]

  - name: config_pack_activations_total
    type: counter
    help: Total config pack activations
    labels: [type, schema_id, status]

  - name: config_validation_errors_total
    type: counter
    help: Schema validation errors
    labels: [schema_id, error_type]

  - name: config_event_delivery_lag_seconds
    type: histogram
    help: Event delivery lag from publish to subscriber receipt
    labels: [event_type]
    buckets: [0.01, 0.05, 0.1, 0.5, 1, 5]

  - name: config_materialized_view_refresh_duration_seconds
    type: histogram
    help: Time to refresh materialized view
    labels: [schema_id]
```

### 8.2 Structured Logs

```json
{
  "timestamp": "2025-03-02T10:30:00.123Z",
  "level": "INFO",
  "service": "config-service",
  "correlation_id": "abc-123-def-456",
  "tenant_id": "tenant_789",
  "action": "CONFIG_RESOLVED",
  "schema_id": "fee_schedule",
  "cache_hit": true,
  "latency_ms": 1.2,
  "resolution_path": ["GLOBAL", "JURISDICTION", "TENANT"],
  "user_id": "user_456"
}
```

**Required Fields**:

- `timestamp`: ISO8601 with milliseconds
- `correlation_id`: Trace ID for distributed tracing
- `tenant_id`: For tenant-specific filtering
- `action`: Operation performed
- `latency_ms`: Operation duration

### 8.3 Distributed Traces

```yaml
# OpenTelemetry spans
spans:
  - name: ConfigClient.resolve
    attributes:
      schema_id: string
      tenant_id: string
      cache_hit: boolean
    events:
      - cache_lookup
      - grpc_call
      - schema_validation
      - deep_merge

  - name: ConfigService.ResolveConfig
    attributes:
      schema_id: string
      resolution_levels: int
    events:
      - query_database
      - build_resolution_path
      - validate_result

  - name: ConfigCommandHandler.UpdateConfig
    attributes:
      pack_id: string
      requires_approval: boolean
    events:
      - validate_schema
      - check_maker_checker
      - persist_pack
      - publish_event
```

### 8.4 Audit Events Schema

```json
{
  "audit_id": "uuid",
  "timestamp_gregorian": "2025-03-02T10:30:00Z",
  "timestamp_bs": "2081-11-17",
  "tenant_id": "tenant_789",
  "actor_id": "user_123",
  "action": "CONFIG_PACK_ACTIVATED",
  "resource_uri": "/config/packs/np_fee_schedule_v2",
  "before_state": {
    "status": "APPROVED"
  },
  "after_state": {
    "status": "ACTIVE",
    "activated_at": "2025-03-02T10:30:00Z"
  },
  "reason": "SEBON directive 2081/11/10",
  "correlation_id": "abc-123",
  "ip_address": "10.0.1.45",
  "user_agent": "ConfigCLI/1.0.0"
}
```

**Immutability**: Audit records written to K-07 Audit Framework (append-only, cryptographically chained)

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Extension Points

| Extension Point         | Type        | Purpose                                               | Example                           |
| ----------------------- | ----------- | ----------------------------------------------------- | --------------------------------- |
| Custom Resolution Logic | Plugin Hook | Override default hierarchy                            | Multi-region resolution           |
| Schema Validators       | Plugin Hook | Add custom validation rules                           | Business rule validation          |
| Cache Strategies        | Plugin Hook | Custom caching behavior                               | Redis vs in-memory                |
| Event Transformers      | Plugin Hook | Transform events before publish                       | Add custom metadata               |
| Value Catalog Providers | Plugin Hook | Supply dynamic option sets or bounded numeric domains | Jurisdiction-specific order types |

**Example: Custom Resolution Plugin**:

```python
class CustomResolutionPlugin:
    def resolve(self, context: ResolutionContext) -> Optional[dict]:
        """
        Called before default resolution. Return None to use default.
        """
        if context.schema_id == "multi_region_config":
            # Custom multi-region logic
            return self.resolve_multi_region(context)
        return None  # Use default resolution
```

### 9.2 Backward Compatibility Rules

**Schema Evolution**:

1. ✅ **Allowed**: Add optional fields
2. ✅ **Allowed**: Add new enum values (with default handling)
3. ✅ **Allowed**: Relax validation (e.g., increase max length)
4. ❌ **Prohibited**: Remove required fields
5. ❌ **Prohibited**: Change field types
6. ❌ **Prohibited**: Rename fields

**API Versioning**:

- REST API: `/api/v1/`, `/api/v2/` (version in path)
- gRPC: Package versioning (`siddhanta.config.v1`, `siddhanta.config.v2`)
- Minimum support: 2 major versions (v1 and v2 coexist)

### 9.3 Metadata-Driven Process and Value Governance

1. Workflow/task metadata may reference value catalogs by key and pinned version.
2. Active workflows must resolve against the version pinned at instance start unless an explicit migration policy says otherwise.
3. Value catalogs may add options or widen bounded ranges in a backward-compatible release.
4. Value catalogs must not remove or rename an active option while open instances, forms, or persisted domain records still reference it.
5. Human-task schemas and process-template parameters must support scope overlays (`GLOBAL`, `JURISDICTION`, `OPERATOR`, `TENANT`) with deterministic fallback.
6. All catalog activations and deprecations require maker-checker approval when they affect regulated workflows.

### 9.4 Metadata Compatibility Matrix

| Change Type                            | Example                                 | Compatibility Class | Allowed For Active Instances | Required Handling                                                               |
| -------------------------------------- | --------------------------------------- | ------------------- | ---------------------------- | ------------------------------------------------------------------------------- |
| Add optional field                     | Add `review_reason_code` to task schema | Additive            | Yes                          | Existing instances may continue on pinned version or upgrade at safe checkpoint |
| Add new catalog entry                  | Add `ESCALATE_TO_COMPLIANCE` option     | Additive            | Yes                          | Preserve existing ordering semantics; no removal of older codes                 |
| Widen numeric bounds                   | Increase `max_retry_count` from 3 to 5  | Additive            | Yes                          | Revalidate new submissions only                                                 |
| Change default route using same schema | New routing-policy target queue         | Migratable          | Only at declared checkpoint  | Require explicit migration policy and audit event                               |
| Remove existing field                  | Remove `review_notes`                   | Breaking            | No                           | Publish new version only; old active instances remain pinned                    |
| Rename catalog code                    | `EDD` -> `ENHANCED_DD`                  | Breaking            | No                           | Add new code, deprecate old code, migrate data first                            |
| Narrow allowed enum/list               | Remove `REJECT` from decision list      | Breaking            | No                           | Block activation until no active references remain                              |
| Change field type                      | `amount` number -> string               | Breaking            | No                           | Require new major version and migration tooling                                 |

### 9.5 Metadata Asset Lifecycle

```
Draft metadata asset created
  ↓
Schema validation + compatibility check
  ↓
Maker submits for approval
  ↓
Checker approves or rejects
  ↓
Approved asset waits for effective_date
  ↓
Asset becomes ACTIVE and emits change event
  ↓
Consumers resolve pinned version at workflow/task/query start or safe checkpoint
  ↓
Asset later marked DEPRECATED with replacement guidance
  ↓
Asset retired only after no active instances or retained records depend on it
```

### 9.6 Schema Evolution Policy

```python
def validate_schema_compatibility(old_schema: dict, new_schema: dict) -> ValidationResult:
    """
    Validate backward compatibility between schema versions.
    """
    errors = []

    # Check for removed required fields
    old_required = set(old_schema.get('required', []))
    new_required = set(new_schema.get('required', []))
    removed_required = old_required - new_required
    if removed_required:
        errors.append(f"Removed required fields: {removed_required}")

    # Check for type changes
    old_props = old_schema.get('properties', {})
    new_props = new_schema.get('properties', {})
    for field, old_def in old_props.items():
        if field in new_props:
            if old_def.get('type') != new_props[field].get('type'):
                errors.append(f"Changed type of field '{field}'")

    return ValidationResult(
        compatible=len(errors) == 0,
        errors=errors
    )
```

### 9.7 Deprecation Policy

**Timeline**:

1. **T+0**: Announce deprecation (release notes, API docs)
2. **T+90 days**: Add deprecation warnings in API responses
3. **T+180 days**: Mark as deprecated in schema registry
4. **T+365 days**: Remove from active use (keep in archive for audits)

**Deprecation Response Header**:

```http
HTTP/1.1 200 OK
Deprecation: true
Sunset: Sat, 01 Mar 2026 00:00:00 GMT
Link: </api/v2/config/packs>; rel="successor-version"
```

### 9.5 Region/Country Variability Injection

**T1 Config Packs** (Jurisdiction-Specific Data):

```json
{
  "pack_id": "np_trading_hours",
  "type": "JURISDICTION",
  "context_key": "NP",
  "schema_id": "trading_hours",
  "payload": {
    "market_open": "11:00",
    "market_close": "15:00",
    "timezone": "Asia/Kathmandu",
    "holidays": ["2081-01-01", "2081-04-14"]
  }
}
```

**T2 Rule Packs** (Jurisdiction-Specific Logic):

```rego
# Nepal-specific validation rules
package np.order_validation

min_lot_size := 10

valid_order_types := ["LIMIT", "MARKET"]

deny[msg] {
  input.order_type not in valid_order_types
  msg := sprintf("Order type %v not allowed in Nepal", [input.order_type])
}

deny[msg] {
  input.quantity % min_lot_size != 0
  msg := sprintf("Quantity must be multiple of %v", [min_lot_size])
}
```

**T3 Executable Packs** (Custom Adapters):

- Not applicable to Config Engine (consumed by other modules)

---

## 10. TEST PLAN (LLD-LEVEL)

### 10.1 Unit Tests

```python
class TestConfigResolution:
    def test_global_only_resolution(self):
        """Test resolution with only global config."""
        result = resolve_config(
            tenant_id="tenant_1",
            schema_id="fee_schedule"
        )
        assert result.config["brokerage_rate"] == 0.005  # Global default
        assert len(result.resolution_path) == 1
        assert result.resolution_path[0]["level"] == "GLOBAL"

    def test_jurisdiction_override(self):
        """Test jurisdiction config overrides global."""
        result = resolve_config(
            tenant_id="tenant_np_1",  # Nepal tenant
            schema_id="fee_schedule"
        )
        assert result.config["brokerage_rate"] == 0.004  # Nepal override
        assert len(result.resolution_path) == 2
        assert result.resolution_path[1]["level"] == "JURISDICTION"

    def test_deep_merge_behavior(self):
        """Test deep merge preserves nested structures."""
        global_config = {"fees": {"brokerage": 0.005, "exchange": 0.001}}
        tenant_config = {"fees": {"brokerage": 0.004}}

        result = deep_merge(global_config, tenant_config)

        assert result["fees"]["brokerage"] == 0.004  # Overridden
        assert result["fees"]["exchange"] == 0.001  # Preserved

    def test_schema_validation_failure(self):
        """Test invalid config rejected."""
        with pytest.raises(SchemaValidationError):
            validate_config(
                schema_id="fee_schedule",
                payload={"brokerage_rate": -0.01}  # Negative not allowed
            )

    def test_effective_date_filtering(self):
        """Test configs activated only after effective date."""
        future_date = datetime.utcnow() + timedelta(days=30)

        result = resolve_config(
            tenant_id="tenant_1",
            schema_id="fee_schedule",
            as_of_date=datetime.utcnow()
        )

        # Future config not included
        assert "future_field" not in result.config
```

### 10.2 Contract Tests

```yaml
# Pact contract test
interactions:
  - description: Resolve config for tenant
    request:
      method: POST
      path: /siddhanta.config.v1.ConfigService/ResolveConfig
      body:
        tenant_id: tenant_123
        schema_id: fee_schedule
    response:
      status: 200
      body:
        schema_id: fee_schedule
        resolved_config:
          brokerage_rate: 0.004
        resolution_path:
          - level: GLOBAL
          - level: JURISDICTION
        cache_ttl: 300
      matchingRules:
        body:
          $.resolved_config.brokerage_rate:
            match: type
          $.cache_ttl:
            match: integer
```

### 10.3 Integration Tests

```python
class TestConfigIntegration:
    @pytest.mark.integration
    def test_end_to_end_config_update(self):
        """Test complete config update flow with maker-checker."""
        # Step 1: Maker creates config
        response = admin_client.post("/api/v1/config/packs", json={
            "pack_id": "test_pack_1",
            "type": "TENANT",
            "context_key": "tenant_test",
            "schema_id": "fee_schedule",
            "payload": {"brokerage_rate": 0.003},
            "effective_date": {
                "bs": "2081-11-17",
                "gregorian": "2025-03-02T00:00:00Z"
            },
            "maker_id": "maker_1"
        })

        assert response.status_code == 202
        pack_id = response.json()["pack_id"]
        assert response.json()["status"] == "PENDING_APPROVAL"

        # Step 2: Checker approves
        response = admin_client.put(f"/api/v1/config/packs/{pack_id}/approve", json={
            "checker_id": "checker_1"
        })

        assert response.status_code == 200
        assert response.json()["status"] == "APPROVED"

        # Step 3: Wait for activation (or trigger manually)
        time.sleep(1)

        # Step 4: Verify config resolved correctly
        result = config_client.resolve(
            tenant_id="tenant_test",
            schema_id="fee_schedule"
        )

        assert result.config["brokerage_rate"] == 0.003

    @pytest.mark.integration
    def test_hot_reload_propagation(self):
        """Test hot reload propagates to all subscribers."""
        # Subscribe to config changes
        changes = []
        def on_change(event):
            changes.append(event)

        config_client.watch(["fee_schedule"], "tenant_test", on_change)

        # Activate new config
        activate_config_pack("test_pack_2")

        # Wait for event propagation
        time.sleep(0.5)

        # Verify event received
        assert len(changes) == 1
        assert changes[0].schema_id == "fee_schedule"
        assert changes[0].event_type == "ACTIVATED"
```

### 10.4 Replay Tests

```python
class TestEventReplay:
    def test_rebuild_cache_from_events(self):
        """Test materialized view can be rebuilt from events."""
        # Step 1: Clear materialized view
        db.execute("TRUNCATE resolved_config_cache")

        # Step 2: Replay all ConfigPackActivated events
        events = event_store.replay(
            event_type="ConfigPackActivatedEvent",
            from_offset=0
        )

        for event in events:
            handle_config_pack_activated(event)

        # Step 3: Verify cache rebuilt correctly
        cache_count = db.execute("SELECT COUNT(*) FROM resolved_config_cache").scalar()
        assert cache_count > 0

        # Step 4: Verify resolution still works
        result = config_client.resolve(
            tenant_id="tenant_1",
            schema_id="fee_schedule"
        )
        assert result.config is not None
```

### 10.5 Chaos/Failure Injection Tests

```python
class TestChaosEngineering:
    @pytest.mark.chaos
    def test_database_partition(self):
        """Test behavior during database partition."""
        # Inject network partition
        chaos.partition_network(target="postgres", duration=10)

        # Verify reads served from cache
        result = config_client.resolve(
            tenant_id="tenant_1",
            schema_id="fee_schedule"
        )
        assert result.config is not None  # Served from local cache

        # Verify writes rejected gracefully
        with pytest.raises(ServiceUnavailableError):
            admin_client.post("/api/v1/config/packs", json={...})

    @pytest.mark.chaos
    def test_event_bus_failure(self):
        """Test behavior when event bus unavailable."""
        # Stop Kafka
        chaos.stop_service("kafka")

        # Verify config updates queued locally
        response = admin_client.post("/api/v1/config/packs", json={...})
        assert response.status_code == 202

        # Restart Kafka
        chaos.start_service("kafka")

        # Verify queued events published
        time.sleep(2)
        events = event_store.query(event_type="ConfigPackActivatedEvent")
        assert len(events) > 0

    @pytest.mark.chaos
    def test_cache_invalidation_failure(self):
        """Test eventual consistency when cache invalidation fails."""
        # Inject failure in cache invalidation
        chaos.inject_fault(
            target="config_client.invalidate_cache",
            failure_rate=1.0,
            duration=5
        )

        # Activate new config
        activate_config_pack("test_pack_3")

        # Verify stale config served temporarily
        result = config_client.resolve(
            tenant_id="tenant_1",
            schema_id="fee_schedule"
        )
        # May be stale

        # Wait for background sync
        time.sleep(10)

        # Verify eventually consistent
        result = config_client.resolve(
            tenant_id="tenant_1",
            schema_id="fee_schedule"
        )
        assert result.config["version"] == "latest"
```

### 10.6 Security Tests

```python
class TestSecurity:
    def test_maker_cannot_approve_own_config(self):
        """Test maker-checker separation enforced."""
        # Maker creates config
        response = admin_client.post("/api/v1/config/packs", json={
            "maker_id": "user_123",
            ...
        })
        pack_id = response.json()["pack_id"]

        # Same user tries to approve
        response = admin_client.put(f"/api/v1/config/packs/{pack_id}/approve", json={
            "checker_id": "user_123"  # Same as maker
        })

        assert response.status_code == 403
        assert "MAKER_CHECKER_VIOLATION" in response.json()["error"]

    def test_tenant_isolation(self):
        """Test tenant cannot access other tenant's configs."""
        # Tenant A creates config
        response = admin_client.post("/api/v1/config/packs", json={
            "type": "TENANT",
            "context_key": "tenant_a",
            ...
        })

        # Tenant B tries to resolve
        result = config_client.resolve(
            tenant_id="tenant_b",
            schema_id="fee_schedule"
        )

        # Verify Tenant A's config not included
        assert "tenant_a_specific_field" not in result.config

    def test_unsigned_bundle_rejected(self):
        """Test unsigned config bundles rejected."""
        unsigned_bundle = create_config_bundle(sign=False)

        with pytest.raises(SignatureVerificationError):
            load_config_bundle(unsigned_bundle)

    def test_mtls_enforcement(self):
        """Test mTLS required for service-to-service calls."""
        # Call without client certificate
        with pytest.raises(TLSError):
            grpc_client.ResolveConfig(
                request,
                credentials=None  # No client cert
            )
```

---

## 11. VALIDATION QUESTIONS & ASSUMPTIONS

### Assumptions

1. **[ASSUMPTION]** Tenant-to-jurisdiction mapping is 1:1 (one tenant belongs to exactly one jurisdiction)
   - **Validation Question**: Can a multi-national operator have a single tenant spanning multiple jurisdictions?
   - **Impact**: May need multi-jurisdiction resolution logic

2. **[ASSUMPTION]** Config changes are infrequent (< 100 updates/day)
   - **Validation Question**: Are there use cases for high-frequency config updates?
   - **Impact**: May need different caching strategy

3. **[ASSUMPTION]** Materialized view refresh latency of 1-5 seconds is acceptable
   - **Validation Question**: Are there real-time config requirements?
   - **Impact**: May need streaming materialized views

4. **[ASSUMPTION]** Config payloads are < 1MB
   - **Validation Question**: Are there large config payloads (e.g., ML model configs)?
   - **Impact**: May need blob storage for large payloads

5. **[ASSUMPTION]** Dual-calendar conversion is handled by K-15 (not Config Engine)
   - **Validation Question**: Should Config Engine validate BS dates?
   - **Impact**: May need BS date validation logic

---

**END OF LLD: K-02 CONFIGURATION ENGINE**
