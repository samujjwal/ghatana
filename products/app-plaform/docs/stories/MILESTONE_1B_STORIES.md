# MILESTONE 1B — KERNEL SERVICES COMPLETION
## Sprints 3–4 | 147 Stories | K-01, K-14, K-16, K-17, K-18, K-03, K-04, K-06, K-11

> **Story Template**: ID, title, feature ref, points, sprint, team, description, Given/When/Then ACs, key tests, dependencies.

---

# EPIC K-01: IDENTITY & ACCESS MANAGEMENT (23 Stories)

## Feature K01-F01 — OAuth 2.0 + JWT Authentication with MFA (5 Stories)

---
### STORY-K01-001: Implement OAuth 2.0 token endpoint with client_credentials flow
**Feature**: K01-F01 · **Points**: 5 · **Sprint**: 3 · **Team**: Alpha

Implement POST `/auth/token` for client_credentials grant. Generate RS256-signed JWT with claims: sub, tenant_id, roles[], permissions[], iss, aud, iat, exp, iat_bs, jti. Integrate K-14 for signing key retrieval. Token lifetime configurable per tenant through K-02.

**ACs**:
1. Given valid client_id + client_secret, When POST /auth/token with grant_type=client_credentials, Then JWT returned with tenant-scoped claims
2. Given invalid credentials, When POST /auth/token, Then 401 Unauthorized with error description
3. Given JWT generated, When decoded, Then contains all required claims including iat_bs from K-15

**Tests**: auth_validCreds_returnsJwt · auth_invalidCreds_401 · auth_claims_complete · auth_rs256_verified · perf_5kAuthPerSec

**Dependencies**: K-14 (signing keys), K-15 (iat_bs), K-02 (tenant config)

---
### STORY-K01-002: Implement authorization_code flow with PKCE
**Feature**: K01-F01 · **Points**: 5 · **Sprint**: 3 · **Team**: Alpha

Implement OAuth 2.0 authorization_code grant with PKCE (S256). Redirect flow: GET /auth/authorize → consent → POST /auth/token with code + code_verifier. Anti-CSRF state parameter enforcement.

**ACs**:
1. Given valid authorize request with code_challenge, When user authenticates, Then authorization_code returned via redirect
2. Given authorization_code + valid code_verifier, When POST /auth/token, Then access + refresh tokens returned
3. Given invalid code_verifier (S256 mismatch), When POST /auth/token, Then 400 invalid_grant

**Tests**: authCode_validFlow_tokens · authCode_pkce_invalid_rejected · authCode_missingState_rejected · authCode_codeReuse_rejected

**Dependencies**: STORY-K01-001

---
### STORY-K01-003: Implement refresh token rotation with family tracking
**Feature**: K01-F01 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Implement refresh_token grant with family-based rotation. On each refresh: new access + refresh tokens issued, old refresh token invalidated. If reused refresh token detected → revoke entire family (compromise detection). Store refresh tokens in Redis with TTL.

**ACs**:
1. Given valid refresh_token, When POST /auth/token with grant_type=refresh_token, Then new access + refresh tokens; old refresh invalidated
2. Given already-used refresh_token (replay), When POST /auth/token, Then entire token family revoked
3. Given refresh_token expired, When used, Then 401 with token_expired error

**Tests**: refresh_valid_rotates · refresh_reuse_revokesFamily · refresh_expired_401 · refresh_concurrent_firstWins

**Dependencies**: STORY-K01-001, Redis

---
### STORY-K01-004: Implement MFA enrollment and TOTP verification
**Feature**: K01-F01 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Implement TOTP-based MFA (RFC 6238). POST /auth/mfa/enroll generates secret + QR URI. POST /auth/mfa/verify validates 6-digit TOTP code. Backup codes (10 single-use) generated at enrollment. Maker-checker roles require mandatory MFA.

**ACs**:
1. Given user enrolls MFA, When POST /auth/mfa/enroll, Then returns secret + QR URI + 10 backup codes
2. Given MFA-enabled user, When login without TOTP, Then returns 403 mfa_required challenge
3. Given valid TOTP code, When POST /auth/mfa/verify, Then authentication completes; JWT issued

**Tests**: mfa_enroll_generatesSecret · mfa_login_requiresTotp · mfa_validTotp_completes · mfa_backupCode_singleUse · mfa_invalidCode_rejected

**Dependencies**: STORY-K01-001

---
### STORY-K01-005: Implement brute force protection and account lockout
**Feature**: K01-F01 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Rate limit auth endpoints: 10 attempts/min per IP, 5 failed logins per account → 15-min lockout. Track failed attempts in Redis sliding window. Emit AccountLocked event. IP-based and account-based independent tracking.

**ACs**:
1. Given 5 failed login attempts for same account, When 6th attempt, Then 429 Too Many Requests, account locked 15 minutes
2. Given IP exceeds 10 attempts/min, When next attempt, Then 429 regardless of account
3. Given lockout period expires, When user logs in with valid creds, Then succeeds normally

**Tests**: lockout_5failures_locks · lockout_ipRateLimit · lockout_expires_unlocks · lockout_event_emitted · lockout_differentAccounts_independent

**Dependencies**: STORY-K01-001, K-05 (events), Redis

---

## Feature K01-F02 — Multi-Tenant Session Management (3 Stories)

---
### STORY-K01-006: Implement Redis-backed session store
**Feature**: K01-F02 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Create session management service storing sessions in Redis. Session entity: session_id, user_id, tenant_id, device_fingerprint, ip_address, created_at, last_active, expires_at. Key format: `session:{session_id}`. Configurable TTL per tenant via K-02 (default 30min idle, 8h absolute).

**ACs**:
1. Given successful authentication, When session created, Then stored in Redis with tenant-specific TTL
2. Given session activity, When request processed, Then last_active updated, idle timer reset
3. Given session idle exceeds tenant TTL, When next request, Then 401 session expired

**Tests**: session_create_storedInRedis · session_activity_refreshesTtl · session_idle_expires · session_tenantTtl_respected

**Dependencies**: STORY-K01-001, K-02 (tenant config), Redis

---
### STORY-K01-007: Implement concurrent session limits and eviction
**Feature**: K01-F02 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Enforce max concurrent sessions per user (configurable, default 5). When limit reached, oldest session evicted. Track active sessions per user using Redis sorted set (score = created_at). Session revocation API: revoke single, revoke all for user, revoke all for tenant.

**ACs**:
1. Given user has 5 active sessions, When 6th login, Then oldest session evicted, new session created
2. Given DELETE /sessions/{id}, When called by admin, Then specific session revoked
3. Given DELETE /users/{id}/sessions, When called, Then all sessions for user revoked

**Tests**: session_maxConcurrent_evictsOldest · session_revokeSingle · session_revokeAllUser · session_revokeAllTenant

**Dependencies**: STORY-K01-006

---
### STORY-K01-008: Implement cross-tenant session isolation
**Feature**: K01-F02 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Enforce strict tenant isolation — session for tenant A cannot access tenant B resources. Session middleware extracts tenant_id from JWT, validates against session's tenant_id. Cross-tenant access attempt logged as security event.

**ACs**:
1. Given session for tenant A, When request targets tenant B resource, Then 403 Forbidden
2. Given cross-tenant attempt, When detected, Then SecurityViolation event emitted to K-05
3. Given user belongs to multiple tenants, When switching, Then new session created for target tenant

**Tests**: session_crossTenant_blocked · session_securityEvent_emitted · session_multiTenant_separateSessions

**Dependencies**: STORY-K01-006, K-05

---

## Feature K01-F03 — RBAC + ABAC Authorization Engine (4 Stories)

---
### STORY-K01-009: Implement role-permission data model and management API
**Feature**: K01-F03 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Create role and permission data model in PostgreSQL. Permission format: `resource:action` (e.g., orders:create, ledger:read). Role→Permission many-to-many. REST API: CRUD for roles, assign/revoke permissions to roles, assign/revoke roles to users. All changes through maker-checker.

**ACs**:
1. Given new role "trader" with permissions [orders:create, orders:read], When created, Then role and permissions persisted
2. Given user assigned role "trader", When queried, Then returns aggregated permissions from all roles
3. Given permission change, When saved, Then RoleUpdated event emitted for cache invalidation

**Tests**: role_create_withPermissions · role_assignToUser · role_getPermissions_aggregated · role_event_onUpdate

**Dependencies**: K-05 (events), K-07 (audit)

---
### STORY-K01-010: Implement authorization middleware with Redis-cached permissions
**Feature**: K01-F03 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Create Express/Fastify middleware: `authorize(resource, action)`. Extracts user_id from JWT, looks up permissions from Redis cache (fallback to DB). Checks if user has matching permission. Cache invalidated on RoleUpdated events from K-05.

**ACs**:
1. Given user with "orders:create" permission, When POST /orders, Then authorized (200/201)
2. Given user without "orders:create" permission, When POST /orders, Then 403 Forbidden
3. Given role updated event, When received, Then cached permissions invalidated within 1 second

**Tests**: authz_hasPermission_allows · authz_missingPermission_403 · authz_cacheInvalidation · authz_cacheMiss_fallbackDb · perf_authzCheck_sub1ms

**Dependencies**: STORY-K01-009, K-05 (cache invalidation events), Redis

---
### STORY-K01-011: Integrate ABAC policy evaluation via K-03 rules engine
**Feature**: K01-F03 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Extend authorization middleware with ABAC layer. After RBAC check passes, invoke K-03 with request context (user attributes, resource attributes, environment) for fine-grained policy evaluation. Policies defined in OPA/Rego (e.g., "traders can only modify own department's orders").

**ACs**:
1. Given ABAC policy restricting to own department, When trader accesses other department, Then 403
2. Given no ABAC policy for resource, When RBAC passes, Then authorized (ABAC is additive restriction)
3. Given K-03 timeout, When ABAC evaluation fails, Then fallback to RBAC only with degraded flag logged

**Tests**: abac_departmentRestriction · abac_noPolicy_rbacSufficient · abac_k03Timeout_fallback · abac_multiplePolices_allMustPass

**Dependencies**: STORY-K01-010, K-03

---
### STORY-K01-012: Implement super-admin and break-glass access controls
**Feature**: K01-F03 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Implement super-admin role bypassing normal RBAC (for emergency scenarios). All super-admin actions double-audited. Break-glass access requires MFA + reason + time-limited elevation (max 4 hours). BreakGlassActivated event emitted.

**ACs**:
1. Given super-admin role, When accessing any resource, Then authorized with double-audit
2. Given break-glass request, When MFA verified + reason provided, Then temporary elevation granted (4h max)
3. Given break-glass expires, When next request, Then normal permissions apply

**Tests**: superAdmin_bypassRbac_audited · breakGlass_requiresMfa · breakGlass_expires · breakGlass_event_emitted

**Dependencies**: STORY-K01-004 (MFA), STORY-K01-010, K-07

---

## Feature K01-F04 — National ID Adapter Plugin Interface (2 Stories)

---
### STORY-K01-013: Define T3 national ID verification plugin interface
**Feature**: K01-F04 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Define the T3 plugin interface for national ID verification providers. Interface: `verifyNationalId(idNumber, idType, fullName, dob?) → {verified: boolean, details, confidence}`. Plugin manifest specifies supported ID types. Timeout and fallback configurable per provider.

**ACs**:
1. Given T3 plugin implementing NationalIdVerifier, When registered, Then available for client onboarding flows
2. Given plugin times out, When fallback configured, Then next provider attempted
3. Given all providers fail, When verification needed, Then manual verification workflow triggered

**Tests**: pluginInterface_registration · pluginInterface_timeout_fallback · pluginInterface_allFail_manualWorkflow

**Dependencies**: K-04 (plugin runtime)

---
### STORY-K01-014: Implement Nepal NID adapter (default T3 plugin)
**Feature**: K01-F04 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Implement reference T3 plugin for Nepal National ID verification. Connects to Nepal government API (mocked in dev). Maps NID number to person record. Caches verification results (TTL 24h). Handles offline/air-gap mode with signed verification bundles.

**ACs**:
1. Given valid Nepal NID, When verifyNationalId called, Then returns verified=true with person details
2. Given invalid NID, When called, Then returns verified=false with reason
3. Given air-gap mode, When offline verification bundle exists, Then verifies against local signed data

**Tests**: nepalNid_valid_verified · nepalNid_invalid_rejected · nepalNid_cached · nepalNid_airGap_offline · nepalNid_apiTimeout_fallback

**Dependencies**: STORY-K01-013, K-10 (air-gap mode)

---

## Feature K01-F05 — Service-to-Service Identity (2 Stories)

---
### STORY-K01-015: Implement mTLS service identity provisioning
**Feature**: K01-F05 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Implement service identity using mTLS certificates. Each microservice gets a unique X.509 certificate from K-14. Service mesh (Istio) enforces mTLS for all inter-service communication. Certificate CN encodes service name and tenant context.

**ACs**:
1. Given service starts, When certificate provisioned from K-14, Then mTLS enabled with unique identity
2. Given service A calls service B, When mTLS handshake, Then mutual authentication verified
3. Given expired certificate, When renewal triggered, Then zero-downtime rotation

**Tests**: mtls_provision_certificate · mtls_mutualAuth_success · mtls_expiredCert_renewal · mtls_invalidCert_rejected

**Dependencies**: K-14 (certificate management), Istio service mesh

---
### STORY-K01-016: Implement service-to-service JWT propagation
**Feature**: K01-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Implement JWT token propagation for service-to-service calls. Incoming user JWT forwarded in internal calls (preserving actor context). Service-initiated calls use service-level JWT (no user context). Header: `X-Forwarded-Authorization` for user JWT, `Authorization` for service JWT.

**ACs**:
1. Given user request to service A, When A calls B, Then user JWT forwarded B can extract original actor
2. Given background job (no user context), When service calls another, Then service JWT used
3. Given both headers present, When authorization evaluated, Then user JWT takes precedence for authorization, service JWT for authentication

**Tests**: jwtPropagation_userContext_maintained · jwtPropagation_serviceOnly · jwtPropagation_precedence

**Dependencies**: STORY-K01-001, STORY-K01-015

---

## Feature K01-F06 — Audit Logging Integration (2 Stories)

---
### STORY-K01-017: Integrate IAM events with audit framework
**Feature**: K01-F06 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Emit audit events for all IAM operations: login, logout, token refresh, MFA enroll/verify, role changes, session revocation, permission changes. Use K-07 audit SDK. Include actor_id, action, resource, IP, user-agent, result.

**ACs**:
1. Given successful login, When completed, Then LoginSucceeded audit event emitted with actor, IP, user-agent
2. Given failed login, When detected, Then LoginFailed audit event emitted with attempted username, IP
3. Given role change, When processed, Then RoleUpdated audit event with old/new values

**Tests**: audit_loginSuccess · audit_loginFail · audit_roleChange · audit_sessionRevoke · audit_mfaEnroll

**Dependencies**: K-07 (audit SDK)

---
### STORY-K01-018: Implement IAM audit query API
**Feature**: K01-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Implement read API for IAM audit events. GET /audit/iam with filters: user_id, action, date_range (BS + Gregorian), result. Paginated results. Integrates with K-07's Elasticsearch-backed audit search.

**ACs**:
1. Given filter by user_id, When queried, Then returns all IAM actions for that user
2. Given date range filter, When queried, Then returns events within range (supports BS dates)
3. Given query with pagination (limit=50, offset=100), When called, Then correct page returned

**Tests**: auditQuery_byUser · auditQuery_byDateRange · auditQuery_pagination · auditQuery_bsDateFilter

**Dependencies**: STORY-K01-017, K-07

---

## Feature K01-F07 — Beneficial Ownership Graph (3 Stories)

---
### STORY-K01-019: Implement beneficial ownership data model
**Feature**: K01-F07 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Create graph data model for beneficial ownership tracking. Entities: Person, LegalEntity, Account. Relationships: owns_percentage, controls, beneficiary_of. Store in PostgreSQL with recursive CTE support for traversal. Temporal: ownership valid_from/valid_to.

**ACs**:
1. Given person A owns 60% of Entity B which owns 40% of Entity C, When graph queried, Then A has 24% indirect ownership of C
2. Given ownership change, When updated, Then previous record closed (valid_to set), new record created
3. Given point-in-time query, When as_of date provided, Then returns ownership as of that date

**Tests**: ownership_direct · ownership_indirect_calculation · ownership_temporal · ownership_circular_detection · ownership_threshold_alert

**Dependencies**: K-05 (events)

---
### STORY-K01-020: Implement beneficial ownership traversal API
**Feature**: K01-F07 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Create REST API: GET /entities/{id}/beneficial-owners traverses ownership graph using recursive CTE. Returns all beneficial owners above configurable threshold (default 10%). Supports max_depth parameter. Used by D-07 compliance for regulatory reporting.

**ACs**:
1. Given complex ownership chain (depth 5), When traversed, Then all beneficial owners above threshold returned
2. Given circular ownership detected, When traversal runs, Then cycle detected, logged, and broken gracefully
3. Given threshold=25%, When queried, Then only owners ≥25% returned

**Tests**: traversal_deepChain · traversal_circularDetection · traversal_threshold · traversal_pointInTime · perf_1000entities_sub200ms

**Dependencies**: STORY-K01-019

---
### STORY-K01-021: Implement ownership change notification and compliance triggers
**Feature**: K01-F07 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

When ownership changes cause beneficial owner to cross regulatory thresholds (5%, 10%, 25%, 50%, 75%), emit OwnershipThresholdCrossed event. D-07 compliance subscribes for reporting. Changes require maker-checker approval.

**ACs**:
1. Given ownership increases from 9% to 11%, When threshold 10% crossed, Then OwnershipThresholdCrossed event emitted
2. Given ownership change request, When submitted, Then requires maker-checker approval before effective
3. Given threshold crossing, When notified, Then compliance team alerted via notification

**Tests**: threshold_crossing_event · threshold_makerChecker · threshold_multipleThresholds · notification_sent

**Dependencies**: STORY-K01-019, K-05, D-07

---

## Feature K01-F08 — Rate Limiting & Anomaly Detection (2 Stories)

---
### STORY-K01-022: Implement auth endpoint rate limiting
**Feature**: K01-F08 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Implement token-bucket rate limiting on all auth endpoints. Limits: per IP (10/min), per tenant (100/min), global (1000/min). Use Redis MULTI/EXEC for atomic counter updates. Return Retry-After header on 429 responses.

**ACs**:
1. Given IP exceeds 10 requests/min, When next request, Then 429 with Retry-After header
2. Given tenant exceeds 100/min, When next request, Then 429 for all users of that tenant
3. Given rate limit counters, When minute window passes, Then counters reset

**Tests**: rateLimit_perIp · rateLimit_perTenant · rateLimit_global · rateLimit_retryAfter · rateLimit_windowReset

**Dependencies**: K-11 (API Gateway), Redis

---
### STORY-K01-023: Implement login anomaly detection
**Feature**: K01-F08 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Detect suspicious login patterns: login from new device/IP, impossible travel (login from two distant locations within short time), unusual time-of-day patterns. Emit SecurityAnomaly event. Configurable: alert-only or block + step-up MFA.

**ACs**:
1. Given login from new IP/device not seen before, When detected, Then SecurityAnomaly event emitted, step-up MFA required
2. Given login from Kathmandu then London within 30 minutes, When detected, Then impossible travel flagged
3. Given anomaly configuration set to "alert-only", When anomaly detected, Then event emitted but login allowed

**Tests**: anomaly_newDevice_stepUp · anomaly_impossibleTravel · anomaly_alertOnly_mode · anomaly_normalLogin_noAlert

**Dependencies**: STORY-K01-001, K-05 (events)

---

# EPIC K-14: SECRETS MANAGEMENT (14 Stories)

## Feature K14-F01 — Multi-Provider Vault Abstraction (3 Stories)

---
### STORY-K14-001: Define SecretProvider interface and provider registry
**Feature**: K14-F01 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Define `SecretProvider` interface: `getSecret(path): SecretValue`, `putSecret(path, value, metadata)`, `deleteSecret(path)`, `listSecrets(prefix): string[]`, `rotateSecret(path): SecretValue`. Provider registry selects implementation based on K-02 environment config. Providers registered at startup.

**ACs**:
1. Given provider configured as "hashicorp-vault", When getSecret called, Then delegates to Vault provider
2. Given provider configured as "local-file", When getSecret called, Then delegates to local encrypted file provider
3. Given unknown provider name, When startup, Then fails fast with clear error

**Tests**: registry_selectsVault · registry_selectsLocal · registry_unknownProvider_failsFast · interface_allMethods_implemented

**Dependencies**: K-02 (config)

---
### STORY-K14-002: Implement HashiCorp Vault KV v2 provider
**Feature**: K14-F01 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Implement Vault provider using KV v2 secrets engine. Token-based auth (AppRole for services). Secret caching with configurable TTL (default 5 minutes). Lease renewal before expiry. Connection pooling.

**ACs**:
1. Given Vault configured, When getSecret("db/password"), Then returns current secret from KV v2
2. Given cached secret within TTL, When getSecret called, Then returns cached value (no Vault round-trip)
3. Given Vault token expiring, When renewal triggered, Then new token acquired transparently

**Tests**: vault_getSecret_success · vault_cache_hit · vault_cache_expired_refresh · vault_tokenRenewal · vault_unavailable_cachedFallback

**Dependencies**: STORY-K14-001, HashiCorp Vault

---
### STORY-K14-003: Implement local encrypted file provider (air-gap)
**Feature**: K14-F01 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Implement local file-based secret provider for air-gap deployments. Secrets stored in AES-256-GCM encrypted files. Master key derived from passphrase via Argon2id. File format: JSON with nonce, ciphertext, tag per secret. Ed25519 signed bundle for integrity.

**ACs**:
1. Given encrypted secret file, When getSecret called, Then decrypts and returns plaintext
2. Given tampered file (signature mismatch), When loaded, Then rejects with IntegrityError
3. Given new secret, When putSecret called, Then encrypted and appended to file with updated signature

**Tests**: localFile_decrypt_success · localFile_tampered_rejected · localFile_encrypt_stores · localFile_masterKey_argon2

**Dependencies**: STORY-K14-001

---

## Feature K14-F02 — Automatic Secret Rotation (3 Stories)

---
### STORY-K14-004: Implement secret rotation scheduler
**Feature**: K14-F02 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Create rotation scheduler that auto-rotates secrets based on configurable policies (per-secret max_age). Rotation creates new version, keeps previous version active for grace period. Cron-based schedule (default: check every hour). SecretRotated event emitted.

**ACs**:
1. Given secret with max_age=30d, When age exceeds 30d, Then rotation triggered automatically
2. Given rotation, When new version created, Then old version remains active for grace period (default 24h)
3. Given rotation completed, When SecretRotated event emitted, Then consumers can refresh cached secrets

**Tests**: rotation_ageExceeded_triggers · rotation_gracePeroid · rotation_event_emitted · rotation_manualTrigger

**Dependencies**: STORY-K14-001, K-05 (events)

---
### STORY-K14-005: Implement database credential rotation
**Feature**: K14-F02 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Implement zero-downtime PostgreSQL credential rotation. Two-phase: create new credentials → update applications → revoke old. Uses Vault dynamic database credentials or manual rotation for local provider. Connection pool drain before revoke.

**ACs**:
1. Given DB credential rotation triggered, When new credentials created, Then both old and new work during grace period
2. Given grace period expired, When old credentials revoked, Then connections using old creds fail gracefully, new creds used
3. Given rotation failure, When new creds can't be created, Then old creds remain active, alert emitted

**Tests**: dbRotation_zeroDowntime · dbRotation_graceRevoke · dbRotation_failure_noDisruption · dbRotation_connectionPool_drain

**Dependencies**: STORY-K14-004

---
### STORY-K14-006: Implement API key and JWT signing key rotation
**Feature**: K14-F02 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Implement rotation for API keys and JWT RS256 signing keys. JWT rotation: new kid activated, old kid valid for 24h grace. JWKS endpoint updated immediately. API key rotation: new key active, old key valid for grace period.

**ACs**:
1. Given JWT signing key rotated, When new kid activated, Then JWKS endpoint returns both old and new keys
2. Given token signed with old kid (within grace), When validated, Then accepted
3. Given token signed with old kid (past grace), When validated, Then rejected

**Tests**: jwtRotation_jwksUpdated · jwtRotation_oldKid_graceAccepted · jwtRotation_oldKid_expiredRejected · apiKeyRotation

**Dependencies**: STORY-K14-004, STORY-K01-001

---

## Feature K14-F03 — Certificate Lifecycle Management (2 Stories)

---
### STORY-K14-007: Implement X.509 certificate issuance and tracking
**Feature**: K14-F03 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Implement certificate lifecycle: generate CSR, issue via internal CA (or external CA integration), track expiry, auto-renewal. Certificate inventory: subject, issuer, serial, not_before, not_after, key_usage. Alerts at 30d, 14d, 7d before expiry.

**ACs**:
1. Given CSR generated for service, When submitted to CA, Then certificate issued and stored
2. Given certificate approaching expiry (30d), When checked, Then CertificateExpiringAlert emitted
3. Given auto-renewal enabled, When 14d before expiry, Then renewal initiated automatically

**Tests**: cert_issue_success · cert_expiryAlert_30d · cert_autoRenewal · cert_inventory_tracked · cert_revocation

**Dependencies**: STORY-K14-001

---
### STORY-K14-008: Implement certificate distribution to services
**Feature**: K14-F03 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Distribute issued certificates to consuming services via K-05 events or pull-based API. Services poll or subscribe for certificate updates. Hot-reload without restart. Certificate pinning validation for critical connections.

**ACs**:
1. Given new certificate issued for service A, When CertificateIssued event fired, Then service A receives and loads new cert
2. Given certificate hot-reload, When new cert loaded, Then no connection drops (graceful swap)
3. Given certificate pinning configured, When unexpected cert received, Then rejected with alert

**Tests**: cert_distribution_event · cert_hotReload_noDowntime · cert_pinning_validation · cert_pullBased_api

**Dependencies**: STORY-K14-007, K-05

---

## Feature K14-F04 — Break-Glass Access (2 Stories)

---
### STORY-K14-009: Implement break-glass secret access workflow
**Feature**: K14-F04 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Implement emergency "break-glass" access to secrets. Requires: MFA verification + reason text + approval from second admin (or bypass with dual-MFA in urgent scenarios). Time-limited elevation (max 4 hours). Full audit trail. BreakGlassAccess event.

**ACs**:
1. Given break-glass request with MFA + reason, When approved, Then temporary access granted (4h max)
2. Given break-glass without MFA, When attempted, Then rejected
3. Given break-glass access, When any secret read, Then doubly-audited (standard + break-glass log)

**Tests**: breakGlass_mfaRequired · breakGlass_timeLimit · breakGlass_auditTrail · breakGlass_noApproval_rejected

**Dependencies**: STORY-K01-004 (MFA), K-07 (audit)

---
### STORY-K14-010: Implement break-glass forensic reporting
**Feature**: K14-F04 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Generate forensic reports for all break-glass access events. Report includes: who accessed, when, which secrets, reason provided, approval chain, duration, actions taken during elevation. Auto-generated after elevation expires.

**ACs**:
1. Given break-glass session ended, When report generated, Then includes all accessed secrets and actions
2. Given report, When reviewed by security team, Then marked as reviewed with reviewer's notes
3. Given monthly audit, When break-glass report generated, Then aggregates all incidents for period

**Tests**: forensic_report_generated · forensic_review_tracked · forensic_monthly_aggregate

**Dependencies**: STORY-K14-009, K-07

---

## Feature K14-F05 — HSM Integration (2 Stories)

---
### STORY-K14-011: Implement HSM key operations abstraction
**Feature**: K14-F05 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Integrate Hardware Security Module (HSM) via PKCS#11 interface. Support: key generation (RSA, ECDSA), signing, verification, encryption/decryption. Keys never leave HSM boundary. Fallback to software keys when HSM unavailable (configurable).

**ACs**:
1. Given HSM configured, When sign operation requested, Then operation performed inside HSM, key never exported
2. Given HSM unavailable, When fallback enabled, Then software key used with degradation warning
3. Given HSM unavailable, When fallback disabled, Then operation fails with HSM_UNAVAILABLE error

**Tests**: hsm_sign_insideHsm · hsm_keyNeverExported · hsm_fallback_software · hsm_fallback_disabled_error

**Dependencies**: STORY-K14-001, HSM hardware/emulator

---
### STORY-K14-012: Implement HSM-backed JWT signing
**Feature**: K14-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Route JWT RS256 signing through HSM when available. JWKS endpoint serves public key from HSM. Signing operation: hash JWT header+payload → send hash to HSM → HSM signs → return signature. Benchmark: target < 5ms per signing operation.

**ACs**:
1. Given HSM available, When JWT generated, Then RS256 signature produced by HSM
2. Given JWKS endpoint queried, When HSM public key available, Then served with HSM key ID
3. Given signing latency, When measured, Then < 5ms P99

**Tests**: hsm_jwtSign · hsm_jwksPublicKey · hsm_signLatency_sub5ms · hsm_fallback_softwareSign

**Dependencies**: STORY-K14-011, STORY-K01-001

---

## Feature K14-F06 — Secret Versioning & Rollback (2 Stories)

---
### STORY-K14-013: Implement secret version history
**Feature**: K14-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Store all secret versions with metadata (version, created_at, created_by, rotation_type: manual|auto). GET /secrets/{path}?version=N returns specific version. Default returns latest. Max versions retained configurable (default 10).

**ACs**:
1. Given secret updated 5 times, When GET /secrets/path?version=3, Then returns version 3
2. Given GET /secrets/path (no version), When called, Then returns latest version
3. Given max_versions=10 exceeded, When 11th version created, Then oldest version purged

**Tests**: version_getSpecific · version_getLatest · version_maxPurge · version_metadata_tracked

**Dependencies**: STORY-K14-001

---
### STORY-K14-014: Implement secret rollback
**Feature**: K14-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Implement rollback to previous secret version. POST /secrets/{path}/rollback?version=N creates new version with old version's value. Triggers same rotation notification flow. Rollback requires maker-checker approval.

**ACs**:
1. Given current version 5 has bad value, When rollback to version 3, Then new version 6 created with version 3's value
2. Given rollback request, When submitted, Then requires maker-checker approval
3. Given rollback completed, When SecretRotated event emitted, Then consumers refresh

**Tests**: rollback_createsNewVersion · rollback_makerChecker · rollback_event · rollback_invalidVersion_rejected

**Dependencies**: STORY-K14-013, K-05

---

# EPIC K-16: LEDGER FRAMEWORK (19 Stories)

## Feature K16-F01 — Double-Entry Posting Engine (4 Stories)

---
### STORY-K16-001: Create ledger schema with append-only enforcement
**Feature**: K16-F01 · **Points**: 5 · **Sprint**: 3 · **Team**: Alpha

Create PostgreSQL ledger tables: `journal` (journal_id, reference, description, posted_at_utc, posted_at_bs, fiscal_year), `journal_entry` (entry_id, journal_id FK, account_id FK, direction ENUM(DEBIT,CREDIT), amount DECIMAL(28,12), currency, metadata JSONB). REVOKE UPDATE, DELETE on both tables. Partitioned by fiscal_year.

**ACs**:
1. Given migration runs, When tables created, Then UPDATE/DELETE revoked on application role
2. Given journal_entry, When amount stored, Then DECIMAL(28,12) preserves precision for all currency types
3. Given partition by fiscal_year, When queried for specific year, Then partition pruning applied

**Tests**: schema_createsTables · schema_updateBlocked · schema_deleteBlocked · schema_precision_28_12 · schema_partitioned

**Dependencies**: K-15 (fiscal year), PostgreSQL

---
### STORY-K16-002: Implement journal creation with balance enforcement
**Feature**: K16-F01 · **Points**: 5 · **Sprint**: 3 · **Team**: Alpha

Implement POST `/ledger/journals` that creates a journal with entries[]. Enforce: sum(DEBIT amounts) == sum(CREDIT amounts) per currency within journal. Reject unbalanced journals. Multi-leg journals supported (3+ entries). JournalPosted event emitted.

**ACs**:
1. Given balanced journal (debit=5000, credit=5000 NPR), When posted, Then journal persisted, JournalPosted event emitted
2. Given unbalanced journal (debit=5000, credit=4999.99), When posted, Then 422 UNBALANCED_JOURNAL error
3. Given multi-leg journal (A→B 3000, A→C 2000), When posted, Then balanced (debits=5000, credits=5000)

**Tests**: journal_balanced_created · journal_unbalanced_rejected · journal_multiLeg · journal_multiCurrency_eachBalances · journal_event_emitted · perf_20kPostingsPerSec

**Dependencies**: STORY-K16-001, K-05 (events), K-15 (timestamps)

---
### STORY-K16-003: Implement account balance materialization
**Feature**: K16-F01 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Create materialized account balance views updated on each journal posting. Balance = sum(DEBIT) - sum(CREDIT) for asset/expense accounts; sum(CREDIT) - sum(DEBIT) for liability/equity/revenue. Trigger-based or event-driven refresh. Balance per currency.

**ACs**:
1. Given 100 journal entries for account, When balance queried, Then returns correct balance per currency
2. Given new journal posted, When balance refreshed, Then reflects new posting within 100ms
3. Given concurrent postings to same account, When balance computed, Then consistent (no race condition)

**Tests**: balance_100entries_correct · balance_refresh_afterPosting · balance_concurrent_consistent · balance_multiCurrency · perf_balanceQuery_sub5ms

**Dependencies**: STORY-K16-002

---
### STORY-K16-004: Implement journal reversal (correction entries)
**Feature**: K16-F01 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Implement journal reversal as new journal with swapped debits/credits. POST `/ledger/journals/{id}/reverse` creates contra-entry. Original journal unchanged (append-only). Reversal references original via reversal_of_journal_id. Requires maker-checker for reversals.

**ACs**:
1. Given original journal (debit A 5000, credit B 5000), When reversed, Then new journal (credit A 5000, debit B 5000) created
2. Given reversed journal, When original queried, Then shows reversal_reference
3. Given reversal request, When submitted, Then requires maker-checker approval

**Tests**: reversal_createsContra · reversal_reference_linked · reversal_makerChecker · reversal_doubleReversal_prevented · reversal_balance_netZero

**Dependencies**: STORY-K16-002, K-07 (audit)

---

## Feature K16-F02 — Append-Only Storage (2 Stories)

---
### STORY-K16-005: Implement storage-level immutability verification
**Feature**: K16-F02 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Create verification utility that confirms UPDATE/DELETE restrictions on ledger tables. Scheduled daily verification job attempts prohibited operations (on test row) and confirms rejection. Generates ImmutabilityVerified event. Alert if verification fails.

**ACs**:
1. Given daily verification job, When UPDATE attempted on ledger, Then rejected; ImmutabilityVerified event emitted
2. Given DELETE attempted on ledger, When executed, Then rejected; verification passes
3. Given verification fails (someone granted UPDATE), When detected, Then CRITICAL alert emitted

**Tests**: verify_updateBlocked · verify_deleteBlocked · verify_alertOnFailure · verify_scheduledDaily

**Dependencies**: STORY-K16-001

---
### STORY-K16-006: Implement ledger entry hash chain for tamper detection
**Feature**: K16-F02 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Add hash chain to journal entries: each entry's hash includes previous entry's hash (per account). Uses SHA-256. Periodic chain validation job. Tamper detection breaks chain, triggers SecurityViolation alert.

**ACs**:
1. Given journal entries for account, When each stored, Then entry_hash = SHA-256(previous_hash + amount + direction + account_id)
2. Given chain validation runs, When all hashes valid, Then LedgerChainValid event emitted
3. Given tampered entry, When chain validation runs, Then chain break detected at specific entry, alert emitted

**Tests**: hash_chain_construction · hash_validation_valid · hash_validation_tampered_detected · hash_genesis_entry

**Dependencies**: STORY-K16-002

---

## Feature K16-F03 — Temporal Balance Queries & Snapshots (3 Stories)

---
### STORY-K16-007: Implement as-of-date balance query
**Feature**: K16-F03 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Implement GET `/ledger/accounts/{id}/balance?as_of=2082-06-15` returning account balance as of specified date (supports both BS and Gregorian dates via K-15 conversion). Computes balance by summing entries up to as_of date.

**ACs**:
1. Given as_of date in middle of history, When queried, Then returns balance at that point-in-time
2. Given as_of in Bikram Sambat format, When queried, Then K-15 converts and returns correct balance
3. Given as_of in future, When queried, Then returns current balance (no future entries)

**Tests**: asOfDate_midHistory · asOfDate_bsFormat · asOfDate_future_returnsCurrent · asOfDate_noEntries_returnsZero

**Dependencies**: STORY-K16-003, K-15

---
### STORY-K16-008: Implement periodic balance snapshots
**Feature**: K16-F03 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Create end-of-day and end-of-fiscal-year balance snapshots. Snapshots stored in `balance_snapshot` table (account_id, snapshot_date, snapshot_type, balances_by_currency JSONB). Speed up as-of queries: find nearest snapshot, then compute delta from snapshot to target date.

**ACs**:
1. Given EOD job runs, When balances snapshotted, Then all accounts have snapshot for that date
2. Given as-of query with snapshot available, When queried, Then uses snapshot + delta (faster)
3. Given fiscal year end, When snapshot created, Then labeled as FISCAL_YEAR type with all account balances

**Tests**: snapshot_eod_created · snapshot_fiscalYear · snapshot_speedsUpQuery · snapshot_allAccounts · perf_asOfWithSnapshot_sub10ms

**Dependencies**: STORY-K16-007, K-15 (fiscal year boundaries)

---
### STORY-K16-009: Implement balance history report
**Feature**: K16-F03 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

GET `/ledger/accounts/{id}/history?from=&to=&interval=daily|monthly` returns time-series of balances. Uses snapshots where available, computes where not. Supports dual-calendar date ranges. Returns array of {date, date_bs, balance_by_currency}.

**ACs**:
1. Given monthly interval, When queried, Then returns monthly balance time-series
2. Given dual-calendar range (BS from/to), When queried, Then dates returned in both calendars
3. Given large date range, When snapshots exist, Then computation efficient

**Tests**: history_daily · history_monthly · history_dualCalendar · history_usesSnapshots · perf_yearHistory_sub500ms

**Dependencies**: STORY-K16-008

---

## Feature K16-F04 — Multi-Currency / Multi-Asset Support (3 Stories)

---
### STORY-K16-010: Implement currency registry and precision rules
**Feature**: K16-F04 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Create currency registry: code (ISO 4217), name, symbol, decimal_places (NPR=2, BTC=8, JPY=0), rounding_mode (HALF_UP, BANKER'S). T1 configurable via K-02. All monetary amounts validated against currency's precision before posting.

**ACs**:
1. Given NPR amount 1234.567, When posting, Then rounded to 1234.57 per NPR precision (2 decimals)
2. Given BTC amount 0.12345678, When posting, Then stored with full 8 decimal precision
3. Given unknown currency code, When posting attempted, Then rejected with UNKNOWN_CURRENCY

**Tests**: precision_npr_2dec · precision_btc_8dec · precision_jpy_0dec · precision_unknown_rejected · rounding_halfUp · rounding_bankers

**Dependencies**: K-02 (config)

---
### STORY-K16-011: Implement multi-currency journal posting
**Feature**: K16-F04 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Extend journal posting to support multi-currency entries. Balance enforcement: each currency must independently balance within journal. FX conversion entries recorded as separate balanced journals with exchange rate metadata.

**ACs**:
1. Given journal with NPR entries (debit=5000, credit=5000) and USD entries (debit=100, credit=100), When posted, Then each currency balances independently
2. Given NPR debit=5000 and USD credit=37.50 (mixed currencies), When posted, Then rejected — currencies must balance per-currency
3. Given FX conversion NPR→USD, When posted, Then two journals: NPR journal + USD journal with fx_rate in metadata

**Tests**: multiCurrency_eachBalances · multiCurrency_mixedRejected · multiCurrency_fxConversion · multiCurrency_metadata

**Dependencies**: STORY-K16-002, STORY-K16-010

---
### STORY-K16-012: Implement multi-asset account support
**Feature**: K16-F04 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Extend ledger to support asset accounts (securities, units). Account types: CASH (currency amounts), SECURITY (quantity of instrument), UNIT (fund units). Security entries track instrument_id + quantity. Balances reported per asset type.

**ACs**:
1. Given SECURITY account, When 100 shares of NABIL posted, Then balance shows 100 NABIL shares
2. Given CASH and SECURITY accounts in same journal, When trade settled, Then cash debit + security credit balanced
3. Given balance query for multi-asset account, When queried, Then returns all asset balances

**Tests**: multiAsset_security_posting · multiAsset_trade_settlement · multiAsset_balanceReport · multiAsset_unitFund

**Dependencies**: STORY-K16-002, D-11 (instrument reference)

---

## Feature K16-F05 — Reconciliation Engine (3 Stories)

---
### STORY-K16-013: Implement internal reconciliation engine
**Feature**: K16-F05 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Implement reconciliation that compares ledger balances against source system totals. POST `/ledger/reconciliation/run` triggers matching. Match rules: exact match, tolerance match (configurable epsilon). Results: MATCHED, BREAK, PENDING_REVIEW.

**ACs**:
1. Given ledger balance matches source total (exact), When recon runs, Then status MATCHED
2. Given difference within tolerance (ε=0.01), When recon runs, Then status MATCHED with tolerance_used flag
3. Given difference exceeds tolerance, When recon runs, Then status BREAK with amount difference

**Tests**: recon_exactMatch · recon_toleranceMatch · recon_break_detected · recon_multiCurrency · recon_multiAccount

**Dependencies**: STORY-K16-003

---
### STORY-K16-014: Implement break aging and escalation
**Feature**: K16-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Track reconciliation breaks with aging: T+0 (new) → T+1 (aged) → T+3 (escalated) → T+5 (critical). Auto-escalation notifications. Break lifecycle: OPEN → INVESTIGATING → RESOLVED → CLOSED. Dashboard widget for break summary.

**ACs**:
1. Given break detected at T+0, When unresolved by T+1, Then auto-escalated to team lead
2. Given break at T+3, When still unresolved, Then escalated to department head
3. Given break resolved, When marked RESOLVED, Then requires resolution notes and approver

**Tests**: aging_t0_new · aging_t1_escalation · aging_t3_critical · resolution_requiresNotes · dashboard_breakSummary

**Dependencies**: STORY-K16-013, K-05 (events for notifications)

---
### STORY-K16-015: Implement external statement reconciliation
**Feature**: K16-F05 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Reconcile ledger against external bank/depository statements. Import statements (CSV, MT940, XML). Matching engine: amount + date + reference. Three-way match: ledger entry ↔ statement line ↔ original transaction. Unmatched items flagged.

**ACs**:
1. Given bank statement imported, When matching engine runs, Then ledger entries matched to statement lines
2. Given unmatched statement line, When flagged, Then appears in break report for manual investigation
3. Given CSV/MT940/XML formats, When imported, Then all parsed correctly

**Tests**: externalRecon_csvImport · externalRecon_mt940 · externalRecon_matching · externalRecon_unmatched_flagged · externalRecon_threeWayMatch

**Dependencies**: STORY-K16-013

---

## Feature K16-F06 — Chart of Accounts (2 Stories)

---
### STORY-K16-016: Implement chart of accounts management
**Feature**: K16-F06 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Create chart of accounts (CoA) data model: account_id, code, name, type (ASSET,LIABILITY,EQUITY,REVENUE,EXPENSE), parent_id (hierarchy), currency, status, metadata. T1 configurable per jurisdiction via K-02. Seed default CoA for capital markets.

**ACs**:
1. Given default CoA template, When jurisdiction "NP" configured, Then Nepal-specific chart seeded
2. Given account hierarchy (Assets → Current Assets → Cash), When queried, Then tree structure returned
3. Given account deactivated, When posting attempted, Then rejected with ACCOUNT_INACTIVE

**Tests**: coa_defaultSeed · coa_hierarchy · coa_deactivated_blocked · coa_jurisdictionSpecific · coa_t1Config

**Dependencies**: K-02 (T1 config)

---
### STORY-K16-017: Implement account creation workflow with maker-checker
**Feature**: K16-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

New account creation requires maker-checker approval. POST /ledger/accounts creates in PENDING status. Approver reviews and approves/rejects. AccountCreated event emitted on approval. Account code uniqueness enforced.

**ACs**:
1. Given new account request, When submitted, Then created in PENDING status awaiting approval
2. Given approver approves, When approved, Then status → ACTIVE, AccountCreated event emitted
3. Given duplicate account code, When submitted, Then 409 Conflict

**Tests**: account_create_pending · account_approve_active · account_reject · account_duplicateCode_409 · account_event_emitted

**Dependencies**: STORY-K16-016, K-07 (audit)

---

## Feature K16-F07 — Precision & Rounding Rules (2 Stories)

---
### STORY-K16-018: Implement monetary amount value object
**Feature**: K16-F07 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Create `Money` value object: amount (DECIMAL), currency (ISO 4217), ensures precision per currency registry. Operations: add, subtract, multiply, divide with configurable rounding. Prevents floating-point errors by using decimal arithmetic throughout.

**ACs**:
1. Given Money(100.10, "NPR") + Money(200.20, "NPR"), When added, Then Money(300.30, "NPR") exactly
2. Given Money("NPR") + Money("USD") (different currencies), When added, Then throws CurrencyMismatchError
3. Given Money(100, "NPR") / 3, When divided, Then rounds per currency rules

**Tests**: money_add_sameCurrency · money_add_differentCurrency_throws · money_divide_rounds · money_multiply · money_precision_noFloat

**Dependencies**: STORY-K16-010

---
### STORY-K16-019: Implement rounding allocation for split payments
**Feature**: K16-F07 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Implement fair rounding allocation when splitting amounts. Given NPR 100 split 3 ways: [33.34, 33.33, 33.33] (first gets remainder). Ensures total always equals original. Used for dividend distribution, fee splitting, etc.

**ACs**:
1. Given NPR 100 split 3 ways, When allocated, Then sum of parts = 100.00 exactly
2. Given NPR 1.00 split 3 ways (2 decimal currency), When allocated, Then [0.34, 0.33, 0.33]
3. Given single recipient, When allocated, Then receives full amount unchanged

**Tests**: split_threeWay_balanced · split_remainder_toFirst · split_singleRecipient · split_largeGroup_100ways · split_zeroCurrency_jpy

**Dependencies**: STORY-K16-018

---

# EPIC K-17: DISTRIBUTED TRANSACTION COORDINATOR (14 Stories)

## Feature K17-F01 — Transactional Outbox Pattern (3 Stories)

---
### STORY-K17-001: Implement outbox table and relay service
**Feature**: K17-F01 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Create `outbox` table: id, aggregate_id, event_type, payload JSONB, created_at, published BOOLEAN DEFAULT false, published_at. Relay service polls unpublished rows, publishes to Kafka, marks published. Uses SELECT FOR UPDATE SKIP LOCKED for concurrent relay instances.

**ACs**:
1. Given domain event stored in outbox within same DB transaction as state change, When relay polls, Then event published to Kafka
2. Given Kafka unavailable, When relay retries, Then event remains in outbox, retried on next poll
3. Given multiple relay instances, When polling concurrently, Then SKIP LOCKED prevents double-publish

**Tests**: outbox_publishOnPoll · outbox_kafkaDown_retries · outbox_concurrent_skipLocked · outbox_cleanup_afterPublish · perf_10kPerSec

**Dependencies**: K-05 (Kafka), PostgreSQL

---
### STORY-K17-002: Implement outbox cleanup and monitoring
**Feature**: K17-F01 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Scheduled cleanup of published outbox entries older than configurable retention (default 7 days). Monitoring metrics: outbox_unpublished_count, outbox_lag_seconds, outbox_publish_rate. Alert when lag exceeds threshold.

**ACs**:
1. Given published entries older than 7 days, When cleanup runs, Then purged from outbox table
2. Given outbox lag > 30 seconds, When threshold exceeded, Then alert emitted
3. Given monitoring dashboard, When viewed, Then shows publish rate, lag, queue depth

**Tests**: cleanup_purgesOldEntries · cleanup_retainsRecent · monitoring_lagAlert · monitoring_dashboard_metrics

**Dependencies**: STORY-K17-001, K-06 (metrics)

---
### STORY-K17-003: Implement exactly-once publish guarantee
**Feature**: K17-F01 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Implement idempotent Kafka producer with producer ID and sequence number to achieve exactly-once semantics. Outbox entry ID used as Kafka message key for deduplication. Consumer-side dedup via K-05 idempotency framework as safety net.

**ACs**:
1. Given relay crash after Kafka publish but before marking published, When relay restarts, Then re-publishes same entry — Kafka deduplicates
2. Given idempotent producer enabled, When producing, Then duplicate messages suppressed at broker level
3. Given consumer receives duplicate (edge case), When K-05 idempotency checked, Then second processing skipped

**Tests**: exactlyOnce_relayCrash_noDuplicate · exactlyOnce_producerId · exactlyOnce_consumerDedup

**Dependencies**: STORY-K17-001, K-05 (idempotency)

---

## Feature K17-F02 — Version Vectors for Causal Ordering (3 Stories)

---
### STORY-K17-004: Implement version vector data structure
**Feature**: K17-F02 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Implement version vector (vector clock) data structure for causal ordering across distributed services. Operations: increment(nodeId), merge(otherVector), compare(otherVector) → BEFORE|AFTER|CONCURRENT. Stored as JSONB {nodeId: counter}.

**ACs**:
1. Given vector [A:1, B:2], When A increments, Then [A:2, B:2]
2. Given vectors [A:1, B:2] and [A:2, B:1], When compared, Then CONCURRENT (neither dominates)
3. Given vectors [A:1, B:1] and [A:2, B:2], When compared, Then first BEFORE second

**Tests**: vector_increment · vector_merge · vector_compare_before · vector_compare_after · vector_compare_concurrent · vector_empty_init

**Dependencies**: None (pure data structure)

---
### STORY-K17-005: Implement causal ordering middleware
**Feature**: K17-F02 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Middleware that attaches version vector to every outgoing event/command. On receive: compare incoming vector with local → detect causal ordering. Buffer out-of-order events until causal dependency satisfied or timeout (configurable, default 5s).

**ACs**:
1. Given event A sent before event B from same service, When consumer receives B then A, Then buffers B until A delivered
2. Given causal dependency not satisfied within 5s timeout, When timeout expires, Then deliver anyway with causal_gap=true flag
3. Given concurrent events (no causal relationship), When received, Then both delivered immediately

**Tests**: causal_reorder_outOfOrder · causal_timeout_delivers · causal_concurrent_noBuffer · causal_vectorPropagation

**Dependencies**: STORY-K17-004, K-05 (event consumer)

---
### STORY-K17-006: Implement conflict detection for concurrent writes
**Feature**: K17-F02 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

When concurrent writes detected (CONCURRENT vector comparison), invoke conflict resolver. Default resolver: last-writer-wins (based on wall-clock). Custom resolvers pluggable per aggregate type. ConflictDetected event emitted for audit.

**ACs**:
1. Given concurrent updates to same aggregate, When detected, Then default last-writer-wins applied
2. Given custom resolver registered for OrderAggregate, When conflict detected, Then custom resolver invoked
3. Given any conflict, When resolved, Then ConflictDetected event emitted with both versions and resolution

**Tests**: conflict_lastWriterWins · conflict_customResolver · conflict_event_emitted · conflict_noConflict_noEvent

**Dependencies**: STORY-K17-005

---

## Feature K17-F03 — Saga State Machine with Compensation (4 Stories)

---
### STORY-K17-007: Define DTC saga policies in K-05 saga registry
**Feature**: K17-F03 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Register DTC-specific saga definitions into the K-05-F05 saga definition registry (K05-016). DTC saga policies cover: DVP settlement saga (Lock→Deliver→Pay→Confirm), fund transfer saga (Debit→Credit→Notify), and corporate action saga. Each policy specifies step topics, compensation topics, per-step timeouts, and DTC-specific retry/escalation overrides. DTC **does not** build its own saga registry — it populates K-05's registry with domain policies.

**ACs**:
1. Given DTC startup, When saga policies loaded, Then DVP/fund-transfer/corporate-action saga definitions registered in K-05 registry with status=ACTIVE
2. Given saga definition with invalid step order, When validation runs, Then K-05 registry rejects with INVALID_DEFINITION and DTC logs policy error
3. Given policy version update deployed, When new version registered, Then K-05 creates new version, old version remains active for running DTC instances

**Tests**: dtc_dvpSaga_registered · dtc_fundTransferSaga_registered · dtc_invalidPolicy_rejected · dtc_policyVersioning · dtc_startupRegistration

**Dependencies**: K-05-F05 (saga definition registry — K05-016), K-05 (event infrastructure)

---
### STORY-K17-008: Implement DTC saga coordination layer using K-05 engine
**Feature**: K17-F03 · **Points**: 5 · **Sprint**: 4 · **Team**: Alpha

Build DTC's saga coordination layer on top of the K-05-F05 saga orchestration engine (K05-017). DTC triggers saga instances via K-05 engine's saga start API passing DTC policy names. DTC listens for K-05 saga lifecycle events (SagaStepCompleted, SagaCompensating, SagaCompleted, SagaFailed) and maps them to DTC transaction state transitions. Adds DTC-specific enrichment: regulatory flags, settlement batch linkage, counterparty references. DTC **does not** re-implement event-sourced saga state — K-05 owns that.

**ACs**:
1. Given DVP settlement initiated, When DTC triggers K-05 saga instance for 'dvp-settlement-saga', Then K-05 engine executes steps and DTC receives lifecycle events
2. Given K-05 reports step 2 failed, When DTC receives SagaCompensating event, Then DTC updates its transaction record to COMPENSATING and publishes DTC-level alert
3. Given K-05 engine restarts and rehydrates sagas from events, When DTC reconnects, Then DTC re-subscribes to in-progress saga events and reconciles local transaction state

**Tests**: dtc_triggerSaga_viaK05 · dtc_compensating_stateUpdate · dtc_k05Restart_reconcile · dtc_batchLinkage · dtc_counterpartyEnrichment

**Dependencies**: STORY-K17-007, K-05-F05 (saga orchestration engine — K05-017)

---
### STORY-K17-009: Register DTC compensation callbacks with K-05 framework
**Feature**: K17-F03 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Register DTC domain-specific compensation callbacks with the K-05-F05 compensation handler framework (K05-018). DTC provides compensation callbacks for each saga step: DVP Lock reversal (unlock securities), DVP Delivery reversal (reverse asset transfer), Fund Debit reversal (credit-back), Fund Credit reversal (debit-back). K-05 framework handles retry logic, idempotency, and audit — DTC only provides the compensation business logic. Compensation callbacks receive `(sagaId, stepName, originalPayload)` from K-05 and return `CompensationResult`.

**ACs**:
1. Given K-05 triggers compensation for DVP Lock step, When DTC's lock-reversal callback invoked, Then securities unlocked and event published; K-05 framework handles retry if it fails
2. Given K-05 invokes DTC compensation callback twice (retry), When second invocation, Then idempotent — no double-reversal (DTC checks its own ledger state)
3. Given all DTC compensation callbacks exhausted by K-05 framework, When COMPENSATION_FAILED emitted by K-05, Then DTC raises manual review alert with full regulatory context

**Tests**: dtc_lockReversal_callback · dtc_creditBack_callback · dtc_compensation_idempotent · dtc_compensationFailed_manualAlert · dtc_compensation_audit_logged

**Dependencies**: STORY-K17-008, K-05-F05 (compensation handler framework — K05-018), K-07 (audit)

---
### STORY-K17-010: Monitor DTC saga execution via K-05 lifecycle events
**Feature**: K17-F03 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Build DTC saga monitoring on top of K-05-F05 timeout and lifecycle events (K05-019). DTC configures per-step timeouts in saga policy definitions (registerd in K05-016). K-05 engine fires timeout events; DTC receives them and applies DTC-level escalation rules (e.g., DVP step timeout → SRN alert, fund transfer timeout → compliance notification). DTC dashboard surfaces: active DTC sagas, step SLA breaches, counterparty-level failure rates, regulatory compensation queue depth.

**ACs**:
1. Given DVP step timeout configured as 30s in DTC policy, When K-05 fires step timeout event, Then DTC publishes DtcSagaStepTimedOut event and raises SRN alert
2. Given DTC saga global SLA of 5 minutes exceeded, When K-05 fires global timeout, Then DTC marks transaction as SETTLEMENT_FAILED and notifies clearing house
3. Given DTC monitoring dashboard loaded, When viewed, Then shows active DTC sagas grouped by type, step SLA breaches, and regulatory compensation queue depth

**Tests**: dtc_stepTimeout_srnAlert · dtc_globalTimeout_clearingNotified · dtc_dashboard_activeSagas · dtc_dashboard_complianceQueue · dtc_alerting_stuckSaga

**Dependencies**: STORY-K17-008, K-05-F05 (saga timeout engine — K05-019), K-06 (metrics)

---

## Feature K17-F04 — Idempotent Command Processing (2 Stories)

---
### STORY-K17-011: Extend K-05 idempotency store with DTC command namespace
**Feature**: K17-F04 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Extend the K-05-F04 idempotency store (K05-013, Redis SET NX) to handle DTC command-bus traffic. DTC registers a namespaced key prefix `dtc:{command_type}:{command_id}` in K-05's Redis idempotency store — **no separate Redis cluster or dedup table** is created. Adds DTC-specific TTL policy: settlement commands retain 7 days (vs platform default 24h) to cover T+2 settlement windows. Also configures PostgreSQL fallback (K05-015) to use the shared `idempotency_keys` table with a `source=DTC` discriminator column.

**ACs**:
1. Given DTC command with command_id, When checked via K-05 store with prefix `dtc:settle:{id}`, Then Redis NX check performed; hit returns cached result, miss executes command and caches
2. Given settlement command resubmitted within 7-day TTL, When re-checked, Then cached result returned — no duplicate settle execution
3. Given Redis unavailable, When DTC command processed, Then K-05 PostgreSQL fallback (K05-015) used with DTC source discriminator; result correct

**Tests**: dtc_dedup_namespace_hit · dtc_dedup_namespace_miss_executes · dtc_dedup_7dayTTL · dtc_dedup_fallback_postgres · perf_dedupCheck_sub1ms

**Dependencies**: K-05-F04 (idempotency store — K05-013, K05-015)

---
### STORY-K17-012: Apply K-05 idempotency middleware to DTC command API
**Feature**: K17-F04 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Configure the K-05-F04 `IdempotencyGuard` middleware (K05-014) on all DTC command API endpoints (POST `/transactions`, POST `/transactions/{id}/settle`, POST `/transactions/{id}/cancel`). DTC-specific configuration: key extracted from `X-DTC-Command-Id` header (falls back to `X-Idempotency-Key`), namespace prefix `dtc:cmd`, TTL=7 days. Concurrent duplicate DTC commands — second returns 409 Conflict while first is in-flight. DTC does **not** rewrite the guard — it mounts K05-014 middleware with DTC route config.

**ACs**:
1. Given POST /transactions with X-DTC-Command-Id header, When first call, Then K05-014 middleware enforces idempotency with DTC namespace and caches 201 response
2. Given same X-DTC-Command-Id resubmitted within 7-day TTL, When cache hit, Then cached 201 response returned without re-executing settlement logic
3. Given two concurrent POST /transactions with identical X-DTC-Command-Id, When second arrives while first is processing, Then 409 Conflict returned with Retry-After; first completes normally and caches result

**Tests**: dtc_idempotent_firstCall · dtc_idempotent_replay_cached201 · dtc_idempotent_concurrent_409 · dtc_idempotent_customHeader_fallback · dtc_idempotent_noHeader_passthrough

**Dependencies**: STORY-K17-011, K-05-F04 (idempotency guard middleware — K05-014)

---

## Feature K17-F05 — Saga Definition Registry (2 Stories)

---
### STORY-K17-013: Implement DTC saga policy management REST API
**Feature**: K17-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

REST API for managing DTC-specific saga policy definitions stored in the K-05 saga registry. Endpoints: GET `/dtc/saga-policies` (list DTC saga policies), GET `/dtc/saga-policies/{name}` (specific version), POST `/dtc/saga-policies` (register new policy — proxies to K05-016 with DTC namespace), DELETE `/dtc/saga-policies/{name}` (deprecate). Swagger documented. RBAC protected (admin only). Maker-checker workflow for policy changes.

**ACs**:
1. Given GET /dtc/saga-policies, When called, Then returns all DTC saga policies registered in K-05 registry with their versions and status
2. Given new policy POSTed, When valid, Then stored as PENDING in K-05 registry until maker-checker approved; DTC publishes DtcSagaPolicyPending event
3. Given deprecation request, When approved, Then K-05 marks version deprecated; no new DTC instances started for deprecated policy, existing continue

**Tests**: api_listDtcPolicies · api_getByName · api_register_pending_makerChecker · api_deprecate · api_rbac_adminOnly

**Dependencies**: STORY-K17-007, K-05-F05 (saga definition registry — K05-016)

---
### STORY-K17-014: Implement DTC saga instance query and intervention API
**Feature**: K17-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

REST API for querying and managing DTC saga instances backed by K-05 saga state. GET `/dtc/sagas` with filters (status, type: dvp|fund-transfer|corporate-action, date_range, counterparty). GET `/dtc/sagas/{id}` returns full saga step history sourced from K-05 event stream enriched with DTC business context. POST `/dtc/sagas/{id}/force-compensate` triggers manual K-05 compensation with DTC regulatory override flag. POST `/dtc/sagas/{id}/force-complete` for exceptional SRN-approved completions.

**ACs**:
1. Given running DTC sagas, When GET /dtc/sagas?status=RUNNING&type=dvp, Then returns all in-progress DVP sagas with counterparty and settlement batch references
2. Given saga instance, When GET /dtc/sagas/{id}, Then returns K-05 step event history enriched with DTC fields (counterparty, ISIN, settlement amount) for audit
3. Given stuck saga, When force-compensate called with supervisory override, Then K-05 compensation triggered with manual_override=true flag and DTC raises regulatory alert

**Tests**: dtc_query_byStatus · dtc_query_byType · dtc_query_fullHistory_enriched · dtc_forceComplete_srnApproved · dtc_forceCompensate_audited

**Dependencies**: STORY-K17-008, K-05-F05 (saga engine — K05-017)

---

# EPIC K-18: RESILIENCE PATTERNS (13 Stories)

## Feature K18-F01 — Circuit Breaker Library (3 Stories)

---
### STORY-K18-001: Implement circuit breaker state machine
**Feature**: K18-F01 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Create CircuitBreaker class with states: CLOSED → OPEN → HALF_OPEN → CLOSED. Config: failure_threshold (default 5), reset_timeout_ms (default 30000), half_open_probes (default 3). Sliding window failure detection (count-based or time-based configurable).

**ACs**:
1. Given CLOSED state with 5 consecutive failures, When threshold reached, Then transitions to OPEN
2. Given OPEN state for 30 seconds, When timeout expires, Then transitions to HALF_OPEN
3. Given HALF_OPEN state with 3 successful probes, When probes pass, Then transitions to CLOSED

**Tests**: cb_closedToOpen · cb_openToHalfOpen · cb_halfOpenToClosed · cb_halfOpenToOpen_onFail · cb_slidingWindow

**Dependencies**: K-06 (metrics)

---
### STORY-K18-002: Implement circuit breaker execution wrapper
**Feature**: K18-F01 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Create execution wrapper: `circuitBreaker.execute(fn, fallback?)`. CLOSED: execute fn. OPEN: return fallback immediately (if provided) or throw CircuitOpenError. HALF_OPEN: allow limited probes. Emit CircuitStateChanged events. Prometheus gauge for state.

**ACs**:
1. Given CLOSED circuit, When fn called, Then executes normally
2. Given OPEN circuit, When fn called with fallback, Then fallback returned immediately (no execution)
3. Given state changes, When transition occurs, Then CircuitStateChanged event emitted, Prometheus gauge updated

**Tests**: execute_closed_normal · execute_open_fallback · execute_open_noFallback_throws · execute_event_emitted · execute_metrics_updated

**Dependencies**: STORY-K18-001, K-05 (events), K-06 (metrics)

---
### STORY-K18-003: Implement pre-defined circuit breaker profiles
**Feature**: K18-F01 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Create named profiles: `STRICT` (threshold=3, timeout=60s — for financial operations), `STANDARD` (threshold=5, timeout=30s), `RELAXED` (threshold=10, timeout=15s — for non-critical paths). T1 configurable per endpoint via K-02.

**ACs**:
1. Given STRICT profile applied, When 3 failures, Then circuit opens (instead of default 5)
2. Given RELAXED profile, When 10 failures, Then circuit opens with 15s timeout
3. Given per-endpoint override via K-02, When config changes, Then circuit breaker reconfigured without restart

**Tests**: profile_strict · profile_standard · profile_relaxed · profile_override_fromConfig · profile_hotReload

**Dependencies**: STORY-K18-001, K-02

---

## Feature K18-F02 — Bulkhead Isolation (2 Stories)

---
### STORY-K18-004: Implement thread pool bulkhead
**Feature**: K18-F02 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Implement bulkhead pattern using bounded thread/worker pools. Each downstream service gets its own pool (configurable pool size). When pool exhausted → immediate rejection (BulkheadFullError) instead of queuing. Prevents one slow dependency from consuming all resources.

**ACs**:
1. Given pool size 10 for serviceA, When 11th concurrent request, Then immediately rejected with BulkheadFullError
2. Given serviceA pool exhausted, When serviceB called, Then serviceB pool unaffected (isolated)
3. Given pool monitoring, When metrics queried, Then shows active/available/rejected counts per pool

**Tests**: bulkhead_exhausted_rejects · bulkhead_isolation · bulkhead_metrics · bulkhead_configurable_poolSize

**Dependencies**: K-06 (metrics)

---
### STORY-K18-005: Implement semaphore-based bulkhead
**Feature**: K18-F02 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Implement semaphore-based (non-thread) bulkhead for Node.js async context. Limits concurrent executions using async semaphore. Max concurrent (configurable) + max queue (configurable). Queue timeout (default 5s) → reject if wait exceeds.

**ACs**:
1. Given max_concurrent=10, max_queue=20, When 11th request, Then queued (not rejected)
2. Given queue full (30 total), When 31st request, Then immediately rejected
3. Given queued request waiting > 5s, When timeout, Then rejected with QueueTimeoutError

**Tests**: semaphore_withinLimit · semaphore_queued · semaphore_queueFull_rejected · semaphore_queueTimeout

**Dependencies**: STORY-K18-004

---

## Feature K18-F03 — Retry Policies (2 Stories)

---
### STORY-K18-006: Implement retry with exponential backoff and jitter
**Feature**: K18-F03 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Create retry utility: `retry(fn, {maxAttempts, initialDelay, maxDelay, backoffMultiplier, jitter})`. Exponential backoff: delay = min(initialDelay × multiplier^attempt, maxDelay) + random_jitter. Only retry on configurable error types (transient). Non-retryable errors fail immediately.

**ACs**:
1. Given transient error on first attempt, When retried, Then succeeds on 2nd attempt with backoff delay
2. Given non-retryable error (400 Bad Request), When encountered, Then fails immediately (no retry)
3. Given max attempts exhausted, When all retries fail, Then throws MaxRetriesExceededError with all attempt details

**Tests**: retry_transient_succeeds · retry_nonRetryable_immediate · retry_maxExhausted · retry_backoff_exponential · retry_jitter_randomized

**Dependencies**: None

---
### STORY-K18-007: Implement retry context propagation
**Feature**: K18-F03 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Propagate retry metadata in context: attempt_number, total_attempts, is_retry. Logging includes retry context. Metrics: retry_attempts_total, retry_success_after_n_attempts histogram. Consumer-side idempotency ensures retried operations are safe.

**ACs**:
1. Given retry attempt #3, When executing, Then attempt_number=3 available in context for logging
2. Given retry succeeds on attempt 2, When metric recorded, Then histogram records success_after_2
3. Given all retries logged, When log searched, Then shows correlation between original and retry attempts

**Tests**: retryContext_attemptNumber · retryContext_metrics · retryContext_logging · retryContext_idempotentSafe

**Dependencies**: STORY-K18-006, K-06 (metrics)

---

## Feature K18-F04 — Timeout Management (2 Stories)

---
### STORY-K18-008: Implement cascading timeout budgets
**Feature**: K18-F04 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Implement timeout budget propagation: when service A calls B (500ms timeout), B calls C — C's timeout is remaining budget from A's perspective. Pass deadline in header `X-Request-Deadline` (UTC timestamp). Each hop subtracts elapsed time.

**ACs**:
1. Given A sets 500ms budget calling B, When B processes for 200ms then calls C, Then C gets 300ms budget
2. Given budget exhausted before C responds, When deadline passed, Then TimeoutBudgetExceeded error
3. Given no deadline header, When request received, Then default timeout per service config applied

**Tests**: timeout_cascading_budgetReduced · timeout_exceeded_error · timeout_noHeader_default · timeout_headerPropagation

**Dependencies**: K-11 (API Gateway propagates header)

---
### STORY-K18-009: Implement timeout configuration per route
**Feature**: K18-F04 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Configure timeout durations per route/endpoint via K-02. Critical paths (risk check) get shorter timeouts (5ms). Non-critical paths (reporting) get longer (30s). Timeout override via request header (capped at max). Metrics: timeout_triggered_total per route.

**ACs**:
1. Given /risk/check configured with 5ms timeout, When exceeds 5ms, Then timeout triggered
2. Given client sends X-Timeout-Override: 10ms, When within max, Then 10ms applied
3. Given timeout triggered, When metric recorded, Then route-level timeout counter incremented

**Tests**: timeout_perRoute · timeout_override_withinMax · timeout_override_exceedsMax_capped · timeout_metrics

**Dependencies**: STORY-K18-008, K-02

---

## Feature K18-F05 — Pre-Defined Resilience Profiles (2 Stories)

---
### STORY-K18-010: Implement composite resilience profiles
**Feature**: K18-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Create composite profiles combining circuit breaker + bulkhead + retry + timeout. Profile `TRADING_CRITICAL`: CB(strict) + Bulkhead(50) + Retry(2, 100ms) + Timeout(5ms). Profile `STANDARD_SERVICE`: CB(standard) + Bulkhead(100) + Retry(3, 500ms) + Timeout(5s). Profile `BACKGROUND_JOB`: Retry(5, 2s) + Timeout(60s).

**ACs**:
1. Given TRADING_CRITICAL profile applied to risk check endpoint, When called, Then all 4 patterns active with correct params
2. Given profile selected via decorator/annotation `@resilient('TRADING_CRITICAL')`, When applied, Then configured automatically
3. Given K-02 config override, When profile parameters changed, Then hot-reloaded without restart

**Tests**: profile_tradingCritical · profile_standardService · profile_backgroundJob · profile_decorator · profile_hotReload

**Dependencies**: STORY-K18-001 through K18-009, K-02

---
### STORY-K18-011: Implement resilience profile monitoring
**Feature**: K18-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Dashboard showing per-service resilience profile status: circuit states across all endpoints, bulkhead utilization, retry rates, timeout rates. Composite health score per service. Alert when health degrades below threshold.

**ACs**:
1. Given dashboard, When viewed, Then shows all circuit breaker states per endpoint per service
2. Given service health drops below 80%, When threshold crossed, Then alert emitted
3. Given time range selector, When period selected, Then shows historical resilience metrics

**Tests**: dashboard_circuitStates · dashboard_bulkheadUtil · dashboard_healthScore · alerting_threshold

**Dependencies**: STORY-K18-010, K-06 (Grafana)

---

## Feature K18-F06 — Dependency Health Dashboard (2 Stories)

---
### STORY-K18-012: Implement dependency health check aggregator
**Feature**: K18-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Each service exposes GET /health/dependencies returning status of all downstream dependencies (DB, Redis, Kafka, other services). Aggregator collects from all services, builds dependency health tree. Status: UP, DEGRADED, DOWN.

**ACs**:
1. Given service with healthy deps, When /health/dependencies called, Then {postgres: UP, redis: UP, kafka: UP}
2. Given Kafka down, When health checked, Then {kafka: DOWN}, overall status: DEGRADED
3. Given aggregator collecting from 33 services, When polled, Then builds complete health tree

**Tests**: health_allUp · health_oneDown_degraded · health_aggregator_fullTree · health_timeout_unknown

**Dependencies**: K-06 (observability)

---
### STORY-K18-013: Implement dependency health Grafana dashboard
**Feature**: K18-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Create Grafana dashboard showing: service dependency graph with color-coded health (green/yellow/red), circuit breaker states overlay, latency percentiles per dependency, availability SLO tracking. Auto-refresh 30s.

**ACs**:
1. Given all deps healthy, When dashboard viewed, Then all nodes green
2. Given circuit breaker open on dependency, When viewed, Then node red with CB indicator
3. Given SLO target 99.9%, When availability drops below, Then error budget burn rate highlighted

**Tests**: dashboard_renders · dashboard_colorCoding · dashboard_circuitOverlay · dashboard_sloTracking

**Dependencies**: STORY-K18-012, K-06

---

# EPIC K-03: POLICY / RULES ENGINE (14 Stories)

## Feature K03-F01 — OPA/Rego Evaluation Runtime (3 Stories)

---
### STORY-K03-001: Implement OPA evaluation service wrapper
**Feature**: K03-F01 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Wrap Open Policy Agent as gRPC/REST service. POST `/rules/evaluate` accepts input (JSON) + policy path, returns decision. Bundle-based policy loading from K-02. OPA runs as sidecar or embedded library (configurable deployment mode).

**ACs**:
1. Given Rego policy loaded, When evaluate called with input, Then decision returned (allow/deny + reasons)
2. Given policy not found for path, When evaluate called, Then 404 POLICY_NOT_FOUND
3. Given evaluation timeout (>10ms P99 target), When exceeded, Then circuit breaker triggers

**Tests**: opa_evaluate_allow · opa_evaluate_deny · opa_policyNotFound · opa_timeout_circuitBreaker · perf_sub10ms

**Dependencies**: K-02 (policy bundles), K-18 (circuit breaker)

---
### STORY-K03-002: Implement policy bundle management
**Feature**: K03-F01 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Manage OPA policy bundles: upload, version, activate. Bundle = collection of Rego files + data files. Store in S3/MinIO. OPA polls for bundle updates (configurable interval). Bundle integrity verified via SHA-256 hash.

**ACs**:
1. Given new bundle uploaded, When valid Rego, Then stored with version, SHA-256 hash computed
2. Given bundle activated, When OPA polls, Then downloads and applies new bundle
3. Given corrupted bundle, When OPA downloads, Then hash mismatch detected, old bundle retained

**Tests**: bundle_upload · bundle_activate · bundle_hashVerification · bundle_corruptRejected · bundle_versioning

**Dependencies**: S3/MinIO, K-02

---
### STORY-K03-003: Implement policy evaluation caching
**Feature**: K03-F01 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Cache policy decisions in Redis for frequently evaluated identical inputs. Cache key: SHA-256(policy_path + input). Configurable TTL per policy (default 60s, 0 for no-cache for dynamic policies). Cache invalidated on bundle update.

**ACs**:
1. Given same input evaluated twice, When cache hit, Then cached decision returned (<1ms)
2. Given bundle updated, When new version active, Then cache invalidated for affected policies
3. Given no-cache policy, When evaluated, Then always evaluated fresh (never cached)

**Tests**: cache_hit_fast · cache_missEvaluates · cache_invalidatedOnBundleUpdate · cache_noCachePolicy · perf_cacheSub1ms

**Dependencies**: STORY-K03-001, Redis

---

## Feature K03-F02 — T2 Sandbox Isolation (3 Stories)

---
### STORY-K03-004: Implement T2 rule execution sandbox
**Feature**: K03-F02 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Create sandboxed execution environment for T2 (jurisdictional) rule packs. T2 rules run in isolated V8/WASM sandbox with restricted API surface. No network access, no file system, limited memory (configurable, default 64MB), execution timeout (default 100ms).

**ACs**:
1. Given T2 rule executed in sandbox, When completes, Then result returned within resource limits
2. Given T2 rule attempts network access, When intercepted, Then NetworkAccessDenied error
3. Given T2 rule exceeds memory limit, When 64MB exceeded, Then MemoryLimitExceeded error, execution terminated

**Tests**: sandbox_execution_success · sandbox_networkBlocked · sandbox_memoryLimit · sandbox_timeout · sandbox_fileSystemBlocked

**Dependencies**: K-04 (plugin runtime for sandboxing)

---
### STORY-K03-005: Implement T2 rule API surface (restricted SDK)
**Feature**: K03-F02 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Define restricted API surface available inside T2 sandbox: read config (K-02), query reference data (D-11, read-only), read calendar (K-15), logging (structured). No write operations. API calls instrumented with sandbox context.

**ACs**:
1. Given T2 rule calls readConfig(), When executed, Then returns config value from K-02
2. Given T2 rule calls writeConfig(), When attempted, Then WriteNotAllowed error
3. Given API call from sandbox, When logged, Then includes sandbox_id, rule_pack, invocation_context

**Tests**: api_readConfig_allowed · api_writeConfig_blocked · api_readRefData · api_calendar · api_logging_contextual

**Dependencies**: STORY-K03-004, K-02, K-15

---
### STORY-K03-006: Implement T2 rule resource accounting
**Feature**: K03-F02 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Track resource usage per T2 rule execution: CPU time, memory peak, API calls count, execution duration. Enforce quotas configurable per jurisdiction. Log resource usage metrics. Alert when rule approaches quota limits.

**ACs**:
1. Given T2 rule executes, When completed, Then resource usage report: cpu_ms, memory_peak_kb, api_calls, duration_ms
2. Given CPU quota 50ms exceeded, When rule runs long, Then terminated with ResourceQuotaExceeded
3. Given rule using 80% of quota, When metric checked, Then warning alert emitted

**Tests**: resources_tracked · resources_quotaEnforced · resources_warningAt80pct · resources_metrics_exposed

**Dependencies**: STORY-K03-004, K-06

---

## Feature K03-F03 — Hot-Reload of Rule Packs (2 Stories)

---
### STORY-K03-007: Implement rule pack hot-reload mechanism
**Feature**: K03-F03 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

When new rule bundle activated, OPA reloads without service restart. Transition: old bundle serves requests → new bundle loaded → health check → if healthy, switch to new → if unhealthy, rollback to old. Zero-downtime reload.

**ACs**:
1. Given new bundle version, When hot-reload triggered, Then old bundle serves until new bundle healthy
2. Given new bundle fails health check, When health check fails, Then rollback to previous bundle
3. Given hot-reload in progress, When concurrent requests, Then served without interruption

**Tests**: hotReload_zeroDowntime · hotReload_failedBundle_rollback · hotReload_concurrentRequests · hotReload_event_emitted

**Dependencies**: STORY-K03-002, K-06

---
### STORY-K03-008: Implement rule pack canary deployment
**Feature**: K03-F03 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Support canary rollout for rule changes: new bundle evaluates X% of requests (configurable). Compare decisions between old and new bundles (shadow mode). If decision mismatch rate below threshold → promote. If above → rollback.

**ACs**:
1. Given canary at 10%, When 100 requests, Then ~10 evaluated by new bundle, ~90 by old
2. Given ≤5% decision mismatch, When threshold not exceeded, Then canary can be promoted
3. Given >5% mismatch, When threshold exceeded, Then auto-rollback to old bundle

**Tests**: canary_percentage · canary_promote_belowThreshold · canary_rollback_aboveThreshold · canary_shadow_comparison

**Dependencies**: STORY-K03-007

---

## Feature K03-F04 — Jurisdiction-Specific Routing (2 Stories)

---
### STORY-K03-009: Implement jurisdiction-based policy routing
**Feature**: K03-F04 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Route policy evaluation to jurisdiction-specific rule bundle based on request context (tenant → jurisdiction mapping from K-02). Each jurisdiction has own bundle namespace. Fall through to global rules if jurisdiction-specific rule not found.

**ACs**:
1. Given Nepal jurisdiction, When compliance rule evaluated, Then NP-specific bundle used
2. Given India jurisdiction, When same rule evaluated, Then IN-specific bundle used
3. Given jurisdiction without specific rule, When evaluated, Then global fallback bundle used

**Tests**: routing_nepal · routing_india · routing_fallbackGlobal · routing_unknownJurisdiction_fallback · routing_configDriven

**Dependencies**: K-02 (jurisdiction config), STORY-K03-001

---
### STORY-K03-010: Implement jurisdiction rule pack registry
**Feature**: K03-F04 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

REST API to manage jurisdiction→bundle mappings. GET /rules/jurisdictions lists configured jurisdictions. POST /rules/jurisdictions/{code}/bundle sets bundle. Maker-checker for changes. AuditTrail for all changes.

**ACs**:
1. Given POST /rules/jurisdictions/NP/bundle with bundle_id, When approved, Then Nepal routes to that bundle
2. Given GET /rules/jurisdictions, When called, Then lists all configured jurisdictions with bundle versions
3. Given bundle change, When made, Then requires maker-checker and audit entry

**Tests**: registry_set_bundle · registry_list · registry_makerChecker · registry_audit_logged

**Dependencies**: STORY-K03-009, K-07

---

## Feature K03-F05 — Maker-Checker for Rule Deployment (2 Stories)

---
### STORY-K03-011: Implement rule change approval workflow
**Feature**: K03-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

All rule bundle deployments require maker-checker. Maker uploads bundle → reviewer validates (dry-run evaluation against test scenarios) → approves/rejects. Approval record stored. RuleDeployed event emitted.

**ACs**:
1. Given new bundle uploaded by maker, When submitted, Then status PENDING_APPROVAL
2. Given reviewer approves with test results, When approved, Then bundle activated, RuleDeployed event
3. Given reviewer rejects, When rejected, Then bundle archived with rejection reason

**Tests**: approval_submit_pending · approval_approve_activates · approval_reject · approval_event_emitted

**Dependencies**: STORY-K03-002, K-07

---
### STORY-K03-012: Implement rule dry-run evaluation
**Feature**: K03-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

POST `/rules/evaluate/dry-run` evaluates against new (unapproved) bundle without activating. Accepts test scenarios (input/expected_decision pairs). Returns comparison: pass/fail per scenario. Used by reviewer before approving bundle.

**ACs**:
1. Given 10 test scenarios, When dry-run against new bundle, Then 10 pass/fail results returned
2. Given all scenarios pass, When summary generated, Then 100% pass rate with details
3. Given scenario fails, When result returned, Then includes expected vs actual decision with diff

**Tests**: dryRun_allPass · dryRun_someFail · dryRun_detailedDiff · dryRun_unapprovedBundle

**Dependencies**: STORY-K03-001, STORY-K03-011

---

## Feature K03-F06 — Circuit Breaker & Degraded Mode (2 Stories)

---
### STORY-K03-013: Implement rules engine circuit breaker
**Feature**: K03-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Apply K-18 circuit breaker STRICT profile to rules engine. When OPA unavailable → circuit opens → configurable degraded behavior per policy type: compliance rules → default DENY (fail-safe), non-critical → default ALLOW, cached decisions → serve stale.

**ACs**:
1. Given OPA unavailable, When compliance rule evaluated, Then default DENY returned (fail-safe)
2. Given OPA unavailable, When non-critical rule evaluated, Then default ALLOW with degraded flag
3. Given OPA recovers, When circuit half-open probes succeed, Then normal evaluation resumes

**Tests**: cb_compliance_defaultDeny · cb_nonCritical_defaultAllow · cb_recovery · cb_degradedFlag_logged

**Dependencies**: K-18 (circuit breaker), STORY-K03-001

---
### STORY-K03-014: Implement rules engine fallback decision logging
**Feature**: K03-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

All fallback/degraded decisions logged with full context: policy, input, fallback_reason, fallback_decision. Alert on fallback rate exceeding threshold (>1% of evaluations). Dashboard for fallback rate tracking.

**ACs**:
1. Given fallback decision made, When logged, Then includes input, policy, reason, decision
2. Given fallback rate >1%, When threshold exceeded, Then alert emitted
3. Given dashboard, When viewed, Then shows fallback rate over time with breakdown by policy

**Tests**: fallback_logged · fallback_alert_threshold · fallback_dashboard · fallback_rateCalculation

**Dependencies**: STORY-K03-013, K-06, K-07

---

# EPIC K-04: PLUGIN RUNTIME & SDK (15 Stories)

## Feature K04-F01 — Ed25519 Signature Verification (2 Stories)

---
### STORY-K04-001: Implement plugin manifest schema and Ed25519 verification
**Feature**: K04-F01 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Define plugin manifest schema: name, version, tier (T1/T2/T3), author, signature, capabilities[], dependencies[], entry_point, checksum. Verify Ed25519 signature against platform's trusted publisher keys. Reject unsigned or invalid plugins.

**ACs**:
1. Given plugin with valid Ed25519 signature, When verified, Then registration proceeds
2. Given plugin with invalid/missing signature, When verification fails, Then rejected with INVALID_SIGNATURE
3. Given known publisher key, When plugin signed by publisher, Then trust chain validated

**Tests**: verify_validSignature · verify_invalidSignature_rejected · verify_missingSignature · verify_knownPublisher · verify_manifest_schema

**Dependencies**: K-14 (trusted keys)

---
### STORY-K04-002: Implement plugin checksum validation
**Feature**: K04-F01 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Validate plugin artifact checksum (SHA-256) matches manifest's declared checksum. Verification at upload time and at runtime load time. Detect tampered plugins. Store verified checksum in plugin registry.

**ACs**:
1. Given plugin upload, When checksum matches manifest, Then accepted
2. Given tampered plugin (checksum mismatch), When verified, Then rejected with CHECKSUM_MISMATCH
3. Given plugin loaded at runtime, When checksum re-verified, Then matches initial verification

**Tests**: checksum_match · checksum_mismatch_rejected · checksum_runtimeRecheck · checksum_stored

**Dependencies**: STORY-K04-001

---

## Feature K04-F02 — Tier-Based Isolation (4 Stories)

---
### STORY-K04-003: Implement T1 config plugin loader
**Feature**: K04-F02 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

T1 plugins are data-only (JSON/YAML configurations). Loader reads, validates against schema, registers config. No code execution. Used for: holiday calendars, chart of accounts, fee schedules. Loaded via K-02 configuration engine.

**ACs**:
1. Given T1 plugin (JSON config), When loaded, Then validated against declared schema and registered
2. Given invalid T1 config, When schema validation fails, Then rejected with SCHEMA_VALIDATION_ERROR
3. Given T1 config change, When hot-reloaded, Then K-02 picks up new config without restart

**Tests**: t1_load_valid · t1_load_invalid_rejected · t1_hotReload · t1_noCodeExecution

**Dependencies**: STORY-K04-001, K-02

---
### STORY-K04-004: Implement T2 sandbox plugin runtime
**Feature**: K04-F02 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

T2 plugins execute scripted rules in sandboxed runtime (V8 isolate or WASM). Restricted API surface (read-only K-02 config, K-15 calendar, D-11 reference data). Memory limit (64MB), CPU timeout (100ms), no network/filesystem. Used for jurisdiction-specific rules.

**ACs**:
1. Given T2 plugin, When loaded, Then executes in isolated V8/WASM sandbox
2. Given T2 plugin attempts filesystem access, When intercepted, Then SandboxViolation error
3. Given T2 plugin exceeds 64MB memory, When limit reached, Then terminated with MemoryExceeded

**Tests**: t2_sandbox_execution · t2_sandbox_noNetwork · t2_sandbox_noFs · t2_sandbox_memoryLimit · t2_sandbox_cpuTimeout

**Dependencies**: STORY-K04-001, K-03 (sandbox shared)

---
### STORY-K04-005: Implement T3 network plugin runtime
**Feature**: K04-F02 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

T3 plugins run in separate containerized processes with network access (for exchange adapters, external API integrations). Communication via gRPC/REST. Resource quotas (CPU, memory, network bandwidth). Capability-based access: declare required endpoints, only those whitelisted.

**ACs**:
1. Given T3 plugin declared capability "exchange:connect", When loaded, Then exchange endpoint accessible
2. Given T3 plugin accesses undeclared endpoint, When intercepted, Then CapabilityDenied error
3. Given T3 container exceeds resource quota, When limits reached, Then throttled/killed with alert

**Tests**: t3_networkAllowed_declared · t3_networkBlocked_undeclared · t3_containerQuota · t3_gRPC_communication

**Dependencies**: STORY-K04-001, Kubernetes

---
### STORY-K04-006: Implement tier enforcement and promotion validation
**Feature**: K04-F02 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Enforce that plugins cannot escalate their own tier at runtime. T1→T2→T3 promotion requires re-registration with updated manifest (re-signed). Tier declared in manifest, verified at load. Runtime tier check on every API call from plugin.

**ACs**:
1. Given T1 plugin, When attempting T2 API (execute script), Then TierEscalation rejected
2. Given T2 plugin, When attempting T3 API (network call), Then TierEscalation rejected
3. Given tier promotion requested, When new manifest signed, Then re-verification required

**Tests**: tierEnforcement_t1_noExecute · tierEnforcement_t2_noNetwork · tierEnforcement_promotion · tierEnforcement_runtime_check

**Dependencies**: STORY-K04-003, STORY-K04-004, STORY-K04-005

---

## Feature K04-F03 — Capability-Based Access Control (2 Stories)

---
### STORY-K04-007: Implement capability declaration and verification
**Feature**: K04-F03 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Plugins declare required capabilities in manifest: read_config, read_calendar, query_reference, execute_network:{endpoint}. At registration, capabilities reviewed. At runtime, every API call checked against declared capabilities.

**ACs**:
1. Given plugin declares "read_config", When calls readConfig(), Then allowed
2. Given plugin does NOT declare "query_reference", When calls queryReferenceData(), Then CapabilityDenied
3. Given capability check, When performed, Then < 0.1ms overhead per call

**Tests**: capability_declared_allowed · capability_undeclared_denied · capability_overhead_minimal · capability_manifest_validation

**Dependencies**: STORY-K04-001

---
### STORY-K04-008: Implement capability approval workflow
**Feature**: K04-F03 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Plugin capability review: new capabilities require admin approval. High-risk capabilities (execute_network, write_data) require security team review. Approved capabilities stored in registry. PluginCapabilityApproved event emitted.

**ACs**:
1. Given plugin requests "execute_network", When submitted, Then requires security team approval
2. Given low-risk capability "read_config", When submitted, Then auto-approved (configurable)
3. Given capability approved, When stored, Then PluginCapabilityApproved event emitted

**Tests**: approval_highRisk_securityReview · approval_lowRisk_autoApproved · approval_event · approval_rejection

**Dependencies**: STORY-K04-007, K-07

---

## Feature K04-F04 — Version Compatibility Enforcement (2 Stories)

---
### STORY-K04-009: Implement semantic version compatibility checking
**Feature**: K04-F04 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Check plugin compatibility using semantic versioning. Plugin declares `platform_version_range` (e.g., ">=2.0.0 <3.0.0"). At load time, verify current platform version satisfies range. Also check inter-plugin dependency versions.

**ACs**:
1. Given plugin requires ">=2.0.0", When platform is 2.5.0, Then compatible — loaded
2. Given plugin requires ">=3.0.0", When platform is 2.5.0, Then incompatible — rejected
3. Given plugin A depends on plugin B v2, When B is v1, Then dependency conflict reported

**Tests**: version_compatible · version_incompatible · version_dependencyConflict · version_rangeEdge

**Dependencies**: STORY-K04-001

---
### STORY-K04-010: Implement plugin dependency resolution
**Feature**: K04-F04 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Resolve plugin dependency graph at registration. Detect circular dependencies, missing dependencies, version conflicts. Load order: topological sort of dependency graph. Block registration if dependencies unsatisfied.

**ACs**:
1. Given plugin A → B → C, When all present, Then load order: C, B, A
2. Given plugin A → B but B missing, When registered, Then rejected with MISSING_DEPENDENCY
3. Given circular A → B → A, When detected, Then rejected with CIRCULAR_DEPENDENCY

**Tests**: dependency_resolution_order · dependency_missing_rejected · dependency_circular_rejected · dependency_version_conflict

**Dependencies**: STORY-K04-009

---

## Feature K04-F05 — Hot-Swap Without Downtime (3 Stories)

---
### STORY-K04-011: Implement plugin hot-swap mechanism
**Feature**: K04-F05 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Upgrade plugin to new version without service restart. Steps: load new version in parallel → health check new → drain old (finish in-flight requests) → switch routing → unload old. Rollback on health check failure.

**ACs**:
1. Given plugin v1 running, When v2 deployed, Then v2 loaded in parallel, health checked, traffic switched
2. Given v2 health check fails, When detected, Then v1 remains active, v2 unloaded, alert emitted
3. Given in-flight requests on v1, When switch happens, Then drain period allows completion before unload

**Tests**: hotSwap_v2_success · hotSwap_healthFail_rollback · hotSwap_drainInFlight · hotSwap_zeroDowntime

**Dependencies**: STORY-K04-003/004/005, K-06

---
### STORY-K04-012: Implement plugin state migration
**Feature**: K04-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

When plugin version changes, support state migration. Plugin manifest declares `migration_script` for state transformation between versions. State backed up before migration. Rollback restores backup state if migration fails.

**ACs**:
1. Given v1→v2 migration script, When upgrade, Then state migrated to v2 format
2. Given migration fails, When error detected, Then state rolled back to v1 backup
3. Given no migration needed, When same state format, Then no migration executed

**Tests**: migration_success · migration_failure_rollback · migration_notNeeded_skipped · migration_stateBackup

**Dependencies**: STORY-K04-011

---
### STORY-K04-013: Implement plugin rollback
**Feature**: K04-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Support manual rollback to previous plugin version. POST `/plugins/{name}/rollback?version=N`. Reverses hot-swap: load old version, health check, switch, unload new. Requires admin authorization. PluginRolledBack event emitted.

**ACs**:
1. Given current v2, When rollback to v1 requested, Then v1 loaded, health checked, switched
2. Given rollback, When PluginRolledBack event emitted, Then audit trail records reason
3. Given v1 no longer available, When rollback requested, Then VERSION_NOT_FOUND error

**Tests**: rollback_success · rollback_event · rollback_versionNotFound · rollback_adminOnly

**Dependencies**: STORY-K04-011

---

## Feature K04-F06 — Resource Quotas & Exfiltration Prevention (2 Stories)

---
### STORY-K04-014: Implement per-plugin resource quotas
**Feature**: K04-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Enforce resource quotas per plugin instance: CPU (millicores), memory (MB), API calls per minute, payload size (KB). Quotas defined in manifest, verified against platform limits. Runtime enforcement via cgroups (T3) or sandbox limits (T2).

**ACs**:
1. Given plugin quota: memory=64MB, When usage exceeds, Then terminated with QUOTA_EXCEEDED
2. Given API rate quota: 1000/min, When exceeded, Then throttled with 429
3. Given payload size limit: 512KB, When oversized request, Then rejected

**Tests**: quota_memory · quota_apiRate · quota_payloadSize · quota_cpu · quota_enforcement

**Dependencies**: STORY-K04-003/004/005

---
### STORY-K04-015: Implement data exfiltration prevention
**Feature**: K04-F06 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Prevent plugins from exfiltrating sensitive data. For T3 (network-enabled): egress whitelist per plugin (only declared endpoints allowed). Data loss prevention (DLP) scan on outgoing requests: detect PII patterns, block if unauthorized. Alert on suspicious data patterns.

**ACs**:
1. Given T3 plugin sending data to non-whitelisted endpoint, When intercepted, Then EGRESS_BLOCKED error
2. Given outgoing request containing PII pattern (e.g., national ID), When DLP scans, Then blocked with DATA_EXFILTRATION_ALERT
3. Given suspicious pattern detected, When alert emitted, Then includes plugin name, destination, data pattern

**Tests**: exfiltration_egressWhitelist · exfiltration_dlpBlock · exfiltration_alert · exfiltration_cleanData_allowed

**Dependencies**: STORY-K04-005, K-06

---

# EPIC K-06: OBSERVABILITY STACK (22 Stories)

## Feature K06-F01 — Unified Observability SDK (3 Stories)

---
### STORY-K06-001: Implement observability SDK core with context management
**Feature**: K06-F01 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Create unified SDK entry point: `observability.init({service, version, environment})`. Manages request context: correlation_id (UUID), trace_id, span_id, tenant_id, actor_id. Express/Fastify middleware auto-extracts from headers or generates.

**ACs**:
1. Given SDK initialized, When middleware active on request, Then correlation_id generated/extracted
2. Given incoming request with X-Correlation-Id header, When processed, Then existing ID preserved
3. Given no correlation header, When request processed, Then new UUID generated and propagated

**Tests**: sdk_init · sdk_correlationExtracted · sdk_correlationGenerated · sdk_contextPropagation

**Dependencies**: None (foundational)

---
### STORY-K06-002: Implement structured logging with context enrichment
**Feature**: K06-F01 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Create logger: `observability.logger.info/warn/error/debug(message, data)`. JSON structured output. Auto-enriched with: timestamp, timestamp_bs, correlation_id, trace_id, service, level, pid. Log level configurable per service via K-02.

**ACs**:
1. Given log call, When output generated, Then JSON with all context fields
2. Given log level set to WARN via K-02, When DEBUG called, Then suppressed
3. Given sensitive data in message, When PII pattern detected, Then warning flag (actual masking in K06-F07)

**Tests**: log_structuredJson · log_contextEnriched · log_levelFiltering · log_piiWarning · log_dualTimestamp

**Dependencies**: STORY-K06-001, K-15 (BS timestamp)

---
### STORY-K06-003: Implement metrics helper library
**Feature**: K06-F01 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Create metrics helpers: `observability.metrics.counter(name, labels)`, `.histogram(name, buckets, labels)`, `.gauge(name, labels)`. Prometheus-compatible. Standard labels: service, version, tenant_id, environment. HTTP request duration auto-instrumented.

**ACs**:
1. Given counter incremented, When Prometheus scrapes, Then counter value reflects increments
2. Given HTTP request processed, When auto-instrumented, Then histogram records duration + status code
3. Given custom gauge set, When Prometheus scrapes, Then gauge value correct

**Tests**: metrics_counter · metrics_histogram · metrics_gauge · metrics_autoInstrumented · metrics_labels

**Dependencies**: STORY-K06-001

---

## Feature K06-F02 — Context Propagation (2 Stories)

---
### STORY-K06-004: Implement W3C Trace Context propagation
**Feature**: K06-F02 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Implement W3C Trace Context specification (traceparent, tracestate headers). Extract on incoming request, inject on outgoing requests (HTTP, gRPC, Kafka). All inter-service calls carry trace context. Works with OpenTelemetry collector.

**ACs**:
1. Given incoming request with traceparent header, When service calls downstream, Then same trace_id propagated
2. Given Kafka message with trace context in headers, When consumed, Then trace continues
3. Given gRPC call, When metadata carries trace context, Then trace linked

**Tests**: traceContext_http · traceContext_kafka · traceContext_grpc · traceContext_noHeader_newTrace

**Dependencies**: STORY-K06-001

---
### STORY-K06-005: Implement baggage propagation for custom context
**Feature**: K06-F02 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Implement W3C Baggage propagation for business context: tenant_id, jurisdiction, fiscal_year, user_role. Baggage travels through entire call chain without manual forwarding. Size limited (max 8KB) to prevent header bloat.

**ACs**:
1. Given baggage item tenant_id=acme, When propagated through 3 services, Then available in all 3
2. Given baggage exceeds 8KB limit, When set, Then excess items rejected with BaggageOverflow warning
3. Given sensitive baggage item, When propagated, Then excluded from external calls (internal-only flag)

**Tests**: baggage_propagation · baggage_sizeLimit · baggage_internalOnly · baggage_multipleItems

**Dependencies**: STORY-K06-004

---

## Feature K06-F03 — Prometheus Metrics + Grafana Dashboards (3 Stories)

---
### STORY-K06-006: Implement Prometheus metrics endpoint per service
**Feature**: K06-F03 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Expose GET `/metrics` endpoint on all services. Standard metrics: http_requests_total, http_request_duration_seconds, process_cpu_seconds_total, process_heap_bytes, active_connections. Service-specific metrics via SDK from K06-F01.

**ACs**:
1. Given service running, When GET /metrics called, Then Prometheus-format metrics returned
2. Given 100 requests processed, When metrics checked, Then http_requests_total=100 with status code labels
3. Given metric endpoint, When Prometheus scrapes, Then no authentication required (internal network only)

**Tests**: metrics_endpoint_returns · metrics_httpTotal_accurate · metrics_processCpu · metrics_heap · metrics_scrapeInterval

**Dependencies**: STORY-K06-003

---
### STORY-K06-007: Implement platform-wide Grafana dashboards
**Feature**: K06-F03 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha (DevOps assists)

Create Grafana dashboards: (1) Platform Overview — all services health, request rates, error rates; (2) Service Detail — per-service deep dive; (3) Infrastructure — K8s nodes, pods, CPU, memory; (4) Business Metrics — orders/sec, trades/sec, settlements. All dashboards support tenant_id variable.

**ACs**:
1. Given platform overview dashboard, When viewed, Then all 33 services visible with health indicators
2. Given service detail dashboard, When drilled into OMS, Then shows OMS-specific metrics (orders/sec, latency)
3. Given tenant filter, When selected, Then all panels filter to that tenant

**Tests**: dashboard_loads · dashboard_serviceFilter · dashboard_tenantFilter · dashboard_timeRange

**Dependencies**: STORY-K06-006, Grafana

---
### STORY-K06-008: Implement custom business metrics collection
**Feature**: K06-F03 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Define and collect business-specific metrics: orders_placed_total, orders_filled_total, settlement_value_total, risk_checks_total (pass/fail), compliance_checks_total (pass/fail), active_users_gauge. Used by business dashboards and SLO tracking.

**ACs**:
1. Given order placed, When counter incremented, Then orders_placed_total increases
2. Given business dashboard, When viewed, Then shows real-time order flow and settlement values
3. Given metrics used for SLO, When ingested, Then available for SLO calculation in K06-F06

**Tests**: bizMetric_ordersPlaced · bizMetric_riskChecks · bizMetric_settlementValue · bizMetric_activeUsers

**Dependencies**: STORY-K06-006

---

## Feature K06-F04 — Jaeger Distributed Tracing (2 Stories)

---
### STORY-K06-009: Implement OpenTelemetry tracing instrumentation
**Feature**: K06-F04 · **Points**: 3 · **Sprint**: 3 · **Team**: Alpha

Instrument all services with OpenTelemetry SDK for distributed tracing. Auto-instrument: HTTP clients/servers, gRPC, Kafka producers/consumers, PostgreSQL queries, Redis calls. Export spans to Jaeger collector via OTLP.

**ACs**:
1. Given request traversing 3 services, When traced, Then single trace with 3+ spans visible in Jaeger
2. Given PostgreSQL query in span, When traced, Then query duration and table name captured
3. Given Kafka message, When produced and consumed, Then producer and consumer linked in trace

**Tests**: trace_3service_chain · trace_postgres_spans · trace_kafka_linked · trace_redis_spans · trace_jaegerExport

**Dependencies**: STORY-K06-004, Jaeger

---
### STORY-K06-010: Implement trace sampling strategy
**Feature**: K06-F04 · **Points**: 2 · **Sprint**: 3 · **Team**: Alpha

Configure trace sampling: production = probabilistic 10% of requests + always sample errors. Staging = 100%. Per-route override: critical paths (order placement, settlement) = always sampled. Sampling decision propagated to all downstream.

**ACs**:
1. Given production 10% sampling, When 1000 requests, Then ~100 traces created
2. Given error response (5xx), When detected, Then trace always sampled regardless of rate
3. Given critical route /orders, When configured for 100%, Then always sampled

**Tests**: sampling_probabilistic · sampling_alwaysOnError · sampling_criticalRoute · sampling_propagated

**Dependencies**: STORY-K06-009

---

## Feature K06-F05 — ELK Centralized Logging (2 Stories)

---
### STORY-K06-011: Implement log shipping to Elasticsearch
**Feature**: K06-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Ship structured JSON logs from all services to Elasticsearch via Filebeat/Fluentd. Index pattern: `siddhanta-logs-{service}-{date}`. ILM policy: hot (7d) → warm (30d) → cold (90d) → delete. Log schema aligned with K06-F01.

**ACs**:
1. Given service logs to stdout, When Fluentd collects, Then indexed in Elasticsearch within 5s
2. Given ILM policy, When log ages past 90d, Then auto-deleted
3. Given Kibana search, When correlation_id queried, Then all logs across services returned

**Tests**: shipping_latency_sub5s · shipping_indexPattern · ilm_rollover · kibana_search_correlationId

**Dependencies**: STORY-K06-002, Elasticsearch, Kibana

---
### STORY-K06-012: Implement Kibana dashboards and saved searches
**Feature**: K06-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Create Kibana dashboards: (1) Error Rate by service; (2) Slow Queries (logs with duration > threshold); (3) Security Events; (4) Audit Trail viewer. Saved searches for common queries: correlation_id lookup, error aggregation, PII detection flags.

**ACs**:
1. Given error rate dashboard, When viewed, Then shows error count per service over time
2. Given correlation_id, When searched in Kibana, Then returns all related log entries across services
3. Given security events saved search, When filtered, Then shows login failures, access denials, anomalies

**Tests**: kibana_errorRate · kibana_correlationSearch · kibana_securityEvents · kibana_savedSearches

**Dependencies**: STORY-K06-011

---

## Feature K06-F06 — SLO/SLA Framework & Error Budgets (3 Stories)

---
### STORY-K06-013: Implement SLO definition and tracking
**Feature**: K06-F06 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Define SLOs per service: availability (e.g., 99.99%), latency (e.g., P99 < 5ms for risk checks), error rate (< 0.1%). SLO stored in config (K-02). Prometheus recording rules compute SLO compliance. Dashboard shows current SLO status.

**ACs**:
1. Given SLO: availability 99.99% for OMS, When measured over 30d window, Then 99.99% means ≤ 4.32min downtime
2. Given latency SLO: P99 < 5ms for D-06 risk check, When measured, Then shows compliance/violation
3. Given SLO violated, When threshold crossed, Then SloViolation alert emitted

**Tests**: slo_availability_calculation · slo_latency_p99 · slo_violation_alert · slo_definition_config · slo_dashboard

**Dependencies**: STORY-K06-006, K-02

---
### STORY-K06-014: Implement error budget calculation and tracking
**Feature**: K06-F06 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Calculate error budgets: 99.99% SLO → 0.01% error budget → 4.32 minutes/month. Track burn rate: if burning faster than linear → alert. Burn rate > 2x → page on-call. Budget exhausted → feature freeze, focus on reliability.

**ACs**:
1. Given 99.99% SLO in 30-day window, When 2 minutes downtime occurred, Then budget remaining: 2.32 minutes
2. Given burn rate 5x normal, When detected, Then critical alert to on-call
3. Given budget exhausted, When 0 remaining, Then ErrorBudgetExhausted event emitted

**Tests**: errorBudget_calculation · errorBudget_burnRate · errorBudget_exhausted · errorBudget_alert_5x

**Dependencies**: STORY-K06-013

---
### STORY-K06-015: Implement SLA reporting for tenants
**Feature**: K06-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Generate per-tenant SLA reports: availability, latency percentiles, error rates, incident counts. Monthly report auto-generated. Export as PDF. Shows SLA compliance and any violations. Used for contractual SLA tracking.

**ACs**:
1. Given monthly report generation, When triggered, Then per-tenant SLA report generated
2. Given report, When exported as PDF, Then includes availability chart, latency distribution, incidents
3. Given SLA violation in period, When reported, Then highlighted with impact duration

**Tests**: slaReport_monthly · slaReport_pdf · slaReport_perTenant · slaReport_violation_highlighted

**Dependencies**: STORY-K06-013

---

## Feature K06-F07 — PII Detection & Masking (2 Stories)

---
### STORY-K06-016: Implement PII detection rules
**Feature**: K06-F07 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Define PII detection patterns: email (regex), phone (regex), national ID (pattern per jurisdiction), credit card (Luhn + pattern), name+DOB combinations. Apply to log fields before shipping. Classification: HIGH (national ID, CC), MEDIUM (email, phone), LOW (name).

**ACs**:
1. Given log containing email address, When detection runs, Then field classified as PII_MEDIUM
2. Given log containing national ID, When detection runs, Then field classified as PII_HIGH
3. Given detection patterns configurable via K-02, When new pattern added, Then detected in future logs

**Tests**: pii_email_detected · pii_nationalId_detected · pii_creditCard_detected · pii_noMatch_clean · pii_configurable

**Dependencies**: K-02

---
### STORY-K06-017: Implement PII masking in logs and traces
**Feature**: K06-F07 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Mask PII in logs and trace spans before export. Masking strategies: REDACT (replace with [REDACTED]), HASH (SHA-256 — allows correlation without exposure), PARTIAL (show last 4 digits). Strategy configurable per PII classification level.

**ACs**:
1. Given PII_HIGH field (national ID), When masked with REDACT strategy, Then "[REDACTED]" in log
2. Given PII_MEDIUM field (email), When masked with HASH, Then SHA-256 hash in log (correlatable)
3. Given masking applied, When original log searched, Then PII not findable in Elasticsearch

**Tests**: mask_redact · mask_hash · mask_partial · mask_beforeShipping · mask_traceSpans · mask_strategyPerLevel

**Dependencies**: STORY-K06-016, STORY-K06-011

---

## Feature K06-F08 — Alerting Engine (2 Stories)

---
### STORY-K06-018: Implement alerting rules engine
**Feature**: K06-F08 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Define alerting rules via Prometheus Alertmanager: condition (PromQL expression), severity (INFO/WARNING/CRITICAL/PAGE), notification target (Slack, email, PagerDuty). Standard alerts: service down, high error rate, high latency, SLO violation, circuit breaker open.

**ACs**:
1. Given error rate > 5% for 5 minutes, When triggered, Then CRITICAL alert to on-call
2. Given service down (health check fails), When detected within 30s, Then PAGE alert
3. Given alert resolved, When condition clears, Then resolution notification sent

**Tests**: alert_errorRate · alert_serviceDown · alert_resolved · alert_dedup · alert_routing

**Dependencies**: STORY-K06-006, Alertmanager

---
### STORY-K06-019: Implement alert routing and escalation
**Feature**: K06-F08 · **Points**: 2 · **Sprint**: 4 · **Team**: Alpha

Route alerts based on severity and service: CRITICAL → PagerDuty + Slack #incidents; WARNING → Slack #alerts; INFO → Slack #monitoring. Escalation: if CRITICAL unacknowledged for 15 min → escalate to engineering lead. On-call rotation integration.

**ACs**:
1. Given CRITICAL alert, When emitted, Then PagerDuty page + Slack notification within 30s
2. Given unacknowledged CRITICAL for 15 min, When escalation triggers, Then engineering lead paged
3. Given on-call schedule, When alert routed, Then goes to current on-call engineer

**Tests**: routing_critical · routing_warning · routing_info · escalation_15min · oncall_routing

**Dependencies**: STORY-K06-018

---

## Feature K06-F09 — AIOps Intelligence (3 Stories)

---
### STORY-K06-020: Implement ML-based metric anomaly detection and forecasting
**Feature**: K06-F09 · **Points**: 5 · **Sprint**: 4 · **Team**: Alpha

Deploy unsupervised ML anomaly detection (Isolation Forest + LSTM autoencoder) on all Prometheus time-series metrics. Dynamic baselines learnt per metric per time-of-day and day-of-week (seasonality-aware). Anomaly score 0–1 emitted per metric window; score > 0.8 = alert. Forecasting: 2-hour metric value prediction using LSTM to enable proactive alerting before threshold breach. Model governed by K-09 (registry, drift detection, HITL). AnomalyDetected and ForecastBreachPredicted events.

**ACs**:
1. Given a metric (e.g., order_processing_latency_p99) exhibiting a sudden spike, When anomaly detector scores it, Then score > 0.8 triggers AnomalyDetected event with feature contributions (SHAP) within 30s
2. Given LSTM forecast predicts metric will breach SLO threshold within 60 minutes, When ForecastBreachPredicted emitted, Then on-call engineer receives proactive alert before actual breach
3. Given model drift detected (PSI > 0.2 on metric distributions), When K-09 signals drift, Then retraining pipeline triggered automatically; previous model continues serving until new model passes validation

**Tests**: anomaly_isolation_forest · anomaly_lstm_autoencoder · baseline_seasonality · shap_feature_contributions · forecast_60min · proactive_alert · drift_retrain · perf_scoring_under_100ms_per_metric

**Dependencies**: K06-F01 (metrics), K-09 (AI governance — K09-001, K09-009), K-05

---
### STORY-K06-021: Implement log intelligence clustering and semantic search
**Feature**: K06-F09 · **Points**: 3 · **Sprint**: 4 · **Team**: Alpha

Apply NLP to centralized logs (ELK) to cluster recurring log patterns, surface novel log signatures, and enable semantic search. Log vectorization: sentence-transformer embeddings (e.g., `all-MiniLM-L6-v2`) for log messages → stored in vector index (pgvector/Weaviate). Clustering: HDBSCAN on embeddings to group structurally similar log lines. Novel pattern detection: cosine distance > threshold from all known clusters → alert. Semantic search API: `GET /observability/logs/search?q=<natural_language_query>` returns ranked relevant log entries using vector similarity. Volume: handles 1M log events/day.

**ACs**:
1. Given a natural language query "settlement failed with timeout", When semantic search executes, Then top-10 most semantically relevant log lines returned ranked by similarity score, including non-exact-keyword matches (e.g., "settlement processing exceeded deadline")
2. Given a novel log pattern not seen in the last 7 days, When clustering runs, Then NovelLogPatternDetected event emitted with representative sample and cluster members
3. Given 1M log events ingested in a day, When clustering completes, Then known log pattern groups updated within 15 minutes, performance overhead < 5% on ELK cluster

**Tests**: semantic_search_relevance · semantic_search_no_keyword_match · novel_pattern_detection · cluster_grouping_accuracy · embedding_pipeline · vector_index_build · perf_1M_logs_under_15min · perf_search_sub_500ms

**Dependencies**: K06-F05 (ELK logging), K-09 (AI governance), pgvector/Weaviate

---
### STORY-K06-022: Implement intelligent alert noise reduction and event correlation
**Feature**: K06-F09 · **Points**: 5 · **Sprint**: 4 · **Team**: Alpha

Reduce alert noise via ML alert grouping, deduplication, and root cause inference. Grouping: alerts within a 5-minute sliding window across correlated services (dependency graph from K-08 lineage) merged into a single incident entity. Deduplication: embedding-similarity dedup (cosine > 0.92 = same alert). Root cause inference: graph traversal of K-08 service dependency graph + temporal ordering to propose likely root cause service. Suppression: if 80%+ of alerts are downstream symptoms of one root cause, suppress children and surface root cause only. Result: SRE sees one correlated incident with root cause hypothesis vs dozens of raw alert notifications. All correlation logic governed and explainable via K-09.

**ACs**:
1. Given a database connectivity failure causing 15 downstream service alerts within 2 minutes, When correlation runs, Then all 15 alerts merged into one incident with root cause = "database connectivity" and K-08 dependency subgraph shown
2. Given the same alert firing 8 times in 10 minutes (flapping metric), When deduplication runs, Then single deduplicated alert issued; original alert count shown as metadata
3. Given root cause inference for an incident, When SRE views it, Then SHAP-style feature contribution showing which signals most influenced root cause prediction, with confidence score

**Tests**: alert_grouping_5min_window · dedup_embedding_similarity · root_cause_inference · symptom_suppression · dependency_graph_traversal · shap_explanation · perf_correlation_under_5sec · flapping_detection

**Dependencies**: STORY-K06-018, K-08 (lineage graph), K-09 (AI governance — K09-004)

---

# EPIC K-11: API GATEWAY (13 Stories)

## Feature K11-F01 — Request Routing & Service Discovery (3 Stories)

---
### STORY-K11-001: Implement Envoy/Istio-based API Gateway with service registry
**Feature**: K11-F01 · **Points**: 3 · **Sprint**: 3 · **Team**: Zeta

Configure API Gateway (K-11) using Istio Ingress Gateway (Envoy-based) with dynamic service registry backed by Kubernetes service discovery. Routes: `/api/v1/{service}/{path}` → upstream service. Health-check-based routing: remove unhealthy upstreams. WebSocket proxy support.

**ACs**:
1. Given request to /api/v1/orders/123, When routed, Then reaches OMS service
2. Given upstream service fails health check, When detected, Then removed from routing table
3. Given WebSocket upgrade request, When processed, Then proxied to appropriate service

**Tests**: routing_basic · routing_healthCheck_removal · routing_webSocket · routing_serviceDiscovery · routing_loadBalancing

**Dependencies**: Kubernetes, Istio/Envoy

---
### STORY-K11-002: Implement API versioning strategy
**Feature**: K11-F01 · **Points**: 2 · **Sprint**: 3 · **Team**: Zeta

Implement URL-based API versioning: `/api/v1/`, `/api/v2/`. Version routing at gateway level. Deprecation headers (Sunset, Deprecation) for old versions. Multiple versions can coexist. Default to latest if version omitted (configurable).

**ACs**:
1. Given /api/v1/orders, When called, Then routes to v1 handler
2. Given /api/v2/orders, When called, Then routes to v2 handler
3. Given deprecated v1 endpoint, When called, Then response includes Sunset header with deprecation date

**Tests**: versioning_v1 · versioning_v2 · versioning_deprecated_header · versioning_default_latest

**Dependencies**: STORY-K11-001

---
### STORY-K11-003: Implement request/response transformation
**Feature**: K11-F01 · **Points**: 2 · **Sprint**: 4 · **Team**: Zeta

Gateway-level request/response transformation: add correlation_id header if missing, inject tenant_id from auth context, strip internal headers on egress, add security headers (CSP, HSTS, X-Content-Type-Options). Standard response envelope: {data, meta, errors}.

**ACs**:
1. Given request without X-Correlation-Id, When processed by gateway, Then header added with new UUID
2. Given response from service, When returned to client, Then internal headers stripped, security headers added
3. Given error response, When wrapped, Then standard envelope {errors: [{code, message, details}]}

**Tests**: transform_addCorrelation · transform_stripInternal · transform_securityHeaders · transform_errorEnvelope

**Dependencies**: STORY-K11-001, STORY-K06-001

---

## Feature K11-F02 — JWT + mTLS Authentication (2 Stories)

---
### STORY-K11-004: Implement JWT validation at gateway
**Feature**: K11-F02 · **Points**: 2 · **Sprint**: 3 · **Team**: Zeta

Validate JWT on every incoming request at gateway level. Verify RS256 signature against JWKS endpoint (K-01). Check exp, nbf (30s tolerance), iss, aud. Extract tenant_id, roles, permissions → inject into upstream headers.

**ACs**:
1. Given valid JWT, When request processed, Then upstream receives X-Tenant-Id, X-User-Id, X-Roles headers
2. Given expired JWT, When validated, Then 401 with token_expired error
3. Given malformed JWT, When validated, Then 401 with invalid_token error

**Tests**: jwt_valid_injectsHeaders · jwt_expired_401 · jwt_malformed_401 · jwt_wrongIssuer_401 · jwt_jwksRefresh

**Dependencies**: STORY-K01-001 (JWKS endpoint), K-14

---
### STORY-K11-005: Implement mTLS termination and client cert validation
**Feature**: K11-F02 · **Points**: 2 · **Sprint**: 4 · **Team**: Zeta

Terminate mTLS at gateway for external clients requiring certificate-based auth. Validate client certificate against trusted CA list. Extract CN and organization → map to service identity. Required for FIX gateway and regulator portal connections.

**ACs**:
1. Given valid client certificate, When mTLS handshake completes, Then request forwarded with X-Client-CN header
2. Given untrusted certificate, When handshake attempted, Then TLS error (no HTTP response)
3. Given certificate expired, When presented, Then connection rejected

**Tests**: mtls_validCert · mtls_untrustedCA_rejected · mtls_expiredCert · mtls_cnExtraction

**Dependencies**: STORY-K11-001, K-14 (CA certificates)

---

## Feature K11-F03 — Rate Limiting (2 Stories)

---
### STORY-K11-006: Implement tenant-aware token bucket rate limiting
**Feature**: K11-F03 · **Points**: 3 · **Sprint**: 3 · **Team**: Zeta

Implement rate limiting using Redis-backed token bucket. Limits configurable per tenant, per endpoint, global. Tenant limits from K-02. Response headers: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset. 429 Too Many Requests when exceeded.

**ACs**:
1. Given tenant limit 100 req/min, When 101st request, Then 429 with rate limit headers
2. Given global limit 10000/min, When exceeded, Then 429 for all tenants
3. Given per-endpoint limit (/orders: 50/min), When exceeded on /orders, Then other endpoints unaffected

**Tests**: rateLimit_tenantExceeded · rateLimit_global · rateLimit_perEndpoint · rateLimit_headers · rateLimit_redis

**Dependencies**: K-02 (tenant config), Redis

---
### STORY-K11-007: Implement rate limit bypass for internal services
**Feature**: K11-F03 · **Points**: 2 · **Sprint**: 4 · **Team**: Zeta

Internal service-to-service calls (identified by mTLS cert or service JWT) bypass tenant rate limits. Separate internal rate limit (higher, configurable). Prevents internal operations from being throttled by shared tenant limits.

**ACs**:
1. Given internal service call (service JWT), When rate limit checked, Then tenant limit bypassed
2. Given internal rate limit 10000/min, When exceeded, Then internal 429 (separate from tenant)
3. Given external client, When internal bypass attempted via header spoofing, Then detected and rejected

**Tests**: internal_bypass_tenantLimit · internal_ownLimit · internal_spoofPrevented

**Dependencies**: STORY-K11-006, STORY-K01-016

---

## Feature K11-F04 — Jurisdiction-Aware Routing (2 Stories)

---
### STORY-K11-008: Implement jurisdiction-based request routing
**Feature**: K11-F04 · **Points**: 3 · **Sprint**: 4 · **Team**: Zeta

Route requests to jurisdiction-specific service instances based on tenant's jurisdiction (extracted from JWT). Jurisdiction→cluster mapping from K-02. Used for data residency compliance — NP requests stay in NP cluster.

**ACs**:
1. Given NP-jurisdiction tenant, When request received, Then routed to NP service cluster
2. Given IN-jurisdiction tenant, When request received, Then routed to IN cluster
3. Given misrouted request (NP request to IN cluster), When detected, Then re-routed with DataResidencyViolation alert

**Tests**: routing_npToNpCluster · routing_inToInCluster · routing_misroute_alert · routing_configDriven

**Dependencies**: K-02 (jurisdiction config), STORY-K11-004

---
### STORY-K11-009: Implement geo-routing header injection
**Feature**: K11-F04 · **Points**: 2 · **Sprint**: 4 · **Team**: Zeta

Inject X-Jurisdiction, X-Data-Residency-Zone headers based on tenant mapping. Services use these headers for jurisdiction-aware processing (e.g., compliance rules, calendar selection, regulatory format). Headers validated — cannot be spoofed by client.

**ACs**:
1. Given NP tenant request, When gateway processes, Then X-Jurisdiction: NP, X-Data-Residency-Zone: NP-1 headers injected
2. Given client sends X-Jurisdiction header, When gateway processes, Then client header overwritten (no spoofing)
3. Given unknown jurisdiction, When encountered, Then DEFAULT zone applied, alert emitted

**Tests**: header_injection · header_antiSpoof · header_unknownJurisdiction · header_usedByServices

**Dependencies**: STORY-K11-008

---

## Feature K11-F05 — WAF Integration (2 Stories)

---
### STORY-K11-010: Implement OWASP Top 10 protection rules
**Feature**: K11-F05 · **Points**: 3 · **Sprint**: 4 · **Team**: Zeta

Configure WAF (ModSecurity/Envoy WASM filter) with OWASP CRS rules: SQL injection, XSS, CSRF, path traversal, RFI/LFI, command injection. Block and log malicious requests. False positive tuning for API endpoints.

**ACs**:
1. Given SQL injection attempt in query param, When WAF evaluates, Then blocked with 403, logged
2. Given XSS payload in request body, When detected, Then blocked with 403
3. Given legitimate API request triggering false positive, When tuned, Then allowed after rule exception

**Tests**: waf_sqlInjection · waf_xss · waf_pathTraversal · waf_falsePositive_tuning · waf_logging

**Dependencies**: STORY-K11-001

---
### STORY-K11-011: Implement request body size and payload validation
**Feature**: K11-F05 · **Points**: 2 · **Sprint**: 4 · **Team**: Zeta

Enforce request body size limits: default 1MB, configurable per endpoint (file upload: 50MB). Content-Type validation: reject unexpected content types. JSON depth limit: max 20 levels. Request timeout at gateway: default 30s.

**ACs**:
1. Given request body > 1MB (default limit), When received, Then 413 Payload Too Large
2. Given Content-Type: text/xml on JSON-only endpoint, When received, Then 415 Unsupported Media Type
3. Given deeply nested JSON (>20 levels), When parsed, Then rejected with 400

**Tests**: bodySize_exceed_413 · bodySize_perEndpoint · contentType_mismatch · jsonDepth_limit · timeout_gateway

**Dependencies**: STORY-K11-001

---

## Feature K11-F06 — OpenAPI Schema Validation (2 Stories)

---
### STORY-K11-012: Implement request schema validation at gateway
**Feature**: K11-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Zeta

Validate incoming requests against service OpenAPI schemas at gateway level. Schema loaded per service from schema registry. Validate: required fields, field types, enum values, string patterns. Return 400 with specific field-level errors.

**ACs**:
1. Given POST /orders missing required field "instrument_id", When validated, Then 400 with field-level error
2. Given quantity: "abc" (expected number), When validated, Then 400 with type mismatch error
3. Given valid request, When validated, Then passes through to upstream service

**Tests**: schema_missingField · schema_typeMismatch · schema_valid_passes · schema_enumValidation · schema_perService

**Dependencies**: STORY-K11-001

---
### STORY-K11-013: Implement schema registry and version management
**Feature**: K11-F06 · **Points**: 2 · **Sprint**: 4 · **Team**: Zeta

Schema registry: services register their OpenAPI schemas at startup/deployment. Gateway fetches and caches schemas. Schema versioned per API version. Auto-refresh on deployment events. Fallback: if schema not found → skip validation, warn.

**ACs**:
1. Given service deployed, When OpenAPI schema pushed, Then gateway caches for validation
2. Given schema for v2 registered, When v1 request validated, Then v1 schema used
3. Given schema not found, When request arrives, Then validation skipped with WARNING logged

**Tests**: registry_push · registry_cache · registry_version · registry_fallback_skip · registry_refresh

**Dependencies**: STORY-K11-012

---

# MILESTONE 1B SUMMARY

| Epic | Feature Count | Story Count | Total SP |
|------|--------------|-------------|----------|
| K-01 IAM | 8 | 23 | 62 |
| K-14 Secrets Management | 6 | 14 | 35 |
| K-16 Ledger Framework | 7 | 19 | 50 |
| K-17 Distributed Transaction Coordinator | 5 | 14 | 36 |
| K-18 Resilience Patterns | 6 | 13 | 28 |
| K-03 Policy / Rules Engine | 6 | 14 | 30 |
| K-04 Plugin Runtime & SDK | 6 | 15 | 33 |
| K-06 Observability Stack | 9 | 22 | 57 |
| K-11 API Gateway | 6 | 13 | 30 |
| **TOTAL** | **59** | **147** | **361** |

**Sprint 3**: K-01 (001-008,017,022), K-14 (001-006), K-16 (001-003,005-006,010,016,018), K-17 (001-003), K-18 (001-007), K-06 (001-006,009-010), K-11 (001-002,004,006) (~72 stories)
**Sprint 4**: K-01 (009-016,018-021,023), K-14 (007-014), K-16 (004,007-009,011-015,017,019), K-17 (004-014), K-18 (008-013), K-03 (001-014), K-04 (001-015), K-06 (007-008,011-022), K-11 (003,005,007-013) (~75 stories — includes AIOps K06-020 to K06-022)
