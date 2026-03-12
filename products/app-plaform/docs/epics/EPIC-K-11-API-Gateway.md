EPIC-ID: EPIC-K-11
EPIC NAME: Unified API Gateway
LAYER: KERNEL
MODULE: K-11 API Gateway
VERSION: 1.1.1

---

#### Section 1 — Objective

Deploy a Unified API Gateway (K-11) acting as the single, secure entry point for all domain subsystem APIs and external integrations. This epic implements Principle 12 (Single Pane of Glass) by ensuring that all routing, authentication, rate-limiting, and request-level observability are handled centrally, rather than being duplicated within individual domain modules.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Central API routing and versioning.
  2. Integration with K-01 IAM for authentication (token validation) and initial authorization checks.
  3. Dynamic rate-limiting and quota management per tenant/API key.
  4. Automatic registration of domain module API surfaces at startup.
  5. Extension points for Jurisdiction Plugins to contribute custom routing rules.
- **Out-of-Scope:**
  1. Deep payload inspection or business logic validation (handled by domain modules and K-03 Rules Engine).
- **Dependencies:** EPIC-K-01 (IAM), EPIC-K-02 (Config Engine), EPIC-K-06 (Observability)
- **Kernel Readiness Gates:** N/A
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Single Entry Point:** The gateway must route all inbound traffic (REST, GraphQL, gRPC, WebSockets) to the appropriate internal domain module.
2. **FR2 Auth Validation:** The gateway must validate JWTs and API keys via K-01 before forwarding requests. Unauthenticated requests are rejected immediately.
3. **FR3 Rate Limiting:** Apply configurable rate limits and quotas based on tenant tier and client identity.
4. **FR4 Dynamic Routing:** Domain modules must register their routes dynamically at startup; the gateway updates its routing table without downtime.
5. **FR5 Custom Jurisdiction Routing:** T1 Config Packs can inject routing overrides (e.g., routing specific endpoints to jurisdiction-specific adapter services).
6. **FR6 Telemetry Injection:** The gateway must inject correlation IDs (`trace_id`) and record entry/exit metrics via K-06 Observability.
7. **FR7 Request Size Limits:** The gateway must enforce configurable max request payload sizes: (a) API requests: default 10MB, (b) File uploads: default 100MB, (c) WebSocket frames: default 1MB. Oversized requests must be rejected with HTTP 413 Payload Too Large before being forwarded to backend services. Limits are configurable per route and per tenant via K-02 Config Engine. Repeated oversized requests from the same source trigger rate limiting escalation. [ARB P1-14]
8. **FR8 Request Schema Validation:** The gateway may optionally validate request payloads against registered API schemas before forwarding, rejecting malformed requests early to protect backend services.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The gateway engine itself is jurisdiction-agnostic.
2. **Jurisdiction Plugin:** Routing rules (e.g., specific endpoints for a Nepal external reporting API) are defined in Config Packs.
3. **Resolution Flow:** Config Engine pushes routing rule updates to the gateway.
4. **Hot Reload:** Routing tables update dynamically.
5. **Backward Compatibility:** Deprecated API versions are routed to legacy handlers or return graceful deprecation errors.
6. **Future Jurisdiction:** Handled by adding new routes to the config.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `RouteDefinition`: `{ route_id: String, path: String, target_service: String, auth_required: Boolean, rate_limit_policy: String }`
- **Dual-Calendar Fields:** N/A
- **Event Schema Changes:** N/A

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                |
| ----------------- | -------------------------------------------------------------------------- |
| Event Name        | `ApiQuotaExceededEvent`                                                    |
| Schema Version    | `v1.0.0`                                                                   |
| Trigger Condition | A tenant or user exceeds their allowed API quota for a specific endpoint.  |
| Payload           | `{ "tenant_id": "...", "endpoint": "...", "limit": 1000, "window": "1m" }` |
| Consumers         | Billing Module, Security Operations, Observability                         |
| Idempotency Key   | `hash(tenant_id + endpoint + window_start)`                                |
| Replay Behavior   | Ignored.                                                                   |
| Retention Policy  | 1 year.                                                                    |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `RegisterRouteCommand`                                               |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Route path valid, backend service exists, requester authorized       |
| Handler          | `RouteCommandHandler` in K-11 API Gateway                            |
| Success Event    | `RouteRegistered`                                                    |
| Failure Event    | `RouteRegistrationFailed`                                            |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `UpdateRateLimitCommand`                                             |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Tenant exists, rate limit values valid, requester authorized         |
| Handler          | `RateLimitCommandHandler` in K-11 API Gateway                        |
| Success Event    | `RateLimitUpdated`                                                   |
| Failure Event    | `RateLimitUpdateFailed`                                              |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `BlockTenantCommand`                                                 |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Tenant exists, reason provided, requester authorized                 |
| Handler          | `TenantCommandHandler` in K-11 API Gateway                           |
| Success Event    | `TenantBlocked`                                                      |
| Failure Event    | `TenantBlockFailed`                                                  |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection
- **Workflow Steps Exposed:** Inbound traffic monitoring.
- **Model Registry Usage:** `waf-anomaly-detector-v1`
- **Explainability Requirement:** AI flags anomalous traffic patterns (e.g., potential API abuse or slow-loris attacks) that bypass standard rate limits.
- **Human Override Path:** Operator can unblock an IP flagged by the AI.
- **Drift Monitoring:** False positive monitoring for blocked IPs.
- **Fallback Behavior:** Standard static rate-limiting rules.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                   |
| ------------------------- | ------------------------------------------------------------------ |
| Latency / Throughput      | P99 overhead < 2ms; 50,000 TPS                                     |
| Scalability               | Fully stateless edge nodes                                         |
| Availability              | 99.999% uptime                                                     |
| Consistency Model         | Eventual consistency for rate limit counters                       |
| Security                  | TLS termination; WAF integration; DDoS protection                  |
| Data Residency            | N/A (Transient processing only)                                    |
| Data Retention            | N/A                                                                |
| Auditability              | Admin changes to routes are logged                                 |
| Observability             | Metrics: `gateway.latency`, `gateway.4xx.rate`, `gateway.5xx.rate` |
| Extensibility             | Dynamic route registration                                         |
| Upgrade / Compatibility   | Multi-version API routing (e.g., /v1/, /v2/)                       |
| On-Prem Constraints       | Can run as a local edge proxy                                      |
| Ledger Integrity          | N/A                                                                |
| Dual-Calendar Correctness | N/A                                                                |

---

#### Section 10 — Acceptance Criteria

1. **Given** an unauthenticated request to a protected endpoint, **When** it hits the gateway, **Then** it is rejected with 401 Unauthorized in < 2ms without touching the domain module.
2. **Given** a domain module that just started up, **When** it registers its routes via the control plane, **Then** the gateway begins routing traffic to it within 1 second.
3. **Given** a tenant exceeding their 100 req/sec limit, **When** the 101st request arrives, **Then** it is rejected with 429 Too Many Requests, and an `ApiQuotaExceededEvent` is published.

---

#### Section 11 — Failure Modes & Resilience

- **Auth Service Outage:** Fails closed (rejects requests) to preserve security, unless specific cached tokens are valid.
- **Backend Service Down:** Returns 503 Service Unavailable or 504 Gateway Timeout gracefully. Circuit breaker opens after repeated failures.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                    |
| ------------------- | ----------------------------------------------------------------------------------- |
| Metrics             | `request.latency`, `request.count`, dimensions: `route`, `tenant_id`, `status_code` |
| Logs                | Access logs (stripped of PII/payloads)                                              |
| Traces              | Originating span starts here                                                        |
| Audit Events        | N/A                                                                                 |
| Regulatory Evidence | Access logs can prove API interactions.                                             |

---

#### Section 13 — Compliance & Regulatory Traceability

- System access controls [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** Control plane API for route registration.
- **Jurisdiction Plugin Extension Points:** Gateway config extensions.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                                     |
| --------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                  | Yes.                                                                                                |
| Can a new exchange be connected?                                      | N/A                                                                                                 |
| Can this run in an air-gapped deployment?                             | Yes.                                                                                                |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes. Token-based API authentication and on-chain webhook routing are supported via gateway plugins. |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes. Ultra-low-latency pass-through mode (≤2ms P99) supports real-time settlement API flows.        |

---

#### Section 16 — Threat Model

**Attack Vectors & Mitigations:**

1. **DDoS / Resource Exhaustion**
   - **Threat:** Attacker floods the gateway with requests to overwhelm backend services or exhaust resources.
   - **Mitigation:** Rate limiting per tenant/IP/API key; request throttling; connection limits; auto-scaling based on load; CDN/WAF integration; circuit breakers to protect backends; anomaly detection for traffic spikes.
   - **Residual Risk:** Sophisticated distributed attacks may temporarily degrade service.

2. **API Abuse / Credential Stuffing**
   - **Threat:** Attacker uses stolen API keys or brute-forces credentials to gain unauthorized access.
   - **Mitigation:** API key rotation; rate limiting on authentication endpoints; account lockout after failed attempts; API key scoping (limited permissions); revocation mechanisms; monitoring for unusual API usage patterns.
   - **Residual Risk:** Leaked API keys used before detection.

3. **Injection Attacks (SQL, NoSQL, Command)**
   - **Threat:** Attacker injects malicious payloads in API requests to exploit backend vulnerabilities.
   - **Mitigation:** Input validation and sanitization at gateway; request schema validation; parameterized queries in backends; WAF rules for common injection patterns; content-type enforcement; payload size limits.
   - **Residual Risk:** Zero-day injection techniques.

4. **Man-in-the-Middle (MITM)**
   - **Threat:** Attacker intercepts API traffic to steal credentials or sensitive data.
   - **Mitigation:** Enforce TLS 1.3+ for all connections; certificate pinning; HSTS headers; mTLS for service-to-service; no support for weak ciphers; regular certificate rotation.
   - **Residual Risk:** Compromised client devices with malware.

5. **API Enumeration / Information Disclosure**
   - **Threat:** Attacker probes API endpoints to discover vulnerabilities or sensitive information.
   - **Mitigation:** Generic error messages (no stack traces); rate limiting on 404/403 responses; endpoint obfuscation; no verbose error details in production; security headers (X-Content-Type-Options, X-Frame-Options).
   - **Residual Risk:** Determined attackers may map API surface over time.

6. **Unauthorized Access / Broken Authentication**
   - **Threat:** Attacker bypasses authentication to access protected resources.
   - **Mitigation:** Strict authentication enforcement on all routes; JWT validation with signature verification; token expiry enforcement; integration with K-01 IAM for centralized auth; no default/backdoor credentials.
   - **Residual Risk:** Implementation bugs in auth logic.

7. **Cross-Tenant Data Leakage**
   - **Threat:** Tenant A accesses Tenant B's data through API manipulation.
   - **Mitigation:** Tenant ID validation on every request; row-level tenant isolation enforced at gateway; request context includes verified tenant_id; no tenant ID in URL (use headers/tokens); comprehensive tenant isolation testing.
   - **Residual Risk:** Logic errors in tenant resolution.

8. **Request Smuggling / HTTP Desync**
   - **Threat:** Attacker exploits HTTP parsing differences to smuggle malicious requests.
   - **Mitigation:** Strict HTTP parsing; reject ambiguous requests; normalize headers; use HTTP/2 where possible; regular security updates; WAF rules for smuggling patterns.
   - **Residual Risk:** Novel smuggling techniques.

**Security Controls:**

- TLS 1.3+ enforcement with strong ciphers
- Rate limiting and throttling per tenant/IP/API key
- Input validation and schema enforcement
- Authentication via K-01 IAM with JWT validation
- Authorization checks on every request
- Tenant isolation validation
- WAF integration for common attack patterns
- Circuit breakers to protect backends
- Comprehensive access logging (sanitized)
- Security headers (HSTS, CSP, X-Frame-Options)
- Regular security scanning and penetration testing
- Auto-scaling and DDoS protection
- API versioning and deprecation management

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
