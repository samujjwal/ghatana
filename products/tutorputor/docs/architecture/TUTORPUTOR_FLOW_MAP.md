# TUTORPUTOR_FLOW_MAP

Last audit update: 2026-03-08

## 1) UI -> API -> Service -> DB
```mermaid
flowchart LR
  W["tutorputor-web"] --> G["api-gateway createServer()"]
  A["tutorputor-admin"] --> G
  M["tutorputor-mobile"] --> G
  G --> P["tutorputor-platform setupPlatform()"]
  P --> MOD["Platform modules (content/learning/user/ai/integration/etc)"]
  MOD --> DB["Prisma (@ghatana/tutorputor-db)"]
```

Validation evidence:
- `apps/api-gateway` targeted `build` + `test` PASS.
- Platform module-level tests partially pass (targeted worker/studio tests), but full platform gate is FAIL.

## 2) API -> Queue -> Worker -> DB / gRPC agents
```mermaid
flowchart LR
  CS["Content Studio routes/service"] --> Q["BullMQ queue: content-generation"]
  Q --> CW["ContentWorkerService"]
  CW --> CP["ClaimGenerationProcessor"]
  CP --> GRPC["RealContentGenerationClient"]
  GRPC --> AG["AI/Content gRPC agents"]
  CP --> Q2["follow-up jobs: examples/simulation/animation"]
  Q2 --> EP["ExampleGenerationProcessor"]
  Q2 --> SP["SimulationGenerationProcessor"]
  Q2 --> AP["AnimationGenerationProcessor"]
  EP --> DB["Prisma DB"]
  SP --> DB
  AP --> DB
```

Validation evidence:
- New automation PASS: `platform-content-worker-tests.log`.
- New automation PASS: `platform-content-studio-service-tests.log`.
- Open integration risk: content generation proto/service drift for `GenerateAnimation` (contract exists in one proto, backend implementation coverage incomplete).

## 3) Contract -> Implementation -> Consumer
```mermaid
flowchart LR
  C["contracts (TS + proto)"] --> IMPL1["platform services/processors"]
  C --> IMPL2["web/admin/mobile clients"]
  C --> IMPL3["ai/content Java services"]
```

Validation evidence:
- `contracts` build/test PASS.
- Multiple downstream type/build failures indicate unresolved contract drift in consumers.

## 4) Observability / Health / Ops
```mermaid
flowchart LR
  P["tutorputor-platform"] --> MET["metrics + health endpoints"]
  P --> LOG["structured logging"]
  P --> ERR["error tracking"]
```

Validation evidence:
- Health/metrics routes present.
- Full reliability verification is FAIL due broad gate failures and incomplete integration execution (e2e, migration, security audit).

## Flow-level gaps requiring closure
1. `GenerateAnimation` end-to-end contract alignment across gRPC server/client implementations is incomplete.
2. Queue-backed flows are now fail-fast on enqueue errors (good), but full distributed integration test coverage is still not complete.
3. Several consumer modules fail type/build/test, so contract-to-consumer flow is not release-safe.
