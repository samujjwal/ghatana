# LOW-LEVEL DESIGN: K-01 IDENTITY & ACCESS MANAGEMENT (IAM)

**Module**: K-01 Identity & Access Management  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

K-01 IAM provides **multi-tenant authentication, authorization, session management, and identity federation** for all human and machine actors across the Siddhanta platform.

**Core Responsibilities**:
- OAuth 2.0 / OpenID Connect authentication with JWT issuance
- Multi-Factor Authentication (TOTP, SMS, WebAuthn)
- Role-Based Access Control (RBAC) with fine-grained permissions
- Multi-tenant session management with tenant isolation
- KYC/AML status verification integration via pluggable adapters (T3)
- National ID verification via jurisdiction-specific providers (T3)
- SSO federation (SAML 2.0, OIDC)
- API key management for machine-to-machine (M2M) access
- Wallet-bound identity and DID (Decentralized Identifier) support for digital assets
- Dual-calendar timestamping (BS + Gregorian) on all identity events

**Invariants**:
1. Authentication tokens MUST be short-lived (access: 15min, refresh: 24h — configurable via K-02)
2. All permission checks MUST be evaluated per tenant context
3. Failed login attempts MUST trigger progressive lockout (5 attempts → 15min lock — configurable via K-02)
4. Session tokens MUST be invalidated on password change, role change, or tenant switch
5. All identity lifecycle events MUST be published to K-05 in standard envelope format
6. KYC status MUST be verified before granting trading permissions

### 1.2 Explicit Non-Goals

- ❌ KYC document verification logic (delegated to T3 plugins per jurisdiction)
- ❌ Sanctions screening (handled by D-14)
- ❌ User profile management UI (handled by K-13 Admin Portal)
- ❌ Password policy enforcement beyond standard NIST 800-63B (customized via T2 rules)

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Token expiry, lockout thresholds, MFA settings | K-02 stable |
| K-05 Event Bus | Auth event publishing (login, logout, role change) | K-05 stable |
| K-07 Audit Framework | Immutable audit trail for auth actions | K-07 stable |
| K-14 Secrets Management | JWT signing keys, OAuth client secrets | K-14 stable |
| K-15 Dual-Calendar | BS timestamp generation for auth events | K-15 stable |
| PostgreSQL | User, role, permission persistence | DB available |
| Redis | Session cache, rate-limit counters | Cache available |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/auth/login
Content-Type: application/json

Request:
{
  "username": "trader01",
  "password": "••••••••",
  "tenant_id": "tenant_np_1",
  "mfa_code": "123456"
}

Response 200:
{
  "access_token": "eyJhbGciOiJFZERTQSIs...",
  "refresh_token": "dGhpcyBpcyBhIHJlZnJl...",
  "token_type": "Bearer",
  "expires_in": 900,
  "user": {
    "user_id": "usr_abc123",
    "username": "trader01",
    "tenant_id": "tenant_np_1",
    "roles": ["TRADER", "VIEWER"],
    "kyc_status": "VERIFIED",
    "permissions": ["order:create", "order:view", "portfolio:view"]
  }
}

Response 401:
{
  "error": "AUTH_E001",
  "message": "Invalid credentials",
  "remaining_attempts": 3
}

Response 423:
{
  "error": "AUTH_E002",
  "message": "Account locked due to excessive failed attempts",
  "locked_until": "2025-03-02T10:45:00Z",
  "locked_until_bs": "2081-11-17 10:45:00"
}
```

```yaml
POST /api/v1/auth/refresh
Authorization: Bearer {refresh_token}

Response 200:
{
  "access_token": "eyJhbGciOiJFZERTQSIs...",
  "expires_in": 900
}
```

```yaml
POST /api/v1/auth/logout
Authorization: Bearer {access_token}

Response 204: (No content — session invalidated)
```

```yaml
GET /api/v1/users/{user_id}/permissions
Authorization: Bearer {service_token}

Response 200:
{
  "user_id": "usr_abc123",
  "tenant_id": "tenant_np_1",
  "effective_permissions": [
    { "resource": "order", "actions": ["create", "view", "cancel"], "scope": "own" },
    { "resource": "portfolio", "actions": ["view"], "scope": "own" },
    { "resource": "report", "actions": ["view", "generate"], "scope": "tenant" }
  ],
  "roles": ["TRADER", "VIEWER"],
  "kyc_status": "VERIFIED",
  "evaluated_at": "2025-03-02T10:30:00Z",
  "evaluated_at_bs": "2081-11-17"
}
```

```yaml
POST /api/v1/users
Authorization: Bearer {admin_token}
X-Maker-Checker: true

Request:
{
  "username": "trader02",
  "email": "trader02@example.com",
  "tenant_id": "tenant_np_1",
  "roles": ["TRADER"],
  "kyc_provider": "nepal_nid",
  "national_id": "NP-123456789"
}

Response 202:
{
  "user_id": "usr_def456",
  "status": "PENDING_APPROVAL",
  "approval_id": "appr_789",
  "message": "User creation requires maker-checker approval"
}
```

```yaml
POST /api/v1/api-keys
Authorization: Bearer {admin_token}

Request:
{
  "name": "oms-service",
  "tenant_id": "tenant_np_1",
  "permissions": ["event:publish", "event:subscribe"],
  "expires_in_days": 365
}

Response 201:
{
  "key_id": "ak_xyz789",
  "api_key": "sk_live_••••••••",
  "expires_at": "2026-03-02T00:00:00Z"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.iam.v1;

service IAMService {
  rpc Authenticate(AuthenticateRequest) returns (AuthenticateResponse);
  rpc ValidateToken(ValidateTokenRequest) returns (ValidateTokenResponse);
  rpc CheckPermission(CheckPermissionRequest) returns (CheckPermissionResponse);
  rpc GetUserRoles(GetUserRolesRequest) returns (GetUserRolesResponse);
  rpc InvalidateSession(InvalidateSessionRequest) returns (google.protobuf.Empty);
}

message AuthenticateRequest {
  string username = 1;
  string password = 2;
  string tenant_id = 3;
  string mfa_code = 4;
}

message AuthenticateResponse {
  string access_token = 1;
  string refresh_token = 2;
  int32 expires_in = 3;
  UserInfo user = 4;
}

message ValidateTokenRequest {
  string token = 1;
}

message ValidateTokenResponse {
  bool valid = 1;
  string user_id = 2;
  string tenant_id = 3;
  repeated string permissions = 4;
  int64 expires_at_epoch = 5;
}

message CheckPermissionRequest {
  string user_id = 1;
  string tenant_id = 2;
  string resource = 3;
  string action = 4;
  string resource_id = 5;  // optional — for resource-level checks
}

message CheckPermissionResponse {
  bool allowed = 1;
  string reason = 2;  // denial reason if not allowed
}
```

### 2.3 SDK Method Signatures

```typescript
interface IAMClient {
  /** Authenticate user, returns tokens */
  login(credentials: LoginCredentials): Promise<AuthResult>;

  /** Validate and decode JWT */
  validateToken(token: string): Promise<TokenClaims>;

  /** Check if user has permission */
  checkPermission(
    userId: string,
    tenantId: string,
    resource: string,
    action: string
  ): Promise<boolean>;

  /** Get effective permissions for user */
  getPermissions(userId: string, tenantId: string): Promise<Permission[]>;

  /** Invalidate all sessions for user */
  invalidateSessions(userId: string): Promise<void>;

  /** Register M2M API key */
  createApiKey(request: ApiKeyRequest): Promise<ApiKeyResult>;
}

interface LoginCredentials {
  username: string;
  password: string;
  tenantId: string;
  mfaCode?: string;
}

interface TokenClaims {
  userId: string;
  tenantId: string;
  roles: string[];
  permissions: string[];
  kycStatus: string;
  exp: number;
  iss: string;
}
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| AUTH_E001 | 401 | No | Invalid credentials |
| AUTH_E002 | 423 | No | Account locked (too many failures) |
| AUTH_E003 | 401 | No | Token expired |
| AUTH_E004 | 403 | No | Insufficient permissions |
| AUTH_E005 | 400 | No | Invalid MFA code |
| AUTH_E006 | 409 | No | Username already exists |
| AUTH_E007 | 412 | No | KYC verification required |
| AUTH_E008 | 500 | Yes | Identity provider unavailable |
| AUTH_E009 | 403 | No | Tenant mismatch |
| AUTH_E010 | 429 | Yes | Rate limit exceeded |

---

## 3. DATA MODEL

### 3.1 Core Tables

#### users

```sql
CREATE TABLE users (
  user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  username VARCHAR(255) NOT NULL,
  email VARCHAR(255),
  password_hash VARCHAR(512) NOT NULL,
  mfa_secret VARCHAR(255),
  mfa_enabled BOOLEAN NOT NULL DEFAULT false,
  kyc_status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
    CHECK (kyc_status IN ('PENDING', 'IN_PROGRESS', 'VERIFIED', 'REJECTED', 'EXPIRED')),
  account_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
    CHECK (account_status IN ('ACTIVE', 'LOCKED', 'SUSPENDED', 'DEACTIVATED')),
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMPTZ,
  locked_until_bs VARCHAR(30),
  last_login_at TIMESTAMPTZ,
  last_login_at_bs VARCHAR(30),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at_bs VARCHAR(30) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, username)
);

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY users_tenant_isolation ON users
  USING (tenant_id = current_setting('app.current_tenant')::uuid);

CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_kyc ON users(tenant_id, kyc_status);
```

#### roles

```sql
CREATE TABLE roles (
  role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  role_name VARCHAR(100) NOT NULL,
  description TEXT,
  is_system_role BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL,
  UNIQUE (tenant_id, role_name)
);

ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
CREATE POLICY roles_tenant_isolation ON roles
  USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

#### permissions

```sql
CREATE TABLE permissions (
  permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  resource VARCHAR(100) NOT NULL,
  action VARCHAR(50) NOT NULL,
  scope VARCHAR(50) NOT NULL DEFAULT 'own'
    CHECK (scope IN ('own', 'tenant', 'global')),
  description TEXT,
  UNIQUE (resource, action, scope)
);
```

#### role_permissions

```sql
CREATE TABLE role_permissions (
  role_id UUID NOT NULL REFERENCES roles(role_id),
  permission_id UUID NOT NULL REFERENCES permissions(permission_id),
  PRIMARY KEY (role_id, permission_id)
);
```

#### user_roles

```sql
CREATE TABLE user_roles (
  user_id UUID NOT NULL REFERENCES users(user_id),
  role_id UUID NOT NULL REFERENCES roles(role_id),
  granted_by UUID NOT NULL REFERENCES users(user_id),
  granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  granted_at_bs VARCHAR(30) NOT NULL,
  PRIMARY KEY (user_id, role_id)
);
```

#### sessions

```sql
CREATE TABLE sessions (
  session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(user_id),
  tenant_id UUID NOT NULL,
  refresh_token_hash VARCHAR(512) NOT NULL,
  ip_address INET,
  user_agent TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_sessions_user ON sessions(user_id, revoked);
CREATE INDEX idx_sessions_expiry ON sessions(expires_at) WHERE NOT revoked;
```

#### api_keys

```sql
CREATE TABLE api_keys (
  key_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  name VARCHAR(255) NOT NULL,
  key_hash VARCHAR(512) NOT NULL,
  permissions JSONB NOT NULL,
  created_by UUID NOT NULL REFERENCES users(user_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT false
);

ALTER TABLE api_keys ENABLE ROW LEVEL SECURITY;
CREATE POLICY api_keys_tenant_isolation ON api_keys
  USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

### 3.2 K-05 Event Schemas

```json
{
  "event_type": "UserAuthenticated",
  "event_version": "1.0.0",
  "aggregate_id": "usr_abc123",
  "aggregate_type": "User",
  "sequence_number": 42,
  "data": {
    "user_id": "usr_abc123",
    "tenant_id": "tenant_np_1",
    "login_method": "PASSWORD_MFA",
    "ip_address": "192.168.1.100",
    "result": "SUCCESS"
  },
  "metadata": {
    "trace_id": "tr_abc",
    "causation_id": "cmd_login_1",
    "correlation_id": "corr_session_1",
    "tenant_id": "tenant_np_1"
  },
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}
```

---

## 4. CONTROL FLOW

### 4.1 Login Flow

```
Client → POST /auth/login
  → AuthController.login()
    → RateLimiter.check(ip, username)  // K-02 threshold
      IF rate exceeded → 429
    → UserRepository.findByUsername(tenant, username)
      IF not found → 401 AUTH_E001
    → PasswordService.verify(hash, password)
      IF invalid → increment failed_count
        IF failed_count >= lockout_threshold → lock account, publish AccountLockedEvent
        → 401 AUTH_E001
    → IF user.mfa_enabled
        → MFAService.verify(user.mfa_secret, mfa_code)
          IF invalid → 401 AUTH_E005
    → KYCService.checkStatus(user_id)
      IF not VERIFIED && resource requires KYC → 412 AUTH_E007
    → TokenService.generateAccessToken(user, claims)
    → TokenService.generateRefreshToken(user)
    → SessionRepository.create(session)
    → EventBus.publish(UserAuthenticatedEvent)  // K-05 standard envelope
    → AuditService.log("USER_LOGIN", user_id)  // K-07
    → 200 { access_token, refresh_token, user }
```

### 4.2 Permission Check Flow (Hot Path — ≤1ms target)

```
Service → CheckPermission(userId, tenantId, resource, action)
  → PermissionCache.get(userId, tenantId)        // Redis, TTL=5min
    IF cache hit → evaluate permission → return
    IF cache miss →
      → RoleRepository.getEffectiveRoles(userId, tenantId)
      → PermissionRepository.getByRoles(roleIds)
      → PermissionCache.set(userId, tenantId, permissions, TTL=5min)
      → evaluate permission → return
```

### 4.3 Token Refresh Flow

```
Client → POST /auth/refresh (refresh_token in body)
  → TokenService.validateRefreshToken(token)
    IF expired → 401 AUTH_E003
  → SessionRepository.findByRefreshTokenHash(hash)
    IF not found or revoked → 401
  → TokenService.generateAccessToken(user, claims)
  → 200 { new_access_token }
```

### 4.4 User Creation (Maker-Checker)

```
Admin → POST /users (X-Maker-Checker: true)
  → UserService.createDraft(userData)
  → ApprovalRepository.create(PENDING_APPROVAL)
  → EventBus.publish(UserCreationRequestedEvent)
  → 202 { status: PENDING_APPROVAL }

Checker → POST /approvals/{id}/approve
  → ApprovalService.verify(checker != maker)
  → UserRepository.activate(userId)
  → KYCService.initiateVerification(userId, provider)  // T3 plugin
  → EventBus.publish(UserCreatedEvent)
  → AuditService.log("USER_CREATED", userId, approvedBy)
  → 200 { status: ACTIVE }
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Progressive Lockout Algorithm

```python
def handle_failed_login(user: User, config: K02Config):
    """Progressive lockout with configurable thresholds from K-02."""
    user.failed_login_count += 1
    
    thresholds = config.get("iam.lockout.thresholds")
    # Default: [{"attempts": 5, "duration_min": 15}, {"attempts": 10, "duration_min": 60}, {"attempts": 20, "duration_min": 1440}]
    
    for tier in reversed(thresholds):
        if user.failed_login_count >= tier["attempts"]:
            user.locked_until = now() + timedelta(minutes=tier["duration_min"])
            user.locked_until_bs = dual_calendar.to_bs(user.locked_until)
            event_bus.publish(AccountLockedEvent(
                user_id=user.user_id,
                tier=tier,
                timestamp_bs=dual_calendar.now_bs(),
                timestamp_gregorian=now()
            ))
            break
    
    user_repo.save(user)
```

### 5.2 JWT Token Structure

```json
{
  "header": {
    "alg": "EdDSA",
    "typ": "JWT",
    "kid": "key_2025_Q1"
  },
  "payload": {
    "sub": "usr_abc123",
    "iss": "siddhanta-iam",
    "aud": "siddhanta-platform",
    "exp": 1709376600,
    "iat": 1709375700,
    "tenant_id": "tenant_np_1",
    "roles": ["TRADER", "VIEWER"],
    "permissions": ["order:create", "order:view", "portfolio:view"],
    "kyc_status": "VERIFIED",
    "session_id": "sess_xyz"
  }
}
```

**Signing**: EdDSA (Ed25519) via K-14 managed keys. Key rotation every 90 days with 7-day overlap.

### 5.3 Permission Evaluation Algorithm

```python
def evaluate_permission(user_id, tenant_id, resource, action, resource_id=None):
    """
    Evaluate if user has permission for resource+action.
    Supports 3 scopes: own (user's resources only), tenant (all tenant resources), global (cross-tenant).
    """
    permissions = permission_cache.get(user_id, tenant_id)
    if not permissions:
        roles = role_repo.get_effective_roles(user_id, tenant_id)
        permissions = permission_repo.get_by_roles([r.role_id for r in roles])
        permission_cache.set(user_id, tenant_id, permissions, ttl=300)
    
    for perm in permissions:
        if perm.resource == resource and perm.action == action:
            if perm.scope == "global":
                return True
            if perm.scope == "tenant":
                return True  # tenant-wide access
            if perm.scope == "own" and resource_id:
                return resource_repo.is_owner(user_id, resource_id)
    return False
```

### 5.4 KYC Provider Routing (T3 Plugin)

```python
def route_kyc_verification(user, national_id, config: K02Config):
    """Route to jurisdiction-specific KYC provider via T3 plugin."""
    jurisdiction = config.get(f"tenant.{user.tenant_id}.jurisdiction")
    provider_name = config.get(f"kyc.provider.{jurisdiction}")
    # e.g., "nepal_nid" → Nepal National ID provider
    
    provider = plugin_runtime.load_t3(f"kyc-{provider_name}")  # K-04
    result = provider.verify(national_id)
    
    user.kyc_status = result.status  # VERIFIED | REJECTED | IN_PROGRESS
    event_bus.publish(KYCStatusChangedEvent(
        user_id=user.user_id,
        old_status="PENDING",
        new_status=result.status,
        provider=provider_name,
        timestamp_bs=dual_calendar.now_bs(),
        timestamp_gregorian=now()
    ))
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Budget Allocation |
|-----------|-----|-----|-----|-------------------|
| Login (password + MFA) | 80ms | 150ms | 300ms | DB lookup 20ms + bcrypt 60ms + MFA 30ms + token gen 5ms |
| Token validation (cached) | 0.5ms | 1ms | 2ms | JWT signature verify + claims decode |
| Permission check (cached) | 0.3ms | 0.8ms | 1ms | Redis lookup + evaluation |
| Permission check (cold) | 5ms | 10ms | 20ms | DB query + cache write + evaluation |
| Token refresh | 5ms | 15ms | 30ms | Session lookup + token gen |

### 6.2 Throughput Targets

| Metric | Target |
|--------|--------|
| Login requests | 2K/sec sustained |
| Token validations | 100K/sec (in-process JWT verify) |
| Permission checks | 50K/sec (cached path) |
| Concurrent active sessions | 500K per tenant |

### 6.3 Resource Limits

| Resource | Limit |
|----------|-------|
| Max roles per user | 20 |
| Max permissions per role | 200 |
| Max active sessions per user | 10 |
| Max API keys per tenant | 1000 |
| Session TTL | 24h (configurable via K-02) |
| Access token TTL | 15min (configurable via K-02) |

---

## 7. SECURITY DESIGN

### 7.1 Password Storage

- Algorithm: **bcrypt** with cost factor 12 (configurable via K-02)
- Salt: auto-generated per password (bcrypt built-in)
- No plaintext password storage or logging
- Password history: last 12 passwords stored (hashed) to prevent reuse

### 7.2 Token Security

- JWT signed with **Ed25519** (EdDSA) — keys managed by K-14
- Key rotation: every 90 days, 7-day overlap for graceful rollover
- Refresh tokens: opaque, stored as SHA-256 hash in DB
- Token revocation: session-based invalidation in Redis (O(1) lookup)

### 7.3 Tenant Isolation

- PostgreSQL Row-Level Security (RLS) on all user-facing tables
- `tenant_id` is a required claim in every JWT
- Cross-tenant token usage is rejected at gateway level (K-11)
- Session tokens are bound to a single tenant

### 7.4 Rate Limiting

- Login: 10 attempts/min per IP (configurable via K-02)
- Token refresh: 30/min per user
- API key creation: 5/hour per admin
- Rate limit state stored in Redis with TTL

### 7.5 Audit Trail

All IAM actions are logged to K-07 with:
- Action type (LOGIN, LOGOUT, ROLE_CHANGE, PERMISSION_CHECK, LOCKOUT, KYC_UPDATE)
- Actor (user_id, ip_address, user_agent)
- Target (affected user_id, resource)
- Result (SUCCESS, FAILURE, DENIED)
- Dual-calendar timestamps

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics (Prometheus)

```
# Authentication
siddhanta_iam_login_total{tenant, method, result}  counter
siddhanta_iam_login_duration_ms{tenant, method}    histogram
siddhanta_iam_active_sessions{tenant}              gauge
siddhanta_iam_locked_accounts{tenant}              gauge

# Token
siddhanta_iam_token_validation_total{result}       counter
siddhanta_iam_token_validation_duration_ms          histogram
siddhanta_iam_token_refresh_total{tenant, result}  counter

# Permissions
siddhanta_iam_permission_check_total{result, cached} counter
siddhanta_iam_permission_check_duration_ms{cached}   histogram
siddhanta_iam_permission_cache_hit_ratio{tenant}     gauge

# KYC
siddhanta_iam_kyc_verification_total{provider, result} counter
siddhanta_iam_kyc_verification_duration_ms{provider}   histogram
```

### 8.2 Structured Logs

```json
{
  "level": "INFO",
  "module": "K-01-IAM",
  "action": "USER_LOGIN",
  "user_id": "usr_abc123",
  "tenant_id": "tenant_np_1",
  "ip": "192.168.1.100",
  "method": "PASSWORD_MFA",
  "result": "SUCCESS",
  "duration_ms": 120,
  "trace_id": "tr_abc",
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}
```

### 8.3 Alerts

| Alert | Condition | Severity |
|-------|-----------|----------|
| High login failure rate | >20% failure rate over 5min | P2 |
| Mass lockout detected | >50 accounts locked in 10min | P1 |
| Token validation latency spike | P99 > 5ms over 5min | P2 |
| KYC provider unavailable | >3 consecutive failures | P2 |
| Permission cache hit ratio low | <80% over 15min | P3 |

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 KYC Provider Plugins (T3)

```typescript
interface KYCProviderPlugin {
  /** Plugin metadata */
  readonly metadata: {
    name: string;         // e.g., "nepal-nid-verifier"
    version: string;
    jurisdiction: string; // e.g., "NP", "IN", "BD"
  };

  /** Verify national ID — called by K-01 during onboarding */
  verify(nationalId: string, metadata: Record<string, unknown>): Promise<KYCResult>;

  /** Re-verify (for periodic re-KYC) */
  reverify(userId: string): Promise<KYCResult>;

  /** Health check */
  healthCheck(): Promise<boolean>;
}

interface KYCResult {
  status: 'VERIFIED' | 'REJECTED' | 'IN_PROGRESS' | 'ERROR';
  confidence: number;  // 0.0 - 1.0
  documentType: string;
  verifiedAt: Date;
  verifiedAtBs: string;
  metadata: Record<string, unknown>;
}
```

### 9.2 Custom Auth Provider (T3)

```typescript
interface AuthProviderPlugin {
  /** Authenticate via external provider (LDAP, AD, SAML) */
  authenticate(credentials: ExternalCredentials): Promise<AuthProviderResult>;

  /** Map external roles to Siddhanta roles */
  mapRoles(externalRoles: string[]): string[];
}
```

### 9.3 Password Policy Rules (T2)

```rego
package siddhanta.iam.password_policy

default allow = false

allow {
    input.password_length >= 12
    has_uppercase(input.password)
    has_lowercase(input.password)
    has_digit(input.password)
    not in_breach_list(input.password)
    not in_history(input.password, input.password_history)
}
```

### 9.4 DID / Wallet-Bound Identity (Future)

Extension point for decentralized identity:
- `DIDProviderPlugin` interface for DID resolution
- Wallet address ↔ user_id binding table
- WebAuthn passkey support as MFA factor

---

## 10. TEST PLAN

### 10.1 Unit Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| UT-IAM-001 | Password hashing | bcrypt hash verifies correctly |
| UT-IAM-002 | JWT generation | Token contains all required claims |
| UT-IAM-003 | JWT validation | Expired token rejected |
| UT-IAM-004 | Permission evaluation | Scope hierarchy enforced |
| UT-IAM-005 | Progressive lockout | Correct tier selected per attempt count |
| UT-IAM-006 | MFA verification | TOTP code validated within window |
| UT-IAM-007 | Rate limiter | Requests rejected after threshold |
| UT-IAM-008 | Tenant isolation | Cross-tenant query returns empty |

### 10.2 Integration Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| IT-IAM-001 | Full login flow | Token issued with correct claims |
| IT-IAM-002 | Login + lockout | Account locked after N failures |
| IT-IAM-003 | Token refresh cycle | New access token issued |
| IT-IAM-004 | Session invalidation on password change | Old tokens rejected |
| IT-IAM-005 | Maker-checker user creation | Approval required, user activated |
| IT-IAM-006 | KYC provider plugin (mock) | Status updated via T3 plugin |
| IT-IAM-007 | K-05 event publishing | Auth events published in standard envelope |
| IT-IAM-008 | K-07 audit logging | All actions audited immutably |

### 10.3 Security Tests

| Test | Description |
|------|-------------|
| ST-IAM-001 | SQL injection on login endpoint |
| ST-IAM-002 | Brute force protection (rate limit + lockout) |
| ST-IAM-003 | Token forgery detection (invalid signature) |
| ST-IAM-004 | Cross-tenant token replay |
| ST-IAM-005 | Privilege escalation via role manipulation |
| ST-IAM-006 | Session fixation attack |
| ST-IAM-007 | JWT algorithm confusion attack |

### 10.4 Chaos Tests

| Test | Description | Expected Behavior |
|------|-------------|-------------------|
| CT-IAM-001 | Redis cache failure | Falls back to DB for permission checks, degraded latency |
| CT-IAM-002 | K-14 Secrets unavailable | Token signing fails gracefully, existing tokens still validate |
| CT-IAM-003 | KYC provider timeout | User creation proceeds, KYC status stays PENDING |
| CT-IAM-004 | K-05 Event Bus down | Auth succeeds, events buffered in outbox for retry |
| CT-IAM-005 | DB primary failover | Read replica serves reads, writes fail-over within 30s |

---

**END OF K-01 IAM LLD**
