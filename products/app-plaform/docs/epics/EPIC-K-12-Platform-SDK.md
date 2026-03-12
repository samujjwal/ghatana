EPIC-ID: EPIC-K-12
EPIC NAME: Platform SDK
LAYER: KERNEL
MODULE: K-12 Platform SDK
VERSION: 1.0.1

---

#### Section 1 — Objective

Deliver the K-12 Platform SDK, a unified, independently versioned artifact that provides all clients (AuthZ, Config, Event Bus, Audit, AI Governance, Observability, Calendar, Ledger) for domain modules and plugin authors. This epic enforces Principle 11 (No Kernel Duplication) and Principle 12 (Single Pane of Glass) by strictly prohibiting domain modules from directly accessing kernel internals.

---

#### Section 2 — Scope

- **In-Scope:**
  1. SDK packaging and versioning.
  2. Clients for all Kernel services (K-01 to K-16).
  3. Consistent error handling and generic error code translation.
  4. Integration with standard build pipelines for distribution.
- **Out-of-Scope:**
  1. The internal logic of the kernel services the SDK connects to.
- **Dependencies:** EPIC-K-01 to EPIC-K-16
- **Kernel Readiness Gates:** N/A
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Unified Client Interface:** The SDK must provide a consistent, strongly-typed interface across all supported languages (Go, Java, Python, TypeScript/JavaScript, C#).
2. **FR2 Internal Protocol Abstraction:** The SDK must hide the underlying transport protocols (gRPC, REST, Kafka) used to communicate with Kernel services.
3. **FR3 Error Translation:** The SDK must translate underlying infrastructure errors (e.g., network timeouts) into standard, handleable platform error codes.
4. **FR4 Version Management:** The SDK must be versioned independently using semantic versioning (MAJOR.MINOR.PATCH), with a clear changelog and migration guide for every release.
5. **FR5 Backward Compatibility Guarantee:** Deprecated methods must be supported for at least 2 major versions with compilation warnings.
6. **FR6 Multi-Language Support:** The SDK must provide idiomatic implementations for each supported language (Go modules, Java/Maven, Python/pip, npm, NuGet) with language-specific conventions.
7. **FR7 Distribution Management:** The SDK must be published to standard artifact repositories (Maven Central, PyPI, npm registry, NuGet Gallery, Go proxy) with automated release pipelines.
8. **FR8 Breaking Change Management:** Major version increments must include automated migration tooling, deprecation notices, and comprehensive upgrade guides.
9. **FR9 Client Library Generation:** The SDK must use code generation from shared interface definitions (gRPC proto, OpenAPI specs) to ensure consistency across languages.
10. **FR10 Offline Support:** The SDK must support offline/air-gapped deployments with local artifact repository mirroring.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The SDK is pure Generic Core.
2. **Jurisdiction Plugin:** Plugins use this SDK to interact with the platform.
3. **Resolution Flow:** N/A
4. **Hot Reload:** N/A (compile-time dependency).
5. **Backward Compatibility:** Strict adherence to semantic versioning.
6. **Future Jurisdiction:** N/A

---

#### Section 5 — Data Model Impact

- **New Entities:** N/A
- **Dual-Calendar Fields:** The SDK provides the `DualDate` struct to all domain modules.
- **Event Schema Changes:** N/A

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                            |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `SDKVersionDeprecated`                                                                                                 |
| Schema Version    | `v1.0.0`                                                                                                               |
| Trigger Condition | An SDK version reaches end-of-life or is marked for deprecation.                                                       |
| Payload           | `{ "sdk_version": "2.5.0", "deprecation_date": "2026-06-01", "eol_date": "2026-12-01", "migration_guide_url": "..." }` |
| Consumers         | CI/CD pipelines, Developer notifications, Admin Portal                                                                 |
| Idempotency Key   | `hash(sdk_version + deprecation_date)`                                                                                 |
| Replay Behavior   | Updates deprecation tracking dashboards.                                                                               |
| Retention Policy  | Permanent.                                                                                                             |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                                           |
| ---------------- | ------------------------------------------------------------------------------------- |
| Command Name     | `PublishSDKVersionCommand`                                                            |
| Schema Version   | `v1.0.0`                                                                              |
| Validation Rules | Version follows semver, changelog provided, breaking changes documented, tests passed |
| Handler          | `SDKReleaseHandler` in K-12 Platform SDK                                              |
| Success Event    | `SDKVersionPublished`                                                                 |
| Failure Event    | `SDKPublishFailed`                                                                    |
| Idempotency      | Version must be unique; duplicate versions rejected                                   |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Code Generation / Migration Assistance
- **Workflow Steps Exposed:** SDK upgrade migration, breaking change detection.
- **Model Registry Usage:** `sdk-migration-assistant-v1`
- **Explainability Requirement:** AI analyzes code using deprecated SDK methods and suggests migration paths with code examples. AI detects breaking changes between SDK versions and generates upgrade scripts.
- **Human Override Path:** Developer reviews and approves AI-generated migration code.
- **Drift Monitoring:** Migration success rate tracked against manual migrations.
- **Fallback Behavior:** Manual migration using documentation and migration guides.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                        |
| ------------------------- | ----------------------------------------------------------------------- |
| Latency / Throughput      | SDK overhead < 1ms per call; minimal memory footprint                   |
| Scalability               | Support 10,000+ concurrent SDK clients per service                      |
| Availability              | SDK must handle transient service failures gracefully                   |
| Consistency Model         | Strong consistency for version resolution                               |
| Security                  | SDK validates server certificates; supports mTLS; no credential logging |
| Data Residency            | SDK respects jurisdiction-specific endpoints                            |
| Data Retention            | SDK logs retained per platform policy                                   |
| Auditability              | SDK version and calls logged for compliance                             |
| Observability             | SDK auto-injects telemetry context (trace_id, span_id, tenant_id)       |
| Extensibility             | Plugin-based architecture for custom clients                            |
| Upgrade / Compatibility   | Semantic versioning; 2 major version backward compatibility             |
| On-Prem Constraints       | Published to local artifact registries; offline installation support    |
| Ledger Integrity          | N/A                                                                     |
| Dual-Calendar Correctness | SDK provides DualDate utilities                                         |

---

#### Section 10 — Acceptance Criteria

1. **Given** a domain module developer, **When** they need to publish an event, **Then** they use `PlatformSDK.EventClient.publish()` without needing to know if the backend is Kafka or Pulsar.
2. **Given** an outdated SDK method call, **When** compiled, **Then** the compiler warns of deprecation and indicates the required removal version.
3. **Given** a Go developer, **When** they import the SDK via `go get github.com/siddhanta/platform-sdk-go@v3.2.1`, **Then** the SDK is downloaded from the Go proxy and all kernel clients are available.
4. **Given** a Java developer, **When** they add the SDK dependency to Maven (`com.siddhanta:platform-sdk:3.2.1`), **Then** the SDK is resolved from Maven Central with all transitive dependencies.
5. **Given** a Python developer, **When** they install via `pip install siddhanta-platform-sdk==3.2.1`, **Then** the SDK is installed from PyPI with type hints and autocomplete support.
6. **Given** an SDK major version upgrade (v2 → v3), **When** a developer runs the migration tool, **Then** it identifies deprecated method calls, suggests replacements, and optionally applies automated fixes.
7. **Given** an air-gapped deployment, **When** the SDK is installed from a local artifact mirror, **Then** all dependencies are resolved locally without external network access.
8. **Given** a breaking change in SDK v4.0.0, **When** released, **Then** the changelog includes migration guide, deprecated methods list, and code examples for all breaking changes.
9. **Given** a service using SDK v3.5.0, **When** it calls a kernel service, **Then** the SDK auto-injects trace context, tenant_id, and correlation_id into the request headers.
10. **Given** a transient network error, **When** the SDK calls a kernel service, **Then** it retries with exponential backoff (max 3 retries) and returns a clear error if all retries fail.

---

#### Section 11 — Failure Modes & Resilience

- **Transient Network Errors:** SDK handles automatic retries with exponential backoff (100ms, 200ms, 400ms) for idempotent operations; non-idempotent operations fail fast.
- **Service Unavailable:** SDK returns clear error with service name and retry-after hint; circuit breaker opens after 5 consecutive failures.
- **Version Mismatch:** SDK validates minimum platform version compatibility; fails fast with upgrade instructions if incompatible.
- **Certificate Validation Failure:** SDK rejects connections with invalid certificates; no insecure fallback allowed.
- **Timeout:** SDK enforces configurable timeouts (default 30s); returns timeout error with partial results if available.
- **Serialization Errors:** SDK validates payloads against schemas before sending; returns validation errors with field-level details.
- **Backward Compatibility Break:** SDK maintains shim layers for deprecated APIs; logs warnings but continues to function.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                                        |
| ------------------- | ------------------------------------------------------------------------------------------------------- |
| Metrics             | `sdk.call.latency`, `sdk.call.count`, `sdk.error.count`, dimensions: `service`, `method`, `sdk_version` |
| Logs                | Structured: `trace_id`, `sdk_version`, `service`, `method`, `latency_ms`, `status`                      |
| Traces              | SDK creates child spans for all kernel service calls; propagates trace context                          |
| Audit Events        | SDK version usage tracked for compliance; deprecated method usage logged                                |
| Regulatory Evidence | SDK version audit trail for reproducibility                                                             |

---

#### Section 13 — Compliance & Regulatory Traceability

- Standardizes access to regulated core functions [LCA-AUDIT-001]
- SDK version tracking for audit reproducibility [ASR-TECH-001]
- Enforces secure communication (mTLS) for compliance [ASR-SEC-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract (Go):** `sdk.NewClient(config)` returns `*PlatformClient` with methods: `EventClient()`, `ConfigClient()`, `AuthZClient()`, `LedgerClient()`, etc.
- **SDK Contract (Java):** `PlatformSDK.builder().config(config).build()` returns `PlatformSDK` with getters for all kernel clients.
- **SDK Contract (Python):** `PlatformSDK(config)` returns SDK instance with properties for all kernel clients.
- **SDK Contract (TypeScript):** `new PlatformSDK(config)` returns SDK instance with typed client accessors.
- **Plugin Interface:** `ClientPlugin` interface allows custom kernel client implementations.
- **Middleware Interface:** `RequestMiddleware` and `ResponseMiddleware` for custom request/response processing.
- **Language-Specific Idioms:** Go uses contexts; Java uses CompletableFuture; Python uses async/await; TypeScript uses Promises.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                                                |
| --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                  | Yes, SDK is jurisdiction-agnostic.                                                                             |
| Can new kernel services be added without SDK major version bump?      | Yes, via minor version increments with new client modules.                                                     |
| Can this run in an air-gapped deployment?                             | Yes, with local artifact repository mirrors.                                                                   |
| Can multiple SDK versions coexist in the same application?            | No, single SDK version per application to avoid conflicts.                                                     |
| Can SDK be extended with custom clients?                              | Yes, via ClientPlugin interface.                                                                               |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes. SDK includes optional `digital-assets` module with wallet, token, and smart-contract client abstractions. |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes. Real-time event streaming and atomic transaction helpers in the SDK support T+0 settlement flows.         |

---

#### Section 16 — SDK Versioning & Distribution Strategy

**Semantic Versioning:**

- **MAJOR (X.0.0):** Breaking changes (API removals, signature changes, behavior changes)
- **MINOR (x.Y.0):** New features, new kernel clients, backward-compatible additions
- **PATCH (x.y.Z):** Bug fixes, performance improvements, security patches

**Release Cadence:**

- **Major:** Annually (coordinated with platform major releases)
- **Minor:** Quarterly (new features, new kernel services)
- **Patch:** As needed (bug fixes, security patches)

**Deprecation Policy:**

- Deprecated methods marked with `@Deprecated` (Java), `// Deprecated:` (Go), `@deprecated` (Python/TS)
- Deprecation warnings in compilation/runtime logs
- Deprecated methods supported for 2 major versions (e.g., deprecated in v3.0, removed in v5.0)
- Migration guide published with each deprecation

**Breaking Change Management:**

- Breaking changes only in major versions
- 6-month advance notice for breaking changes
- Automated migration tooling provided
- Parallel SDK versions supported during transition (v2 and v3 simultaneously)

**Distribution Channels:**

1. **Go:** `github.com/siddhanta/platform-sdk-go` via Go proxy
2. **Java:** `com.siddhanta:platform-sdk` on Maven Central
3. **Python:** `siddhanta-platform-sdk` on PyPI
4. **TypeScript/JavaScript:** `@siddhanta/platform-sdk` on npm registry
5. **C#:** `Siddhanta.PlatformSDK` on NuGet Gallery

**Artifact Repository Structure:**

```
Maven Central:
  com.siddhanta:platform-sdk:3.2.1
  com.siddhanta:platform-sdk-core:3.2.1
  com.siddhanta:platform-sdk-events:3.2.1
  com.siddhanta:platform-sdk-auth:3.2.1

PyPI:
  siddhanta-platform-sdk==3.2.1

npm:
  @siddhanta/platform-sdk@3.2.1
  @siddhanta/platform-sdk-types@3.2.1

Go:
  github.com/siddhanta/platform-sdk-go/v3@v3.2.1
```

**Local/Air-Gapped Distribution:**

- Nexus/Artifactory mirror for Maven/npm/PyPI
- Athens proxy for Go modules
- Offline installation bundles (ZIP with all dependencies)
- Checksum verification for all artifacts

**CI/CD Integration:**

- Automated SDK publishing on Git tag (e.g., `v3.2.1`)
- Multi-language build pipeline (Go, Java, Python, TypeScript, C#)
- Automated testing across all supported languages
- Compatibility testing against platform versions
- Security scanning (Snyk, Dependabot)
- License compliance checking

**Documentation:**

- API reference (auto-generated from code)
- Getting started guides per language
- Migration guides for major versions
- Code examples and recipes
- Changelog with breaking changes highlighted
- Compatibility matrix (SDK version ↔ Platform version)

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Preserved the SDK-specific distribution strategy as Section 16.
