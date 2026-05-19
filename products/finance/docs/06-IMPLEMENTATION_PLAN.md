# Finance — Implementation Plan

**Status:** Alpha / Backend-First  
**Stack:** Java 21 + ActiveJ · Gradle monorepo  
**Scope:** Order management, risk, compliance, client onboarding, regulatory reporting

---

## Product Shape

Finance is currently a backend-only product surface. There is no Finance UI/client package registered in the workspace until a shell-aligned product surface is introduced under the Kernel route and design-system contracts.

| Surface       | Kind                                       | Status |
| ------------- | ------------------------------------------ | ------ |
| `backend-api` | Backend Java runtime and HTTP/BFF boundary | Alpha  |
| `portal`      | Backend-only regulator data/query module   | Alpha  |
| `operator`    | Backend-only operator workflow module      | Alpha  |
| `sdk`         | Java platform SDK module                   | Alpha  |

---

## Module Status

| Module                | Gradle Path                             | Status | Notes                                    |
| --------------------- | --------------------------------------- | ------ | ---------------------------------------- |
| `launcher`            | `:products:finance:launcher`            | Alpha  | ServiceLauncher entry point              |
| `domains/`            | `:products:finance:domains:*`           | Alpha  | Finance and surveillance domain services |
| `calendar-service`    | `:products:finance:calendar-service`    | Alpha  | Calendar and scheduling                  |
| `client-onboarding`   | `:products:finance:client-onboarding`   | Alpha  | KYC and onboarding flows                 |
| `rules-engine`        | `:products:finance:rules-engine`        | Alpha  | Risk and compliance rule evaluation      |
| `ledger-framework`    | `:products:finance:ledger-framework`    | Alpha  | Order and ledger lifecycle               |
| `operator-workflows`  | `:products:finance:operator-workflows`  | Alpha  | Operator-facing workflow automation      |
| `platform-sdk`        | `:products:finance:platform-sdk`        | Alpha  | Finance-specific SDK                     |
| `integration-testing` | `:products:finance:integration-testing` | Alpha  | Integration test suites                  |

---

## Implemented Capabilities

| Capability                                        | Status |
| ------------------------------------------------- | ------ |
| Order management and lifecycle workflows          | Alpha  |
| Risk management and exposure tracking             | Alpha  |
| Client onboarding and KYC flows                   | Alpha  |
| Regulatory reporting and integrations             | Alpha  |
| Finance ledger and settlement workflows           | Alpha  |
| Surveillance domain                               | Alpha  |
| Observability (Micrometer, audit trail)           | Alpha  |
| Security (JWT, RBAC via `platform:java:security`) | Alpha  |
| Regulatory calendar integration                   | Alpha  |

---

## Planned / Pending

| Capability                         | Status  | Notes                                                                                          |
| ---------------------------------- | ------- | ---------------------------------------------------------------------------------------------- |
| Finance web portal UI              | Planned | Shell-aligned product surface design pending                                                   |
| EDI clearinghouse integration      | Planned | Not in scope for v1                                                                            |
| Real-time settlement connectivity  | Planned | Interface design pending                                                                       |
| Advanced regulatory risk scenarios | Alpha   | Existing but partial. Core coverage complete; edge cases pending. Evidence: `products/finance` |

---

## Build Commands

```bash
# Build all Finance modules
./gradlew :products:finance:launcher:build

# Run tests
./gradlew :products:finance:check

# Run integration tests
./gradlew :products:finance:integration-testing:test
```

---

## Observability and Regulatory Compliance

- **Tracing:** OpenTelemetry configured in `platform:java:observability`
- **Metrics:** Micrometer with Prometheus format at `/metrics`
- **Audit:** Immutable audit trail for all regulatory-relevant operations
- **Correlation IDs:** `X-Correlation-ID` header propagated through all Finance boundaries
- **HIPAA compatibility:** Finance data classified and separated from health records
- **KYC audit trail:** Client onboarding decisions recorded with immutable evidence

---

## Rollout Criteria

Finance is rollout-ready for local environment when:

1. All Gradle modules compile and tests pass (`./gradlew :products:finance:check`)
2. Launcher starts and responds to health check
3. RBAC enforcement validated for all API surfaces
4. Audit trail emits for all sensitive financial operations
5. Integration tests pass for order lifecycle and ledger workflows

---

## Promotion Criteria (Beyond Local)

- Finance web portal UI designed and implemented
- Regulatory reporting validated against real regulatory authority schema with integration evidence
- EDI clearinghouse adapter implemented and tested
- Compliance team sign-off on risk classification boundaries
- PCI-DSS scoping assessment completed
