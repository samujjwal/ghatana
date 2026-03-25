# Java Heavy-Processing Boundary Definition

> **Status:** Active  
> **Version:** 1.0.0  
> **Backlog Item:** P0.4

## 1. Overview

The TutorPutor content system uses a **Hybrid Backend** architecture:

- **TypeScript / Fastify** (`tutorputor-platform`) — Public API, control plane, orchestration, state management, and user-facing endpoints.
- **Java 21 / ActiveJ** (`tutorputor-content-generation`, `content-studio-agents`) — Heavy AI/LLM processing, content generation, validation, and enhancement. Internal only, never a second public API.

Communication between these planes uses **gRPC** for synchronous processing and **BullMQ** (Redis) for job orchestration and fan-out.

## 2. Responsibility Boundary

### TypeScript Control Plane (Fastify)

| Responsibility         | Description                                                   |
| :--------------------- | :------------------------------------------------------------ |
| Public API             | All routes under `/api/v1/*`. The single public surface.      |
| Job orchestration      | Enqueue, monitor, retry, and report on BullMQ jobs.           |
| State persistence      | Prisma CRUD for experiences, claims, events, analytics.       |
| Auth & tenancy         | Authentication, authorization, tenant isolation.              |
| Progress reporting     | Expose job status, timeline events, and analytics to clients. |
| Correlation assignment | Generate `requestId` (UUID) and propagate through job data.   |

### Java Execution Plane (gRPC)

| Responsibility         | Description                                                                            |
| :--------------------- | :------------------------------------------------------------------------------------- |
| LLM interaction        | All prompting, completion parsing, and retry against AI models.                        |
| Content generation     | Claims, examples, simulations, animations, assessments.                                |
| Content validation     | Multi-dimension scoring (educational, experiential, safety, technical, accessibility). |
| Content enhancement    | Engagement, real-world connections, scaffolding improvements.                          |
| Content needs analysis | Gap detection and artifact requirement inference.                                      |
| Metrics emission       | Micrometer counters and timers for generation operations.                              |

### Invariants

- Java services are **internal execution components**, not a second public API.
- Fastify **owns** the request lifecycle — it enqueues, tracks, and reports.
- Java **owns** the AI execution — it prompts, parses, scores, and returns typed results.
- No LLM calls should originate from Fastify route handlers.

## 3. gRPC Contract

### 3.1 Proto Locations

There are currently **two proto definitions** that must converge:

| Location                                                                         | Package                                | RPCs                                                                     | Status                                   |
| :------------------------------------------------------------------------------- | :------------------------------------- | :----------------------------------------------------------------------- | :--------------------------------------- |
| `services/tutorputor-content-generation/src/main/proto/content_generation.proto` | `tutorputor.content_generation`        | 7 RPCs (includes `GenerateAnimation`, `HealthCheck`)                     | **Primary runtime contract**             |
| `libs/content-studio-agents/src/main/proto/content_generation.proto`             | `com.ghatana.tutorputor.contentstudio` | 6 RPCs (includes `EnhanceContent`, no `GenerateAnimation`/`HealthCheck`) | **Library contract with implementation** |

**Convergence requirement:** These two protos MUST be unified under a single canonical location in `contracts/proto/` with a single package name. The unified contract must include all 8 unique RPCs.

### 3.2 Canonical RPC Catalog

| RPC                   | Request                                        | Response                                | Java Handler                                         | TS Processor                    | Purpose                                   |
| :-------------------- | :--------------------------------------------- | :-------------------------------------- | :--------------------------------------------------- | :------------------------------ | :---------------------------------------- |
| `GenerateClaims`      | topic, grade_level, domain, max_claims         | claims[], validation, metadata          | `ContentGenerationServiceImpl.generateClaims()`      | `ClaimGenerationProcessor`      | Generate Bloom's-aligned learning claims  |
| `AnalyzeContentNeeds` | claims, target_grade_level, learning_objective | gaps[], suggestions[], complexity_score | `ContentGenerationServiceImpl.analyzeContentNeeds()` | —                               | Detect content gaps and artifact needs    |
| `GenerateExamples`    | claim_ref, claim_text, types[], count          | examples[]                              | `ContentGenerationServiceImpl.generateExamples()`    | `ExampleGenerationProcessor`    | Generate worked examples for claims       |
| `GenerateSimulation`  | claim_ref, interaction_type, complexity        | manifest, metadata                      | `ContentGenerationServiceImpl.generateSimulation()`  | `SimulationGenerationProcessor` | Generate interactive simulation manifests |
| `GenerateAnimation`   | claim_ref, animation_type, duration_seconds    | animation, metadata                     | — (proto only)                                       | `AnimationGenerationProcessor`  | Generate animation keyframes              |
| `ValidateContent`     | experience_id, content, config                 | status, scores, issues                  | `ContentGenerationServiceImpl.validateContent()`     | `ContentValidationProcessor`    | Multi-pillar content validation           |
| `EnhanceContent`      | experience_id, enhancement_types               | enhancements[], confidence              | `ContentGenerationServiceImpl.enhanceContent()`      | —                               | Improve engagement and pedagogy           |
| `HealthCheck`         | empty                                          | status, timestamp                       | —                                                    | —                               | Service liveness check                    |

### 3.3 Common Message Types

All requests carry:

```protobuf
message RequestContext {
  string request_id = 1;   // Correlation ID (UUID)
  string tenant_id = 2;    // Tenant isolation
  google.protobuf.Timestamp timestamp = 3;
  map<string, string> metadata = 4;  // Extensible context
}
```

All responses carry:

```protobuf
message GenerationMetadata {
  string model_name = 1;
  int32 tokens_used = 2;
  int64 generation_time_ms = 3;
  double temperature = 4;
  string prompt_hash = 5;
  string timestamp = 6;
}
```

## 4. Job Families

### 4.1 BullMQ Queue: `content-generation`

All content generation jobs flow through a single BullMQ queue with typed job names.

| Job Name              | Job Data                                                                 | Triggers Follow-up                                   | Fan-out                      |
| :-------------------- | :----------------------------------------------------------------------- | :--------------------------------------------------- | :--------------------------- |
| `generate-claims`     | experienceId, tenantId, topic, domain, gradeLevel, maxClaims             | `generate-examples`, `generate-simulation` per claim | 1 → N (per claim with needs) |
| `generate-examples`   | experienceId, tenantId, claimRef, claimText, gradeLevel, types, count    | —                                                    | Terminal                     |
| `generate-simulation` | experienceId, tenantId, claimRef, claimText, interactionType, complexity | —                                                    | Terminal                     |
| `generate-animation`  | experienceId, tenantId, claimRef, animationType, durationSeconds         | —                                                    | Terminal                     |
| `validate-content`    | experienceId, tenantId, title, description, claimTexts, domain           | —                                                    | Terminal                     |

### 4.2 Execution Model

```
Fastify Route Handler
  └─ Enqueue BullMQ job (generate-claims)
       └─ ContentWorkerService dispatches to ClaimGenerationProcessor
            └─ gRPC call → Java ContentGenerationService.GenerateClaims()
                 └─ LLM prompt → parse → return typed claims
            └─ Persist claims to Prisma
            └─ Fan-out: enqueue generate-examples + generate-simulation per claim
                 └─ Each processor → gRPC call → Java → persist result
```

### 4.3 What Stays in TypeScript vs Java

| Processing Step           | Owner                                     | Rationale                                      |
| :------------------------ | :---------------------------------------- | :--------------------------------------------- |
| Job enqueue/dequeue       | **TypeScript** (BullMQ)                   | Orchestration, retry policy, DLQ management    |
| Job dispatch & routing    | **TypeScript** (ContentWorkerService)     | Switch on job name, route to processor         |
| gRPC request assembly     | **TypeScript** (Processors)               | Map job data to proto request                  |
| LLM prompt construction   | **Java**                                  | Domain expertise, prompt engineering           |
| LLM call execution        | **Java** (LLMGateway)                     | Blocking I/O, wrapped in `Promise.ofBlocking`  |
| Response parsing          | **Java**                                  | JSON→proto, validation of LLM output           |
| Result persistence        | **TypeScript** (Processors)               | Prisma writes, maintain Fastify as state owner |
| Follow-up job fan-out     | **TypeScript** (ClaimGenerationProcessor) | Orchestration logic stays in control plane     |
| Metrics emission          | **Java** (MeterRegistry)                  | Generation-specific counters and timers        |
| Progress/status reporting | **TypeScript** (Fastify routes)           | Public API responses, event timeline           |

## 5. Retry, Idempotency, and Failure Handling

### 5.1 BullMQ Retry Policy

| Setting            | Value           | Description                |
| :----------------- | :-------------- | :------------------------- |
| `attempts`         | 3               | Maximum job attempts       |
| `backoff.type`     | `exponential`   | Backoff strategy           |
| `backoff.delay`    | 5000ms          | Base delay between retries |
| `removeOnComplete` | 100 (keep last) | Completed job retention    |
| `removeOnFail`     | 500 (keep last) | Failed job retention       |

### 5.2 gRPC Retry Policy

| Setting      | Value            | Description                                  |
| :----------- | :--------------- | :------------------------------------------- |
| `timeout`    | 5000ms           | Per-request deadline                         |
| `maxRetries` | 3                | Retry attempts within a single job execution |
| `backoff`    | None (immediate) | No backoff between gRPC retries              |

### 5.3 Dead Letter Queue

- Queue: `content-generation-dlq`
- Trigger: Job fails after 3 BullMQ attempts
- DLQ manager moves failed jobs with error message
- DLQ entries are retained for operator inspection

### 5.4 Idempotency

| Layer             | Mechanism                                                                                                                            |
| :---------------- | :----------------------------------------------------------------------------------------------------------------------------------- |
| BullMQ            | `JobDeduplicator` prevents duplicate job enqueue via Prisma lookup                                                                   |
| Claim persistence | `upsert` on `(experienceId, claimRef)` composite key                                                                                 |
| gRPC requests     | Each request carries a unique `requestId` (UUID); Java logs it but does not deduplicate (LLM calls are inherently non-deterministic) |

### 5.5 Failure Payloads

gRPC errors use standard `grpc.Status` codes:

| Status             | When                               | Action                                             |
| :----------------- | :--------------------------------- | :------------------------------------------------- |
| `INTERNAL`         | LLM returns empty/invalid response | Job fails, BullMQ retries                          |
| `INTERNAL`         | Parse error on LLM output          | Job fails, BullMQ retries                          |
| `INVALID_ARGUMENT` | Missing required fields            | Job fails immediately (no retry value)             |
| `UNAVAILABLE`      | Java service unreachable           | gRPC client retries (up to 3), then BullMQ retries |

## 6. Observability and Correlation-ID Propagation

### 6.1 Correlation Chain

```
Fastify Request (requestId from auth/session)
  └─ BullMQ Job (job.id = deterministic hash, job.data contains experienceId + tenantId)
       └─ Processor generates requestId = crypto.randomUUID()
            └─ gRPC Request (request_id field in proto)
                 └─ Java logs requestId, passes to LLM prompt context
                      └─ Java metrics tagged with requestId
```

### 6.2 Current Gaps

1. **No end-to-end correlation:** The Fastify request ID is not propagated into the BullMQ job data. The processor generates a new UUID, breaking the trace chain.
2. **No cross-boundary propagation:** BullMQ `job.id` and gRPC `request_id` are independent identifiers with no linking metadata.
3. **Recommendation:** Add a `correlationId` field to all job data interfaces, set it at enqueue time from the Fastify request context, and pass it through to gRPC `RequestContext.metadata`.

### 6.3 Java Metrics

| Metric                                      | Type    | Tags | Description                 |
| :------------------------------------------ | :------ | :--- | :-------------------------- |
| `tutorputor.content.claims.generated`       | Counter | —    | Total claims generated      |
| `tutorputor.content.examples.generated`     | Counter | —    | Total examples generated    |
| `tutorputor.content.simulations.generated`  | Counter | —    | Total simulations generated |
| `tutorputor.content.validations.performed`  | Counter | —    | Total validations run       |
| `tutorputor.content.enhancements.performed` | Counter | —    | Total enhancements run      |
| `tutorputor.content.generation.duration`    | Timer   | —    | Generation latency          |
| `tutorputor.content.llm.errors`             | Counter | —    | LLM failures                |

### 6.4 TypeScript Logging

Each processor logs structured context:

```typescript
{ jobId: job.id, experienceId, topic }     // on start
{ jobId: job.id, claimsCount: N }          // on success
{ jobId: job.id, err: error }              // on failure
```

Worker-level events:

```typescript
worker.on("completed", (job) => {
  (jobId, name);
});
worker.on("failed", (job, err) => {
  (jobId, name, err);
});
```

## 7. Configuration

### 7.1 ContentWorkerConfig

```typescript
interface ContentWorkerConfig {
  redis: { host: string; port: number; password?: string; db?: number };
  grpc: { serverAddress: string; useTls: boolean };
  concurrency?: number; // default: 5
  logger: Logger;
  prisma?: PrismaClient;
}
```

### 7.2 gRPC Client Config

```typescript
interface GrpcClientConfig {
  serverAddress: string; // e.g., "localhost:50051"
  useTls: boolean;
  timeout: number; // 5000ms
  maxRetries: number; // 3
  logger: Logger;
}
```

### 7.3 Test Environment

- `CONTENT_QUEUE_DISABLED=true` disables BullMQ in test
- Java async tests MUST extend `EventloopTestBase`
- Blocking calls wrapped with `Promise.ofBlocking(executor, ...)`

## 8. Proto Convergence Plan

The two divergent proto files must converge:

### Current Divergences

| Aspect              | Service Proto                   | Library Proto                                  |
| :------------------ | :------------------------------ | :--------------------------------------------- |
| Package             | `tutorputor.content_generation` | `com.ghatana.tutorputor.contentstudio`         |
| `GenerateAnimation` | ✅ Present                      | ❌ Missing                                     |
| `EnhanceContent`    | ❌ Missing                      | ✅ Present                                     |
| `HealthCheck`       | ✅ Present                      | ❌ Missing                                     |
| `RequestContext`    | Structured message              | Flat `tenant_id` field                         |
| Claim model         | `ContentClaim` (7 fields)       | `LearningClaim` (5 fields + `ContentNeeds`)    |
| Simulation model    | `SimulationContent` (8 fields)  | `SimulationManifest` (9 fields + nested types) |
| Validation model    | `ValidationResult` (5 fields)   | `ValidateContentResponse` (8 fields, richer)   |

### Target State

1. Single canonical proto at `contracts/proto/content_generation.proto`.
2. Package: `tutorputor.content_generation` (aligns with service runtime).
3. All 8 RPCs present: GenerateClaims, AnalyzeContentNeeds, GenerateExamples, GenerateSimulation, GenerateAnimation, ValidateContent, EnhanceContent, HealthCheck.
4. `RequestContext` as a structured message on all requests.
5. Richer model from library proto (LearningClaim with ContentNeeds, SimulationManifest with nested entities) adopted as canonical.
6. Both Java packages generated from the single source.

## 9. Versioning

- Proto files follow **semantic versioning** via the `java_package` option suffix (e.g., `contracts.v1`).
- Breaking changes require a new version suffix and migration period.
- The TS gRPC client loads proto files dynamically via `@grpc/proto-loader` — proto path changes require config update only.
