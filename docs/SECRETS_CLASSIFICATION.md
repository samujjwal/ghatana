# Ghatana Secrets Classification and Inventory

**Status:** Active  
**Last Updated:** 2026-04-23  
**Owner:** Platform Security Team  
**Slack:** #platform-security  
**Review Cadence:** Quarterly  

---

## 1. Purpose

This document classifies all secret types used across the Ghatana monorepo, inventories all
generated cryptographic material (certificates, keystores, signing keys), defines rotation
schedules, and records the approved secret storage patterns.

Every engineer touching secrets must read and follow this document. CI gate
`check-secret-classification.sh` enforces that no new secret patterns bypass this policy.

---

## 2. Secret Classification Tiers

| Tier | Label | Description | Examples |
|------|-------|-------------|---------|
| T0 | **Critical** | Compromise grants full system access or customer data exfiltration | JWT root signing key, DB master credentials, TLS CA private key |
| T1 | **High** | Compromise grants product-scoped access or significant data exposure | Per-product JWT signing keys, DB user passwords, Redis passwords |
| T2 | **Medium** | Compromise grants limited or scoped API access | External service API keys, AI model endpoint tokens, SMTP credentials |
| T3 | **Low** | Public or non-sensitive configuration sometimes co-located with secrets | Public TLS certificates, JWKS public keys, non-sensitive config flags |

---

## 3. Inventory: Generated Certificates and Keystores

All generated cryptographic material must be listed here. Files must never be committed to
version control unless explicitly annotated as **public only**.

### 3.1 TLS / mTLS Material

| Location | Type | Contents | Tier | Owner | Rotation |
|----------|------|----------|------|-------|---------|
| `platform/java/database/src/main/java/com/ghatana/cache/security/RedisTlsConfig.java` | Java Config | `KeyStore` / `TrustStore` paths; loaded at runtime from environment | T0 | Platform DB Team | 365 days |
| `shared-services/auth-gateway` | Runtime TLS | TLS certificate + key for HTTPS termination; injected at deploy time | T0 | Auth Platform Team | 365 days (auto-renew via cert-manager) |
| `products/audio-video` | Service TLS | Per-service TLS certs for gRPC channel encryption | T1 | AV Team | 365 days |

**Rule:** Keystores and private key PEM files must NEVER be committed. Use environment injection
or vault secrets at startup. Paths must point to a secure mount, not a relative classpath resource.

### 3.2 JWT Signing Keys

| Module | Key Type | Storage | Tier | Rotation |
|--------|----------|---------|------|---------|
| `platform/java/security/src/main/java/com/ghatana/platform/security/jwt/JwtKeyManager.java` | HMAC-SHA-256 (rotating ring) | Runtime — bootstrapped from `JWT_SIGNING_SECRET` env var; rotated in-process via `JwtKeyManager.rotate()` | T0 | Platform Security | 90 days scheduled; immediate on compromise |
| `shared-services/auth-gateway` | HMAC-SHA-256 | Injected via `JWT_SECRET` env var; managed by `JwtKeyManager` | T0 | Auth Team | 90 days |
| `products/aep` | Product-scoped JWT | Inherits platform JWT via token exchange endpoint | T1 | AEP Team | Inherits parent rotation |
| `products/data-cloud` | Product-scoped JWT | Inherits platform JWT via token exchange endpoint | T1 | Data Cloud Team | Inherits parent rotation |

**Rule:** JWT signing secrets must be ≥ 256 bits (32 bytes). `JwtKeyManager` enforces this at
construction. Rotation must keep the previous key active for at least the `retentionSeconds`
window to allow in-flight token validation.

### 3.3 Database Credentials

| Module | Secret Names | Storage | Tier | Rotation |
|--------|-------------|---------|------|---------|
| `platform/java/database` | `DB_URL`, `DB_USER`, `DB_PASSWORD` | Environment variable injection; never in code | T0 | Platform DB Team | 90 days |
| `products/data-cloud` | `DATACLOUD_DB_URL`, `DATACLOUD_DB_USER`, `DATACLOUD_DB_PASSWORD` | Environment injection | T0 | Data Cloud Team | 90 days |
| `products/aep` | `AEP_DB_URL`, `AEP_DB_USER`, `AEP_DB_PASSWORD` | Environment injection | T0 | AEP Team | 90 days |
| `products/yappc` | `YAPPC_DB_URL`, `YAPPC_DB_USER`, `YAPPC_DB_PASSWORD` | Environment injection | T0 | YAPPC Team | 90 days |
| `shared-services` | `SHARED_DB_URL`, `SHARED_DB_USER`, `SHARED_DB_PASSWORD` | Environment injection | T0 | Platform Team | 90 days |

### 3.4 Cache (Redis) Credentials

| Module | Secret Names | Storage | Tier | Rotation |
|--------|-------------|---------|------|---------|
| `platform/java/database` (`RedisTlsConfig`) | `REDIS_PASSWORD`, `REDIS_TLS_KEYSTORE_PATH`, `REDIS_TLS_KEYSTORE_PASSWORD`, `REDIS_TLS_TRUSTSTORE_PATH`, `REDIS_TLS_TRUSTSTORE_PASSWORD` | Environment injection | T0 | Platform DB Team | 90 days |

**Rule:** Redis in production must use TLS 1.3 + password auth. `RedisTlsConfig` validates this
at startup. Never use Redis without authentication in non-local environments.

### 3.5 Agent / AI Endpoint Secrets

| Module | Secret Names | Storage | Tier | Rotation |
|--------|-------------|---------|------|---------|
| `platform/java/agent-core` (`SecretProvider`) | Arbitrary agent credential names scoped by `(tenantId, secretName)` | In-memory (`InMemorySecretProvider`) for tests; production uses external vault-backed implementation | T1–T2 | Platform Agent Team | Tenant-defined; minimum 90 days |
| `products/yappc` | `OLLAMA_API_KEY`, `AI_INFERENCE_URL` | Environment injection | T2 | YAPPC Team | 90 days |
| `products/audio-video` | `AI_INFERENCE_URL` | Environment injection (see `AiInferenceClient`) | T2 | AV Team | 90 days |

### 3.6 External Service Credentials

| Service | Secret Names | Tier | Rotation |
|---------|-------------|------|---------|
| Container registry | `CONTAINER_REGISTRY_TOKEN` | T2 | 60 days |
| CI/CD pipelines | `CI_DEPLOY_TOKEN`, `GITEA_TOKEN` | T2 | 60 days |
| SMTP / notification | `SMTP_PASSWORD`, `NOTIFICATION_API_KEY` | T2 | 90 days |
| Security scanning (CodeQL, Trivy, TruffleHog) | `SEMGREP_TOKEN`, `SNYK_TOKEN` | T3 | 60 days |

---

## 4. Storage Policies

### 4.1 Approved Storage Backends by Tier

| Tier | Production | Staging | CI | Local Dev |
|------|-----------|---------|-----|-----------|
| T0 | Vault (KV v2, audited) | Vault | Short-lived Vault lease injected by runner | `.env.local` (never committed) |
| T1 | Vault or secrets manager | Vault | Short-lived Vault lease | `.env.local` |
| T2 | Vault, secrets manager, or CI secrets | CI secrets | CI secrets | `.env.local` |
| T3 | Environment variable or config file | Environment variable | Environment variable | Environment variable |

### 4.2 Forbidden Patterns

The following patterns are forbidden and will cause CI to fail (enforced by TruffleHog scan):

- Secrets hardcoded in `.java`, `.kt`, `.ts`, `.tsx`, `.py`, or `.rs` source files
- Secrets committed to `.properties`, `.yml`, `.yaml`, `.env`, `.env.*` (except `.env.example`)
- Private key PEM blocks (`-----BEGIN PRIVATE KEY-----`) in any tracked file
- Base64-encoded secrets in tracked configuration files
- JWT secrets in `application.properties` or `application.yml`

---

## 5. Rotation Procedures

### 5.1 Scheduled Rotation

| Secret Class | Frequency | Responsible Team | Automation |
|-------------|-----------|-----------------|-----------|
| T0 JWT signing keys | 90 days | Platform Security | `JwtKeyManager.rotate()` called by scheduler |
| T0 DB credentials | 90 days | Platform DB | DB credential rotation via vault dynamic secrets |
| T0 TLS certificates | 365 days | Platform Infra | cert-manager auto-renewal |
| T1 Product JWT keys | 90 days (inherits) | Product teams | Triggered by platform rotation |
| T2 API keys | 90 days | Owning team | Manual with vault versioning |
| T2 CI tokens | 60 days | DevOps | Automated via CI config |

### 5.2 Emergency Rotation (Compromise Response)

When a secret is suspected compromised:

1. **Immediately revoke** the credential at the source (vault revoke, API key invalidation)
2. **Rotate all downstream secrets** that may have been exposed
3. **Invalidate all in-flight sessions/tokens** that depended on the secret
4. **Capture audit evidence**: who accessed the secret, when, and from where
5. **Run `JwtKeyManager.rotate()`** or equivalent for in-process keys within 15 minutes
6. **File a security incident** and notify #platform-security
7. **Post-incident review** within 48 hours; update this document

### 5.3 JWT Key Rotation: In-Process Procedure

`JwtKeyManager` maintains a rotating ring of HMAC-SHA-256 keys:

```java
// Scheduled rotation (e.g., daily or weekly):
jwtKeyManager.rotate();  // generates new key, retires old key after retentionSeconds

// Monitor via metric: jwt.key.rotation.count
// Alert if no rotation in > 95 days
```

Keys past `retentionSeconds` are automatically pruned. Ensure `retentionSeconds` is greater than
the maximum token lifetime to avoid invalidating valid in-flight tokens during rotation.

---

## 6. Secret Validation at Startup

All services must validate required secrets at startup and fail fast with a clear error message.

### 6.1 Required Pattern (Java)

```java
String jwtSecret = Objects.requireNonNull(
    System.getenv("JWT_SIGNING_SECRET"),
    "JWT_SIGNING_SECRET environment variable must be set"
);
if (jwtSecret.length() < 32) {
    throw new IllegalStateException("JWT_SIGNING_SECRET must be at least 32 characters");
}
```

### 6.2 Required Pattern (TypeScript)

```typescript
const jwtSecret = process.env.JWT_SIGNING_SECRET;
if (!jwtSecret || jwtSecret.length < 32) {
  throw new Error('JWT_SIGNING_SECRET environment variable must be set and at least 32 characters');
}
```

---

## 7. CI Enforcement

The following CI gates protect against secret leakage:

| Gate | Workflow | Trigger |
|------|----------|---------|
| TruffleHog secret scan | `.github/workflows/security-ci.yml` | Every PR and push to main |
| OWASP dependency-check | `.github/workflows/security-ci.yml` | Every PR |
| Hardcoded credential regex | `.github/workflows/security-ci.yml` | Every PR |
| Vault secret rotation reminder | Scheduled (weekly) | `secrets-rotation-reminder.yml` |

---

## 8. `.env.example` Convention

Each service root must provide a `.env.example` file documenting required environment variables
with safe placeholder values. Production secrets must never appear in `.env.example`.

Example (from `shared-services/.env.example`):

```
# JWT signing secret — set in vault for staging/production
JWT_SIGNING_SECRET=<set-in-vault>

# Database
DB_URL=jdbc:postgresql://localhost:5432/ghatana_shared
DB_USER=<set-in-vault>
DB_PASSWORD=<set-in-vault>
```

---

## 9. Related Documents

- `docs/AI_GOVERNANCE_CONTRACTS.md` — AI model credential governance
- `docs/architecture/PROPAGATION_CONTRACTS.md` — tenant/auth propagation contracts
- `platform/java/security/` — `JwtKeyManager`, `JwtTokenProvider`, security utilities
- `platform/java/agent-core/src/.../SecretProvider.java` — agent-scoped secret abstraction
- `shared-services/auth-gateway/` — token issuance, rotation, and revocation
- `products/phr/docs/03_architecture/phr_secrets_management_playbook.md` — PHR-specific secrets playbook (reference pattern for other products)
