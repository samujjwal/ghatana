# Agent System Modernization — Implementation Plan

**Blueprint Reference:** `AGENT_SYSTEM_MODERNIZATION_BLUEPRINT_2026-04-06.md`  
**Date:** 2026-04-06  
**Status:** Revised for trust, capability, and runtime-alignment work  
**Author:** Platform Architecture  
**Covers:** `platform/java/agent-core`, `platform/java/tool-runtime`, `platform/java/workflow`, `platform/java/observability`, `platform/java/policy-as-code`, `platform-kernel`, `products/aep`, `products/data-cloud`, `products/audio-video`  
**Blueprint sections covered:** §15 (Concrete Work Plan), §19 (Agent Pluggability), §20 (Inter-Agent Protocol)

---

## Quick Navigation

| Phase                                                           | Title                                       | Priority   | Estimated Effort |
| --------------------------------------------------------------- | ------------------------------------------- | ---------- | ---------------- |
| [Phase 0](#phase-0-normalize-truth-sources)                     | Normalize Truth Sources                     | 🔴 Blocker | S (1–2 days)     |
| [Phase 1](#phase-1-canonicalize-enums-and-spec-storage)         | Canonicalize Enums and Spec Storage         | 🔴 High    | M (3–5 days)     |
| [Phase 2](#phase-2-introduce-release-artifacts)                 | Introduce Release Artifacts                 | 🔴 High    | L (1–2 weeks)    |
| [Phase 3](#phase-3-unify-tool-contracts)                        | Unify Tool Contracts                        | 🟠 High    | L (1–2 weeks)    |
| [Phase 4](#phase-4-planning-as-durable-workflow-compilation)    | Planning as Durable Workflow Compilation    | 🟠 Medium  | L (1–2 weeks)    |
| [Phase 5](#phase-5-data-cloud-as-the-evidence-plane)            | Data Cloud as the Evidence Plane            | 🟠 Medium  | XL (2–3 weeks)   |
| [Phase 6](#phase-6-agent-observability-as-first-class-concern)  | Agent Observability First-Class             | 🟡 Medium  | L (1–2 weeks)    |
| [Phase 7](#phase-7-audio-video-as-domain-capability-provider)   | Audio-Video as Domain Capability Provider   | 🟡 Medium  | XL (2–3 weeks)   |
| [Phase 8](#phase-8-agent-pluggability-and-inter-agent-protocol) | Agent Pluggability and Inter-Agent Protocol | 🔴 High    | XL (2–3 weeks)   |
| [Track X](#track-x-trust-privacy-security-explainability-and-capability-gates) | Trust, Privacy, Security, Explainability, Capability Gates | 🔴 Blocker | Continuous       |

---

## Conventions and Ground Rules

Before picking up any task, read and internalize the following:

1. **Java 21** across all modules unless a module documents otherwise.
2. **ActiveJ `Promise<T>`** for all async flows. Never block the event loop. Use `Promise.ofBlocking(...)` only for bridged blocking I/O. Extend `EventloopTestBase` for async tests.
3. **TypedAgent contract** (`platform/java/agent-core`) is the single agent interface. All new agents extend `AbstractTypedAgent<I,O>`. No parallel interface hierarchies.
4. **Constructor injection** for all services. No `@Autowired` field injection.
5. **Immutable records** wherever applicable (Java 21 records or Lombok `@Value`/`@Builder`).
6. **JavaDoc** on every new public class with all four required tags: `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern`.
7. **Tests are part of the task**, not optional. Async unit tests live in `src/test/java` mirroring the source package. Integration tests are `*IT.java`. See [§Testing Conventions](#testing-conventions).
8. **Version catalog** (`gradle/libs.versions.toml`) is the single source for dependency versions. No hardcoded versions in `build.gradle.kts`.
9. **No new libraries** without justification documented in a task note or ADR.
10. **Fix-forward at storage and runtime boundaries; bounded compatibility at ingestion boundaries.** Replace stale references and divergent storage values, but continue accepting documented legacy aliases in loaders and catalog validators until migration is complete.
11. **Privacy, security, observability, and explainability are release concerns, not side work.** Any task that changes execution, memory, tooling, rollout, or telemetry must state its impact on redaction, auditability, and operator explanation.
12. **Zero-warning build** after every task. Fix lint and checkstyle before opening a PR.

---

## Dependency Graph Between Phases

```
Track X (trust/privacy/security/explainability/capability gates)
    ├── establishes mandatory release gates and telemetry/explanation contracts
    ├── constrains Phases 2, 3, 5, 6, 7, and 8
    └── starts with Phase 1 inventory and continues through rollout

Phase 0 (docs fix)
    ↓
Phase 1 (enum + spec canonicalization)    ← must complete before Phases 2, 3, 4, 8
    ↓                  ↓                         ↓
Phase 2 (release)    Phase 3 (tools)         Phase 8 (pluggability + inter-agent protocol)
    ↓                  ↓                         ↓ (P8-T1/T2 parallel with Phase 2)
Phase 5 (evidence)   Phase 4 (planning)      P8-T3 through T12 require Phase 2 + Phase 3
    ↓
Phase 6 (observability) ← can start in parallel with Phase 4/5; P8 extends Phase 6 telemetry
    ↓
Phase 7 (audio-video)   ← starts after Phase 3 + Phase 6 + Phase 8 P8-T3 foundation
```

Phases 0 and 6 have no code dependencies. Any engineer can pick up Phase 0 concurrently.  
Phase 6 can be started in parallel with Phase 4/5 if separate teams are available.  
Phase 8 can start P8-T1 and P8-T2 in parallel with Phase 2, but P8-T3 onward requires Phase 2 (AgentRelease) and Phase 3 (ToolContract) to be complete.
Track X starts immediately after Phase 0 and remains active across every implementation phase. No Phase 2, 3, 5, 6, 7, or 8 deliverable is considered complete until the relevant Track X gate is satisfied.

---

## Testing Conventions

| Test Type            | Suffix               | Base Class                               | Location                   |
| -------------------- | -------------------- | ---------------------------------------- | -------------------------- |
| Unit (sync)          | `*Test.java`         | —                                        | `src/test/java/...` mirror |
| Unit (ActiveJ async) | `*Test.java`         | `EventloopTestBase`                      | `src/test/java/...` mirror |
| Integration          | `*IT.java`           | `EventloopTestBase` or `@Testcontainers` | `src/test/java/...` mirror |
| Contract             | `*ContractTest.java` | JUnit 5                                  | `src/test/java/...` mirror |

Use `lenient().when(...)` for stubs only called in a subset of tests. Stub all chain-called methods for ActiveJ `Promise.then()` chains.

---

## Phase 0: Normalize Truth Sources

> **Goal:** Make documents match code reality. No code changes. Documentation and cross-reference fixes only.  
> **Why First:** Every later task inherits confusion if the docs say "agent-runtime is active" when settings.gradle.kts says it moved to AEP.

### P0-T1 — Fix `docs/SHARED_LIBRARY_REGISTRY.md`

**What:**  
Update all stale module paths. Remove references to the archived `platform/java/agent-runtime`. Replace with the correct split: contracts in `platform/java/agent-core`, advanced runtime in `products/aep/aep-agent-runtime`, durable registry in `products/data-cloud/agent-registry`.

**Where:** `docs/SHARED_LIBRARY_REGISTRY.md`

**How:**

- Find every row containing `platform/java/agent-runtime` → replace with correct AEP path.
- Find every row containing `platform/java/plugin` → replace with `platform-kernel`.
- Add a new "Archived" section listing modules removed from `settings.gradle.kts` to make removals explicit and intentional.
- Add an "Ownership Matrix" table:

| Concern                                         | Module                               | Owner       |
| ----------------------------------------------- | ------------------------------------ | ----------- |
| Agent contracts + SPI                           | `platform/java/agent-core`           | Platform    |
| Advanced runtime (dispatch, governed execution) | `products/aep/aep-agent-runtime`     | AEP         |
| Durable registry (releases, metadata)           | `products/data-cloud/agent-registry` | Data Cloud  |
| Tool governance                                 | `platform/java/tool-runtime`         | Platform    |
| Durable workflow                                | `platform/java/workflow`             | Platform    |
| Plugin/kernel packaging                         | `platform-kernel`                    | Platform    |
| Multimodal capability tools                     | `products/audio-video`               | Audio-Video |

**Acceptance:** No row in the registry points to a path that does not exist in the repo.

---

### P0-T2 — Fix `docs/adr/ADR-001-typed-agent-framework.md`

**What:**  
The ADR describes a six-type taxonomy. `AgentType.java` now has nine canonical types plus deprecated `LLM`. Update the ADR to reflect the current nine-type model.

**Where:** `docs/adr/ADR-001-typed-agent-framework.md`

**How:**

- Open `platform/java/agent-core/src/main/java/com/ghatana/agent/AgentType.java` and read the current enum values.
- Update the ADR's agent type table to list all nine types with their determinism profile and status.
- Mark deprecated `LLM` type as deprecated with migration note: "Use `PROBABILISTIC` with LLM reasoning profile."
- Add note that `CUSTOM` type is extensible via `AgentLogicProvider` SPI.

**Acceptance:** ADR table exactly mirrors `AgentType.java` enum constants.

---

### P0-T3 — Fix `docs/agent-system/README.md`

**What:**  
The README references `Unified_Self_Learning_Agents_Spec_Final.md`, but the actual file in the folder is `Unified_Self_Learning_Agents_Spec_Merged.md`. Also, the Implementation Architecture section references old paths.

**Where:** `docs/agent-system/README.md`

**How:**

- Replace every reference to `Unified_Self_Learning_Agents_Spec_Final.md` with `Unified_Self_Learning_Agents_Spec_Merged.md`.
- Update the three-layer architecture diagram to the five-layer model from the blueprint (Spec, Control, Execution, Memory/Eval, Capability).
- Update module paths in the table.

**Acceptance:** All hyperlinks in the README resolve to files that exist. No stale paths.

---

### P0-T4 — Add ADR for the Five-Layer Architecture

**What:**  
Create a new ADR that formally documents the five-layer operating model and authority decisions from the blueprint. This locks in the architecture as a decision record.

**Where:** `docs/adr/ADR-020-agent-system-five-layer-architecture.md`

**How:**  
Use the existing ADR template from `docs/adr/README.md`. The ADR must contain:

- Context: current split across four authority surfaces
- Decision: five-layer model (Spec, Control, Execution, Memory/Eval, Capability) with explicit owner per layer
- Consequences: what each product team keeps, what moves, what is not allowed
- Status: `Accepted`

**Acceptance:** ADR numbered correctly, follows template, merged to `main`.

---

## Phase 1: Canonicalize Enums and Spec Storage

> **Goal:** One canonical vocabulary for agent type, autonomy scale, determinism, state mutability, and failure mode. Loaders accept aliases but store canonical values. Spec output is versioned.  
> **Why:** Every other phase depends on a stable, unambiguous vocabulary. Drift between `mission-critical` vs `critical` causes silent bugs in policy evaluation and telemetry.

### P1-T1 — Audit All Enum Usages Across the Codebase

**What:**  
Find every place where agent-related enum string values are used — in YAML catalog files, spec loaders, tests, and product-specific configs — and produce an inventory of variant spellings.

**Where:**

- `platform/java/agent-core/src/`
- `platform/agent-catalog/` (if exists)
- `products/aep/agent-catalog/`
- `products/data-cloud/agent-catalog/`
- Any YAML under `products/audio-video/`

**How:**

```bash
# Run from repo root
grep -rn "mission-critical\|semi-autonomous\|fully-deterministic\|persistent" \
  --include="*.yaml" --include="*.yml" --include="*.java" \
  platform/ products/ | sort
```

- Build a table: current value → canonical value → modules affected.
- Document in `docs/agent-system/enum-canonicalization-inventory.md`.

**Acceptance:** Inventory document exists, reviewed, and committed before any code changes.

---

### P1-T2 — Canonicalize the Existing `AutonomyLevel` Model and Compatibility Mappings

**What:**  
Align the implementation plan with the autonomy model that already exists in `agent-core`, and make that model the single canonical runtime vocabulary across catalogs, loaders, and product adapters.

**Where:**

- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/AutonomyLevel.java`
- `platform/agent-catalog/catalog-schema.yaml`
- `products/data-cloud/platform-api/src/main/java/com/ghatana/datacloud/client/autonomy/AutonomyLevel.java`

**How:**

- Treat the existing runtime enum as canonical unless there is a deliberate ADR to change it.
- Preserve the five current canonical values:
  ```
  ADVISORY, DRAFT, SUPERVISED, BOUNDED_AUTONOMOUS, AUTONOMOUS
  ```
- Keep legacy ingestion aliases (`manual`, `assisted`, `semi-autonomous`) in loader/catalog compatibility paths, but do not persist them as stored values.
- Audit the Data Cloud client autonomy enum and either map it explicitly to the same canonical vocabulary or document the reason it must stay product-local.
- Update any doc text that still describes a different autonomy scale.

**Files:**

- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/AutonomyLevel.java`
- `platform/java/agent-core/src/test/java/com/ghatana/agent/framework/runtime/AutonomyLevelTest.java`
- `platform/agent-catalog/catalog-schema.yaml`

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/framework/runtime/AutonomyLevelTest.java`

- Verify current canonical values serialize and deserialize cleanly.
- Verify legacy aliases continue to map to canonical values.
- Verify catalog validation rejects non-canonical stored values while still allowing loader-side normalization at ingress.

**Acceptance:** There is one documented canonical autonomy vocabulary across runtime and catalog storage, with explicit compatibility mappings for legacy values.

---

### P1-T3 — Canonicalize `AgentType` Aliases

**What:**  
Add `@JsonAlias` annotations or a custom Jackson deserializer to `AgentType.java` so that old YAML spellings load without error, but the stored value is always canonical.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/AgentType.java`

**How:**

- Add `@JsonAlias({"llm", "LLM"})` to `PROBABILISTIC`.
- Add similar aliases to any other types with known drift found in P1-T1.
- Add deprecation notices and Javadoc migration notes for the deprecated `LLM` constant.
- Update `agent-base-schema.json` to list all canonical values in the enum field.

**Files:**

- `platform/java/agent-core/src/main/java/com/ghatana/agent/AgentType.java`
- `platform/java/agent-core/src/main/resources/schemas/agent-base-schema.json`

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/AgentTypeSerializationTest.java`

- Test that each alias value deserializes to the canonical enum constant.
- Test that unknown values produce a clear `IllegalArgumentException`.

**Acceptance:** YAML with old spellings loads into canonical enums. No silent coercion.

---

### P1-T4 — Normalize `agentSpecVersion` Handling in the Existing `AgentSpec` Model

**What:**  
Use the existing `agentSpecVersion` field as the canonical spec-format version field, tighten validation around it, and avoid introducing a second conflicting field such as `specVersion` unless an ADR deliberately replaces it.

**Where:**

- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/AgentSpec.java` (or the equivalent spec model class)
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/loader/AgentSpecLoader.java`

**How:**

- Keep `AgentSpec.agentSpecVersion` as the single canonical field for spec format version.
- Preserve current loader behavior that defaults missing values to `"1.0.0"` for legacy flat-format YAML.
- Validate against the supported set of fully qualified version strings actually used by the loader and tests, starting with `{"1.0.0", "2.0.0"}` unless implementation inspection expands the set.
- If a spec includes `specVersion` but not `agentSpecVersion`, fail validation with a descriptive migration error rather than silently accepting two version-field names.
- If `agentSpecVersion` is unrecognized, throw a descriptive checked exception: `UnsupportedSpecVersionException`.
- Update docs and examples so future specs use `agentSpecVersion` consistently.

**New files (only if missing):**

- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/AgentSpec.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/loader/UnsupportedSpecVersionException.java`

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/framework/spec/AgentSpecLoaderTest.java`

- Test: spec with `agentSpecVersion: "2.0.0"` loads cleanly.
- Test: spec missing `agentSpecVersion` loads with warning, defaults to `"1.0.0"`.
- Test: spec with `agentSpecVersion: "99.0.0"` throws `UnsupportedSpecVersionException`.
- Test: spec with `specVersion` but no `agentSpecVersion` fails with a migration error explaining the canonical field name.

**Acceptance:** Loader and docs converge on one canonical spec-format field name, while maintaining safe ingestion of legacy specs already supported by the repo.

---

### P1-T5 — Add Spec Validation for Enum Consistency

**What:**  
Add a `AgentSpecValidator` that validates the loaded spec for enum consistency: all fields that reference canonical enum values use the canonical spelling.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/AgentSpecValidator.java`

**How:**

- Implement `AgentSpecValidator` as a pure, stateless class (no DI needed) with a single `validate(AgentSpec)` method returning `ValidationResult`.
- Check: `agentType` is a known `AgentType`.
- Check: `autonomyLevel` is a known `AutonomyLevel`.
- Check: `stateMutability` is a known `StateMutability`.
- Check: `determinism` is a known `DeterminismGuarantee`.
- For each failure, emit a structured `ValidationIssue(field, value, message)`.
- `ValidationResult` has `isValid()`, `getIssues()`, `throwIfInvalid()`.

**Files:**

- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/AgentSpecValidator.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/ValidationResult.java`
- `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/ValidationIssue.java`

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/framework/spec/AgentSpecValidatorTest.java`

- Test: valid spec → `isValid() == true`.
- Test: spec with unknown `agentType` → issue in results, `isValid() == false`.
- Test: `throwIfInvalid()` throws `AgentSpecValidationException` with all issues listed.

**Acceptance:** Validator is pure, has 100% branch coverage, throws descriptive errors.

---

### P1-T6 — Update Agent Catalog YAML Files to Canonical Values

**What:**  
Update all YAML catalog files to use canonical enum spellings following the inventory from P1-T1.

**Where:**

- `products/aep/agent-catalog/agent-catalog.yaml`
- `products/aep/agent-catalog/operators/*.yaml`
- `products/aep/agent-catalog/capabilities/*.yaml`
- `products/data-cloud/agent-catalog/` (if exists)

**How:**

- Replace every non-canonical value with its canonical counterpart using the P1-T1 inventory table.
- Run `AgentSpecValidator` against all updated YAML files in a CI test to prevent regression.
- Add a Gradle task `validateAgentCatalogs` that runs the validator against all catalog YAML paths.

**Test:** Add catalog validation test:
`products/aep/orchestrator/src/test/java/com/ghatana/aep/integration/registry/CatalogCanonicalValuesTest.java`

- Loads every catalog YAML and asserts `AgentSpecValidator.validate(spec).isValid()`.

**Acceptance:** All catalog files pass validation. CI task `validateAgentCatalogs` passes on every commit.

---

## Phase 2: Introduce Release Artifacts

> **Goal:** A deployable agent is not just a spec file. It is a versioned, signed, policy-linked, evaluation-linked `AgentRelease`. This phase introduces the release model, the lifecycle states, and persists releases in Data Cloud.  
> **Why:** Without a release model, rollback, canary promotion, and audit are impossible.

### P2-T1 — Define `AgentRelease` Model in `agent-core`

**What:**  
Create the `AgentRelease` Java record in `platform/java/agent-core`. This is a new first-class platform contract.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/release/`

**How:**  
Create the following as Java records (Java 21):

```java
// AgentRelease.java
/**
 * @doc.type record
 * @doc.purpose Immutable, versioned, signed release of an agent spec.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentRelease(
    String agentReleaseId,           // UUID, immutable
    String agentId,                  // links back to AgentSpec.agentId
    String specVersion,              // mirrors AgentSpec.agentSpecVersion, e.g. "2.0.0"
    String releaseVersion,           // e.g., "1.3.2" (semver)
    AgentReleaseState state,         // DRAFT, VALIDATED, SHADOW, CANARY, ACTIVE, DEPRECATED, RETIRED, BLOCKED
    String specDigest,               // SHA-256 of the spec YAML
    String policyPackId,             // ID of PolicyPack attached at release time
    String policyPackDigest,         // SHA-256 of the PolicyPack
    String evaluationPackId,         // ID of EvaluationPack that passed this release
    String evaluationPackDigest,
    String memoryContractId,         // ID of MemoryContract
    List<String> compatibleRuntimeVersions,  // e.g., ["aep-runtime:2.x"]
    String signingReference,         // Sigstore bundle or attestation reference (nullable)
    String toolContractVersion,      // version of ToolContract at release time
    String telemetryContractVersion, // version of AgentTelemetryContract expected by this release
    String explanationContractVersion,
    String redactionProfileId,
    String threatModelId,
    Set<String> dataClassesHandled,
    Set<String> permittedPurposes,
    String capabilityMaturityProfile,
    Instant createdAt,
    Instant updatedAt,
    String createdBy                 // principal that created the release
) {}
```

These extra fields are not paperwork. They make privacy, security, observability, explainability, and capability claims queryable at release-admission time instead of being buried in wiki prose.

```java
// AgentReleaseState.java
/**
 * @doc.type enum
 * @doc.purpose Release lifecycle state machine for agent releases.
 * @doc.layer platform
 * @doc.pattern StateMachine
 */
public enum AgentReleaseState {
    DRAFT, VALIDATED, SHADOW, CANARY, ACTIVE, DEPRECATED, RETIRED, BLOCKED;

    /** Returns the set of allowed next states from this state. */
    public Set<AgentReleaseState> allowedTransitions() { ... }
}
```

```java
// PolicyPack.java
/**
 * @doc.type record
 * @doc.purpose Versioned bundle of governance policies for an agent release.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PolicyPack(
    String policyPackId,
    String version,
    Set<String> allowedActionClasses,   // READ, DRAFT, WRITE_REVERSIBLE, etc.
    Map<String, String> sandboxRules,
    Map<String, Object> delegationBudgets,
    Map<String, String> egressRules,
    Map<String, String> dataClassificationRules,
    String digest,
    Instant createdAt
) {}
```

```java
// EvaluationPack.java
/**
 * @doc.type record
 * @doc.purpose Versioned evaluation benchmark suite attached to an agent release.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record EvaluationPack(
    String evaluationPackId,
    String version,
    List<String> benchmarkSuiteIds,
    List<String> regressionGateIds,
    Map<String, Double> promotionThresholds,  // e.g., {"accuracy": 0.95}
    String digest,
    Instant createdAt
) {}
```

```java
// MemoryContract.java
/**
 * @doc.type record
 * @doc.purpose Memory governance contract for an agent release.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record MemoryContract(
    String memoryContractId,
    String version,
    Set<String> allowedMemoryClasses,    // working, task-state, episodic, semantic, procedural
    Map<String, Duration> retentionRules,
    Set<String> provenanceRequirements,
    Set<String> redactionRequirements,
    String shareabilityMode,             // PRIVATE, TEAM, TENANT, PUBLIC
    String digest,
    Instant createdAt
) {}
```

**Files (all in `platform/java/agent-core/src/main/java/com/ghatana/agent/release/`):**

- `AgentRelease.java`
- `AgentReleaseState.java`
- `PolicyPack.java`
- `EvaluationPack.java`
- `MemoryContract.java`
- `AgentReleaseBuilder.java` (builder for `AgentRelease` with fluent API)

**Tests:** `platform/java/agent-core/src/test/java/com/ghatana/agent/release/`

- `AgentReleaseTest.java` — immutability, serialization, builder
- `AgentReleaseStateTest.java` — state transition rules: test all valid transitions, test all invalid transitions throw

**Acceptance:** Records compile, are Jackson-serializable, all state transitions are explicitly modeled.

---

### P2-T2 — Add `AgentInstanceConfig` Model

**What:**  
Create `AgentInstanceConfig` as the tenant-scoped runtime configuration overlay for a given release.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentInstanceConfig.java`

**How:**  
Java record:

```java
/**
 * @doc.type record
 * @doc.purpose Tenant-scoped runtime configuration overlay for an agent release.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentInstanceConfig(
    String instanceConfigId,
    String agentReleaseId,     // references AgentRelease
    String tenantId,
    String environment,        // dev, staging, production
    Map<String, String> modelOverrides,    // e.g., provider, model name
    Map<String, Object> budgets,           // token, cost, time budgets
    Map<String, String> featureFlags,      // rollout toggles
    Map<String, String> environmentBindings, // env var bindings
    boolean killSwitch,
    Instant createdAt,
    Instant updatedAt
) {}
```

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/release/AgentInstanceConfigTest.java`

- Test: Jackson round-trip serialization.
- Test: killSwitch=true causes dispatch to reject the instance (integration: see P2-T5).

**Acceptance:** Record compiles, serializes, documented.

---

### P2-T3 — Define `AgentReleaseRepository` SPI

**What:**  
Add an SPI interface for release persistence so that `agent-core` defines the contract and `data-cloud` provides the implementation.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentReleaseRepository.java`

**How:**

```java
/**
 * @doc.type interface
 * @doc.purpose SPI for persisting and querying agent releases.
 * @doc.layer platform
 * @doc.pattern Repository
 */
public interface AgentReleaseRepository {
    Promise<AgentRelease> save(AgentRelease release);
    Promise<Optional<AgentRelease>> findById(String agentReleaseId);
    Promise<List<AgentRelease>> findByAgentId(String agentId);
    Promise<Optional<AgentRelease>> findActiveRelease(String agentId, String tenantId);
    Promise<AgentRelease> transition(String agentReleaseId, AgentReleaseState targetState, String principalId);
    Promise<List<AgentRelease>> findByState(AgentReleaseState state);
}
```

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/release/AgentReleaseRepositoryContractTest.java`

- Abstract contract test that any implementation must satisfy.
- Use `InMemoryAgentReleaseRepository` as the test vehicle.

**Acceptance:** Interface exists with Javadoc. `InMemoryAgentReleaseRepository` used for testing.

---

### P2-T4 — Implement `DataCloudAgentReleaseRepository` in Data Cloud

**What:**  
Implement `AgentReleaseRepository` in `products/data-cloud/agent-registry` backed by the Data Cloud persistence layer.

**Where:** `products/data-cloud/agent-registry/src/main/java/com/ghatana/datacloud/agent/registry/release/DataCloudAgentReleaseRepository.java`

**How:**

- Implement `AgentReleaseRepository` SPI.
- Use the existing JDBI/jOOQ/Flyway persistence pattern already present in `products/data-cloud`.
- Add Flyway migration for the `agent_releases` table:

```sql
-- V{next}__create_agent_releases.sql
CREATE TABLE agent_releases (
    agent_release_id   VARCHAR(36)  PRIMARY KEY,
    agent_id           VARCHAR(255) NOT NULL,
    spec_version       VARCHAR(20)  NOT NULL,
    release_version    VARCHAR(50)  NOT NULL,
    state              VARCHAR(30)  NOT NULL,
    spec_digest        VARCHAR(64)  NOT NULL,
    policy_pack_id     VARCHAR(36),
    policy_pack_digest VARCHAR(64),
    eval_pack_id       VARCHAR(36),
    eval_pack_digest   VARCHAR(64),
    memory_contract_id VARCHAR(36),
    tool_contract_ver  VARCHAR(50),
    signing_reference  TEXT,
    compatible_runtimes JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(255) NOT NULL
);
CREATE INDEX idx_agent_releases_agent_id ON agent_releases(agent_id);
CREATE INDEX idx_agent_releases_state ON agent_releases(state);
```

- Add JSON-backed tables for `policy_packs`, `evaluation_packs`, `memory_contracts`.
- Implement `transition()` with a state-machine validation using `AgentReleaseState.allowedTransitions()`.
- Emit a structured log on every state transition.

**Tests:**

- `products/data-cloud/agent-registry/src/test/java/com/ghatana/datacloud/agent/registry/release/DataCloudAgentReleaseRepositoryIT.java`
- Extends `AgentReleaseRepositoryContractTest`.
- Uses Testcontainers PostgreSQL.

**Acceptance:** All contract tests pass against PostgreSQL. State machine violations are rejected with a clear exception.

---

### P2-T5 — Make AEP Dispatch Release-Aware

**What:**  
Update `GovernedAgentDispatcher` and the AEP central registry to look up the active `AgentRelease` before dispatching, and reject instances where `killSwitch=true` or `state=BLOCKED`.

**Where:** `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`

**How:**

- Inject `AgentReleaseRepository` into `GovernedAgentDispatcher`.
- Before dispatching, call `findActiveRelease(agentId, tenantId)`.
- If no active release: deny with `AgentExecutionGrant.denied("NO_ACTIVE_RELEASE")`.
- If `killSwitch=true`: deny with `AgentExecutionGrant.denied("KILL_SWITCH_ACTIVE")`.
- If `state=BLOCKED`: deny with `AgentExecutionGrant.denied("RELEASE_BLOCKED")`.
- Attach `agentReleaseId` to `InvariantContext` for downstream use in trace/telemetry.

**Test:** `products/aep/aep-agent-runtime/src/test/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcherTest.java`

- Test: active release → grant issued.
- Test: no active release → denied.
- Test: kill switch → denied.
- Test: state=BLOCKED → denied.

**Acceptance:** Dispatcher rejects all disallowed states. `agentReleaseId` attached to context.

---

### P2-T6 — Add Tenant-Scoped Rollout Records

**What:**  
Add a `AgentRolloutRecord` entity in Data Cloud to store per-tenant rollout state (traffic split, fallback release, approval status).

**Where:** `products/data-cloud/agent-registry/src/main/java/com/ghatana/datacloud/agent/registry/rollout/`

**How:**

- Create `AgentRolloutRecord` as a Java record:
  ```java
  public record AgentRolloutRecord(
      String rolloutId, String agentId, String tenantId, String environment,
      String targetReleaseId, String fallbackReleaseId,
      int trafficSplitPercent,  // 0–100
      AgentRolloutApprovalState approvalState,
      boolean killSwitch, Instant createdAt, Instant updatedAt
  ) {}
  ```
- Add `AgentRolloutApprovalState` enum: `PENDING`, `APPROVED`, `REJECTED`.
- Add Flyway migration for `agent_rollout_records` table.
- Add `AgentRolloutRepository` SPI in `agent-core`.
- Implement `DataCloudAgentRolloutRepository`.

**Test:** `products/data-cloud/agent-registry/src/test/java/com/ghatana/datacloud/agent/registry/rollout/DataCloudAgentRolloutRepositoryIT.java`

- Testcontainers PostgreSQL.
- Test CRUD + approval state transitions.

**Acceptance:** Rollout records persist, approval state machine works, kill switch is testable.

---

## Phase 3: Unify Tool Contracts

> **Goal:** Every tool call — in-process, sandboxed, remote, or MCP-compatible — goes through one normalized `ToolContract` with consistent action classification, audit metadata, and approval hooks.  
> **Why:** Tool execution is where governance, safety, interoperability, and modern agent standards meet. The existing `ToolSandbox` and `ToolExecutionStats` are too thin.

### P3-T1 — Define `ActionClass` Enum in `agent-core`

**What:**  
Create a canonical `ActionClass` enum to classify tool actions by their risk and reversibility profile.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/tools/ActionClass.java`

**How:**

```java
/**
 * @doc.type enum
 * @doc.purpose Canonical action class taxonomy for tool execution governance.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ActionClass {
    READ,                // Idempotent reads
    DRAFT,               // Creates a draft without persisting
    WRITE_REVERSIBLE,    // Persisting write that can be rolled back
    WRITE_IRREVERSIBLE,  // Persisting write that cannot be rolled back
    CALL_EXTERNAL,       // Calls a remote external service
    DELEGATE,            // Spawns a sub-agent or delegates to another agent
    MEMORY_MUTATION,     // Writes to durable memory
    POLICY_CHANGE        // Modifies a policy or governance rule
}
```

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/framework/tools/ActionClassTest.java`

- Verify `READ` and `DRAFT` are considered low-risk (implement a `isLowRisk()` method).
- Verify `WRITE_IRREVERSIBLE` and `POLICY_CHANGE` require approval by default.

**Acceptance:** Enum exists, documents its risk profile per value, test green.

---

### P3-T2 — Define `ToolContract` Model in `agent-core`

**What:**  
Introduce `ToolContract` as the normalized descriptor for any tool, regardless of whether it runs in-process, sandboxed, or remotely.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/tools/`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Normalized, schema-driven tool descriptor for in-process,
 *             sandboxed, remote, and MCP-compatible tools.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ToolContract(
    String toolId,
    String toolVersion,
    String name,
    String description,
    ActionClass actionClass,
    boolean requiresApproval,
    boolean isReversible,
    Map<String, Object> inputSchema,   // JSON Schema (Map form)
    Map<String, Object> outputSchema,
    Set<String> policyTags,            // e.g., {"pii-allowed", "external-call"}
    ToolTransport transport,           // IN_PROCESS, SANDBOX, REMOTE, MCP
    String remoteEndpoint,             // null for IN_PROCESS
    Map<String, String> metadata
) {}
```

```java
/**
 * @doc.type enum
 * @doc.purpose Transport type for a tool implementation.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ToolTransport { IN_PROCESS, SANDBOX, REMOTE, MCP }
```

**Files:**

- `ToolContract.java`
- `ToolTransport.java`

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/framework/tools/ToolContractTest.java`

- JSON round-trip.
- Verify `requiresApproval` defaults correctly by `ActionClass`.

**Acceptance:** Record compiles, serializes, documented with all four `@doc.*` tags.

---

### P3-T3 — Define `ToolExecutionEnvelope` and `ToolExecutionResult`

**What:**  
Every tool invocation should produce a normalized execution envelope capturing full audit and trace metadata.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/tools/`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Normalized execution envelope for a single tool invocation.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ToolExecutionEnvelope(
    String invocationId,        // UUID per call
    String toolId,
    String toolVersion,
    String callerAgentId,
    String callerReleaseId,     // from AgentRelease
    String tenantId,
    ActionClass actionClass,
    String requestSchemaVersion,
    Map<String, Object> input,
    Instant requestedAt
) {}

/**
 * @doc.type record
 * @doc.purpose Normalized result of a tool execution including policy and approval decisions.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ToolExecutionResult(
    String invocationId,
    ToolExecutionStatus status,  // SUCCESS, FAILED, DENIED, APPROVAL_PENDING, TIMEOUT
    Object output,
    String policyDecision,       // ALLOW, DENY, CONDITIONAL
    String approvalDecision,     // APPROVED, DENIED, PENDING, N/A
    Map<String, Object> sideEffectSummary,
    String errorMessage,
    String correlationId,
    Instant completedAt,
    Duration executionDuration
) {}

public enum ToolExecutionStatus { SUCCESS, FAILED, DENIED, APPROVAL_PENDING, TIMEOUT }
```

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/framework/tools/ToolExecutionResultTest.java`

**Acceptance:** Records compile, serialize/deserialize, all status values covered.

---

### P3-T4 — Expand `platform/java/tool-runtime` with Governance Layer

**What:**  
The existing `tool-runtime` module has only `ToolSandbox` and `ToolExecutionStats`. Expand it to be the canonical tool governance boundary by implementing `ToolExecutor` — the single entry point for all tool calls.

**Where:** `platform/java/tool-runtime/src/main/java/com/ghatana/platform/toolruntime/`

**How:**
Create `ToolExecutor` interface:

```java
/**
 * @doc.type interface
 * @doc.purpose Canonical entry point for governed tool execution. All side-effecting
 *             tool calls from any agent must route through this executor.
 * @doc.layer platform
 * @doc.pattern Facade, Strategy
 */
public interface ToolExecutor {
    /**
     * Executes a tool call described by the envelope.
     * Applies policy checks, approval workflows, sandbox, and audit emission before and after execution.
     */
    Promise<ToolExecutionResult> execute(ToolExecutionEnvelope envelope, ToolContract contract);

    /** Register a tool handler for the given toolId. */
    void register(String toolId, ToolHandler handler);
}
```

Create `DefaultToolExecutor`:

- Validates `ToolExecutionEnvelope` against `ToolContract.inputSchema`.
- Checks `ActionClass` against allowed classes from `PolicyPack` (injected via `PolicyEvalResult`).
- If `requiresApproval=true` and class is high-risk: call `ApprovalGateway.requestApproval(...)` and return `APPROVAL_PENDING`.
- Delegates execution to the registered `ToolHandler`.
- Wraps result into `ToolExecutionResult` with policy and approval decisions.
- Emits a structured audit event.

Create supporting types:

- `ToolHandler` interface: `Promise<Object> handle(ToolExecutionEnvelope envelope)`
- `ApprovalGateway` interface: `Promise<ApprovalDecision> requestApproval(ToolExecutionEnvelope envelope)`
- `InProcessToolHandler` — wraps an existing `FunctionTool` or lambda
- `SandboxToolHandler` — delegates to `ToolSandbox`

**Files (in `platform/java/tool-runtime/src/main/java/com/ghatana/platform/toolruntime/`):**

- `ToolExecutor.java`
- `DefaultToolExecutor.java`
- `ToolHandler.java`
- `ApprovalGateway.java`
- `InProcessToolHandler.java`
- `SandboxToolHandler.java`
- `ApprovalDecision.java` (enum: `APPROVED`, `DENIED`, `PENDING`)

**Test:** `platform/java/tool-runtime/src/test/java/com/ghatana/platform/toolruntime/DefaultToolExecutorTest.java`

- Test: `READ` action → no approval gate, executes directly.
- Test: `WRITE_IRREVERSIBLE` with `requiresApproval=true` → returns `APPROVAL_PENDING`.
- Test: policy denial → `DENIED` status, no handler called.
- Test: handler exception → `FAILED` status, error message populated.
- Extend `EventloopTestBase` for all async tests.

**Acceptance:** `DefaultToolExecutor` passes all tests. Every code path has a test. Audit event emitted on each call.

---

### P3-T5 — Add MCP-Aligned Adapter Interface in `tool-runtime`

**What:**  
Add `McpToolAdapter` so that tools described by the Model Context Protocol (revision 2025-03-26) can be invoked via the standard `ToolExecutor`.

**Where:** `platform/java/tool-runtime/src/main/java/com/ghatana/platform/toolruntime/mcp/`

**How:**

- Create `McpToolAdapter` that implements `ToolHandler`.
- Translates `ToolExecutionEnvelope` into an MCP tool call payload (JSON-RPC 2.0 format).
- Sends the call to the MCP server over HTTP using the existing `platform:java:http` client.
- Parses the MCP response into `ToolExecutionResult`.
- Maps MCP error codes to `ToolExecutionStatus`.

**Key types:**

- `McpToolAdapter.java` — implements `ToolHandler`
- `McpToolRequest.java` — MCP tool call payload
- `McpToolResponse.java` — MCP tool result payload

**Test:** `platform/java/tool-runtime/src/test/java/com/ghatana/platform/toolruntime/mcp/McpToolAdapterTest.java`

- Use WireMock to simulate an MCP server.
- Test: successful tool response → `SUCCESS` status.
- Test: MCP error response → `FAILED` status with error message.
- Test: MCP timeout → `TIMEOUT` status.

**Acceptance:** MCP adapter works end-to-end in tests with a WireMock MCP server.

---

### P3-T6 — Route AEP Agent Tool Calls Through `ToolExecutor`

**What:**  
Update AEP agent runtime so that all side-effecting tool calls from agents go through the new `ToolExecutor` instead of calling tool implementations directly.

**Where:** `products/aep/aep-agent-runtime/` (wherever tool execution currently happens)

**How:**

- Identify existing tool invocation sites by searching for `FunctionTool`, `ToolRegistry`, direct tool calls.
- Replace each site with `toolExecutor.execute(envelope, contract)`.
- Build `ToolExecutionEnvelope` from the current `AgentContext` (pull `callerAgentId`, `tenantId`, `correlationId`).
- Pull `ToolContract` from the agent's registered tool contracts (add a `getToolContracts()` method to `AgentDescriptor` or a dedicated `ToolContractRegistry`).

**Test:** Update existing AEP runtime safety tests to verify tool calls go through `ToolExecutor`.

**Acceptance:** No direct tool implementation calls exist outside `ToolExecutor`. Verified by ArchUnit test in `products/aep`.

---

## Phase 4: Planning as Durable Workflow Compilation

> **Goal:** Planning agents compile their plan graphs into `platform/java/workflow` definitions, which AEP then executes durably. No private orchestration semantics outside the durable workflow runtime.  
> **Why:** Durable, inspectable, pause/resume-able planning is better than per-planner private state.

### P4-T1 — Define `PlanGraph` and `PlanStep` Models in `agent-core`

**What:**  
Add a portable `PlanGraph` model that planning agents produce and that the workflow compiler consumes.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/planning/`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Portable directed acyclic graph produced by a planning agent.
 *             Compiled into a durable workflow definition for execution.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PlanGraph(
    String planId,
    String agentId,
    String correlationId,
    List<PlanStep> steps,        // ordered list (DAG edges via dependsOn)
    Map<String, Object> planMetadata
) {}

/**
 * @doc.type record
 * @doc.purpose Single step in a PlanGraph.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PlanStep(
    String stepId,
    String name,
    String toolId,               // tool to invoke (references ToolContract)
    Map<String, Object> input,
    List<String> dependsOn,      // stepIds that must complete before this step
    boolean allowHitlPause,      // pause for human-in-the-loop approval before executing
    ActionClass actionClass,
    Map<String, Object> stepMetadata
) {}
```

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/planning/PlanGraphTest.java`

- Test: DAG with dependencies compiles.
- Test: cycle detection throws `InvalidPlanException`.

**Acceptance:** `PlanGraph` is DAG-validated, serializable, documented.

---

### P4-T2 — Define `PlanCompiler` Interface in `platform/java/workflow`

**What:**  
Define the contract for converting a `PlanGraph` into a `Workflow` definition.

**Where:** `platform/java/workflow/src/main/java/com/ghatana/platform/workflow/planning/PlanCompiler.java`

**How:**

```java
/**
 * @doc.type interface
 * @doc.purpose Compiles a planning agent's PlanGraph into a durable Workflow definition.
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public interface PlanCompiler {
    /**
     * Compiles the given PlanGraph into a Workflow definition ready for submission
     * to the DurableWorkflowRuntime.
     *
     * @param planGraph the plan to compile
     * @param context   execution context (tenant, agent release, etc.)
     * @return the compiled workflow definition
     * @throws PlanCompilationException if the graph cannot be compiled
     */
    Promise<Workflow> compile(PlanGraph planGraph, AgentContext context);
}
```

Implement `DefaultPlanCompiler`:

- Maps each `PlanStep` to a workflow `StepDefinition`.
- Maps `dependsOn` to workflow step dependencies.
- Inserts HITL pause steps where `allowHitlPause=true`.
- Maps `actionClass` to the appropriate `StepOperator` type.

**Files:**

- `PlanCompiler.java`
- `DefaultPlanCompiler.java`
- `PlanCompilationException.java`

**Test:** `platform/java/workflow/src/test/java/com/ghatana/platform/workflow/planning/DefaultPlanCompilerTest.java`

- Test: linear plan → sequential workflow.
- Test: parallel branches (no dependencies) → parallel workflow steps.
- Test: HITL step inserted correctly.
- Test: cycle in plan → `PlanCompilationException`.

**Acceptance:** Compiler produces valid `Workflow` objects for all plan topologies.

---

### P4-T3 — Add HITL Pause/Resume to Workflow Runtime

**What:**  
Add support for human-in-the-loop pause and resume to `platform/java/workflow`. This allows workflows compiled from plans to halt at approval points and resume after human approval.

**Where:** `platform/java/workflow/src/main/java/com/ghatana/platform/workflow/runtime/`

**How:**

- Add `StepOperator` implementation: `HitlPauseOperator` that suspends workflow execution and emits an approval request event.
- Add `WorkflowRuntime.resume(runId, stepId, approvalDecision)` method.
- Store HITL checkpoint in the workflow state store with `status=WAITING_FOR_HITL`.
- On `resume()`, validate `approvalDecision`, update state to `APPROVED` or `REJECTED`, and continue or abort.

**Test:** `platform/java/workflow/src/test/java/com/ghatana/platform/workflow/runtime/HitlPauseOperatorTest.java`

- Test: step with HITL pause → workflow halts, checkpoint stored.
- Test: resume with APPROVED → workflow continues.
- Test: resume with REJECTED → workflow aborts with reason.

**Acceptance:** HITL pause is durable (survives restart). Resume works. All paths tested.

---

### P4-T4 — Wire Planning Agents to Use `PlanCompiler` in AEP

**What:**  
Update AEP planning agent implementations to output `PlanGraph` and route it through `PlanCompiler → DurableWorkflowRuntime` instead of any private orchestration logic.

**Where:** `products/aep/orchestrator/src/main/java/com/ghatana/orchestrator/`

**How:**

- Identify existing planning execution paths in `Orchestrator.java`.
- Inject `PlanCompiler` and `DurableWorkflowRuntime` into the orchestrator.
- Replace direct plan execution with: `planCompiler.compile(graph, ctx).then(workflow -> workflowRuntime.submit(workflow))`.
- Store plan graph and workflow ID in the execution trace.

**Test:** `products/aep/orchestrator/src/test/java/com/ghatana/orchestrator/core/OrchestratorPipelineIntegrationTest.java` — update to verify plan goes through compiler and workflow runtime.

**Acceptance:** Planning agents no longer use private orchestration semantics. ArchUnit test verifies no direct plan execution outside `PlanCompiler`.

---

## Phase 5: Data Cloud as the Evidence Plane

> **Goal:** Data Cloud becomes the single durable store for agent releases, evaluation artifacts, memory namespaces, rollout state, retrieval indices, and promotion evidence. AEP reads from and writes to this plane.  
> **Why:** Without one evidence plane, rollback is guesswork, evaluation is unverifiable, and memory governance is ad hoc.

### P5-T1 — Expand `agent-registry` Schema for Release and Evaluation Indices

**What:**  
Add schema tables for `evaluation_results`, `memory_namespaces`, and `promotion_evidence`.

**Where:** `products/data-cloud/agent-registry/`

**How — Flyway migrations:**

```sql
-- V{N}__create_evaluation_results.sql
CREATE TABLE evaluation_results (
    eval_result_id       VARCHAR(36) PRIMARY KEY,
    eval_pack_id         VARCHAR(36) NOT NULL,
    agent_release_id     VARCHAR(36) NOT NULL REFERENCES agent_releases(agent_release_id),
    benchmark_suite_id   VARCHAR(100),
    score                NUMERIC(5,4),  -- 0.0 to 1.0
    passed               BOOLEAN NOT NULL,
    run_at               TIMESTAMPTZ NOT NULL,
    metadata             JSONB
);

-- V{N+1}__create_memory_namespaces.sql
CREATE TABLE memory_namespaces (
    namespace_id    VARCHAR(36) PRIMARY KEY,
    agent_id        VARCHAR(255) NOT NULL,
    tenant_id       VARCHAR(255) NOT NULL,
    memory_class    VARCHAR(30) NOT NULL,  -- working, task-state, episodic, semantic, procedural
    retention_days  INTEGER,
    shareability    VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- V{N+2}__create_promotion_evidence.sql
CREATE TABLE promotion_evidence (
    evidence_id         VARCHAR(36) PRIMARY KEY,
    agent_release_id    VARCHAR(36) NOT NULL REFERENCES agent_releases(agent_release_id),
    source_memory_class VARCHAR(30) NOT NULL,
    target_memory_class VARCHAR(30) NOT NULL,
    evidence_type       VARCHAR(50) NOT NULL,  -- EPISODIC_CONSOLIDATION, EVAL_PASS, HUMAN_REVIEW
    approved_by         VARCHAR(255),
    approved_at         TIMESTAMPTZ,
    policy_gate_result  JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Test:** `products/data-cloud/agent-registry/src/test/java/com/ghatana/datacloud/agent/registry/DataCloudAgentRegistryComprehensiveTest.java` — extend with new table coverage.

**Acceptance:** Flyway migrations run clean. New tables are queryable from tests.

---

### P5-T2 — Add `EvaluationResultRepository` and `MemoryNamespaceRepository`

**What:**  
Define the SPIs in `agent-core` and implement them in Data Cloud.

**Where:**

- SPIs: `platform/java/agent-core/src/main/java/com/ghatana/agent/release/EvaluationResultRepository.java`
- Implementation: `products/data-cloud/agent-registry/src/main/java/com/ghatana/datacloud/agent/registry/eval/DataCloudEvaluationResultRepository.java`

**How:**

```java
public interface EvaluationResultRepository {
    Promise<EvaluationResult> save(EvaluationResult result);
    Promise<List<EvaluationResult>> findByReleaseId(String agentReleaseId);
    Promise<Optional<EvaluationResult>> findLatest(String agentReleaseId, String evalPackId);
    Promise<Boolean> hasAllGatesPassed(String agentReleaseId, EvaluationPack pack);
}
```

**Test:** Contract test in `agent-core`, integration test in `data-cloud` with Testcontainers.

**Acceptance:** Evaluation queries work. `hasAllGatesPassed` correctly returns `false` when any gate fails.

---

### P5-T3 — Add Memory Promotion Service

**What:**  
Implement the 7-step episodic-to-procedural memory promotion path defined in the blueprint.

**Where:** `products/data-cloud/platform-api/src/main/java/com/ghatana/datacloud/memory/promotion/MemoryPromotionService.java`

**How:**  
The promotion path:

1. Gather `episodic_evidence` records for the agent.
2. Create a `consolidation_proposal` (mark candidate for promotion).
3. Run `EvaluationPack` gates: `evaluationResultRepository.hasAllGatesPassed(...)`.
4. Evaluate against `PolicyPack` rules via `platform/java/policy-as-code`.
5. If required: emit a HITL approval request event.
6. On approval: write `promoted_procedural_artifact` to `procedural` memory namespace.
7. Create a `PromotionEvidence` record and save to `promotion_evidence` table.
8. Trigger `AgentReleaseRepository.transition(...)` to register promoted procedures as part of a new release.

```java
/**
 * @doc.type class
 * @doc.purpose Governs the 7-step episodic-to-procedural memory promotion lifecycle.
 * @doc.layer product
 * @doc.pattern Service
 */
public class MemoryPromotionService { ... }
```

**Test:** `products/data-cloud/platform-api/src/test/java/com/ghatana/datacloud/memory/promotion/MemoryPromotionServiceTest.java`

- Test: happy path → procedural artifact written, evidence saved.
- Test: eval gates fail → no promotion.
- Test: policy gate blocks → no promotion, denial recorded.
- Test: HITL required → approval event emitted, service awaits.

**Acceptance:** All 7 steps are individually testable and traced. No silent promotion.

---

### P5-T4 — Add Retrieval Quality and Memory Governance APIs

**What:**  
Add APIs to the Data Cloud platform-api that allow AEP and other consumers to query memory quality and governance status.

**Where:** `products/data-cloud/platform-api/src/main/java/com/ghatana/datacloud/memory/`

**How:**

- `MemoryGovernanceService`:
  - `checkRetentionCompliance(String namespaceId)` → `Promise<RetentionComplianceResult>`
  - `redactMemoryArtifact(String artifactId, Set<String> fields)` → `Promise<Void>`
  - `listExpiredArtifacts(String agentId, String tenantId)` → `Promise<List<MemoryArtifactSummary>>`
- `RetrievalQualityService`:
  - `logRetrievalHit(String queryId, boolean hit, double relevanceScore)` → `Promise<Void>`
  - `getRetrievalMetrics(String agentId, Instant from, Instant to)` → `Promise<RetrievalMetrics>`

**Test:** Unit tests with mocks for all service methods. Integration test against Testcontainers PostgreSQL.

**Acceptance:** APIs return typed results. Governance violations are logged and observable.

---

## Phase 6: Agent Observability as First-Class Concern

> **Goal:** Every advanced agent run emits a structured trace graph with spans for all 11 lifecycle phases. Internal `ghatana.agent.*` semantic conventions are defined. OpenTelemetry GenAI conventions are dual-emitted as optional.  
> **Why:** "You cannot govern or improve what you cannot inspect consistently."

### P6-T1 — Define `AgentTelemetryContract` Semantic Layer

**What:**  
Define the internal `ghatana.agent.*` semantic conventions as a set of constants in `platform/java/observability`.

**Where:** `platform/java/observability/src/main/java/com/ghatana/platform/observability/agent/AgentTelemetryContract.java`

**How:**  
Create a constants class (not an enum — values are string attribute names):

```java
/**
 * @doc.type class
 * @doc.purpose Internal semantic convention constants for agent telemetry.
 *             Attribute names follow the ghatana.agent.* namespace.
 *             Version: 1.0 (2026-04-06)
 * @doc.layer platform
 * @doc.pattern Contract
 */
public final class AgentTelemetryContract {
    public static final String VERSION = "1.0";
    public static final String SPAN_PREFIX = "ghatana.agent";

    // Span names
    public static final String SPAN_RUN_START         = "ghatana.agent.run.start";
    public static final String SPAN_CONTEXT_RETRIEVAL = "ghatana.agent.context.retrieval";
    public static final String SPAN_PLANNER_INVOKE    = "ghatana.agent.planner.invoke";
    public static final String SPAN_TOOL_EXECUTE      = "ghatana.agent.tool.execute";
    public static final String SPAN_SUB_AGENT_DELEGATE= "ghatana.agent.delegate";
    public static final String SPAN_POLICY_EVAL       = "ghatana.agent.policy.eval";
    public static final String SPAN_APPROVAL_REQUEST  = "ghatana.agent.approval.request";
    public static final String SPAN_MEMORY_WRITE      = "ghatana.agent.memory.write";
    public static final String SPAN_EVAL_GATE         = "ghatana.agent.eval.gate";
    public static final String SPAN_EXTERNAL_COMMIT   = "ghatana.agent.external.commit";
    public static final String SPAN_RUN_COMPLETE      = "ghatana.agent.run.complete";

    // Attribute keys
    public static final String ATTR_AGENT_ID          = "ghatana.agent.id";
    public static final String ATTR_AGENT_RELEASE_ID  = "ghatana.agent.release_id";
    public static final String ATTR_POLICY_PACK_ID    = "ghatana.agent.policy_pack_id";
    public static final String ATTR_TENANT_ID         = "ghatana.agent.tenant_id";
    public static final String ATTR_CORRELATION_ID    = "ghatana.agent.correlation_id";
    public static final String ATTR_ACTION_CLASS      = "ghatana.agent.action_class";
    public static final String ATTR_TOOL_ID           = "ghatana.agent.tool.id";
    public static final String ATTR_TELEMETRY_VERSION = "ghatana.agent.telemetry.version";
    public static final String ATTR_EXPLANATION_CONTRACT_VERSION = "ghatana.agent.explanation.version";
    public static final String ATTR_REDACTION_PROFILE_ID = "ghatana.agent.redaction_profile_id";
    public static final String ATTR_DATA_ACCESS_DECISION = "ghatana.agent.data_access.decision";

    private AgentTelemetryContract() {} // utility class
}
```

Also add a `TelemetryContractVersion` annotation for tagging version in spans.

**Test:** `platform/java/observability/src/test/java/com/ghatana/platform/observability/agent/AgentTelemetryContractTest.java`

- Verify all constants are non-null and non-empty.
- Verify no duplicate values exist.
- Verify sensitive-content attributes are not defined as raw payload fields.

**Acceptance:** Contract class is immutable, testable, documented, version-tagged.

---

### P6-T2 — Create `AgentRunTracer` in `platform/java/observability`

**What:**  
Add `AgentRunTracer`, a fluent, lifecycle-aware span emitter that any agent runtime can use to instrument the 11 standard phases.

**Where:** `platform/java/observability/src/main/java/com/ghatana/platform/observability/agent/AgentRunTracer.java`

**How:**

```java
/**
 * @doc.type class
 * @doc.purpose Fluent OpenTelemetry span emitter for the 11 standard agent run lifecycle phases.
 * @doc.layer platform
 * @doc.pattern Facade
 */
public class AgentRunTracer {
    private final Tracer tracer;

    public AgentRunTracer(Tracer tracer) { this.tracer = tracer; }

    /** Start a root span for a new agent run. Returns a handle for closing. */
    public AgentRunSpan startRun(String agentId, String agentReleaseId, String tenantId, String correlationId) { ... }

    /** Convenience: records a context retrieval span as child of the run span. */
    public void traceContextRetrieval(AgentRunSpan runSpan, int itemCount, Duration latency) { ... }

    /** Convenience: records a tool execution span. */
    public void traceToolExecution(AgentRunSpan runSpan, ToolExecutionEnvelope envelope, ToolExecutionResult result) { ... }

    /** Convenience: records a policy evaluation span. */
    public void tracePolicyEval(AgentRunSpan runSpan, String policyPackId, String decision) { ... }

    /** Convenience: records a memory write span. */
    public void traceMemoryWrite(AgentRunSpan runSpan, String memoryClass, String namespaceId) { ... }

    // ... similarly for all 11 phases
}

/** Closeable run span handle. */
public interface AgentRunSpan extends AutoCloseable {
    void setStatus(StatusCode status, String description);
    void recordException(Throwable t);
}
```

**Test:** `platform/java/observability/src/test/java/com/ghatana/platform/observability/agent/AgentRunTracerTest.java`

- Use `io.opentelemetry:opentelemetry-sdk-testing` (add to version catalog if not present).
- Test: `startRun` + tool execution creates correct span hierarchy.
- Test: exception recording sets ERROR status.
- Test: all attributes from `AgentTelemetryContract` are set on spans.

**Acceptance:** Traces are hierarchically correct. All 11 phases are coverable.

---

### P6-T3 — Instrument `GovernedAgentDispatcher` with `AgentRunTracer`

**What:**  
Add trace instrumentation to the AEP governed dispatcher using `AgentRunTracer`.

**Where:** `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`

**How:**

- Inject `AgentRunTracer` (or `Tracer`) into `GovernedAgentDispatcher`.
- At dispatch start: `agentRunTracer.startRun(agentId, releaseId, tenantId, correlationId)`.
- Wrap tool execution calls with `traceToolExecution(...)`.
- Wrap policy eval with `tracePolicyEval(...)`.
- Close run span on dispatch completion or exception.

**Test:** Update `RuntimeSafetyTest.java` to assert telemetry spans are emitted using the OTel test SDK.

**Acceptance:** Every dispatched run produces a valid, complete trace. Span hierarchy is correct.

---

### P6-T4 — Define and Register Required Metrics

**What:**  
Add all 11 metrics from the blueprint to a `AgentRuntimeMetrics` class using Micrometer.

**Where:** `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/runtime/AgentRuntimeMetrics.java`

**How:**

```java
/**
 * @doc.type class
 * @doc.purpose Micrometer metric registrations for the AEP agent runtime.
 * @doc.layer product
 * @doc.pattern Metrics
 */
public class AgentRuntimeMetrics {
    // Timer: agent.run.latency
    // Timer: agent.run.time_to_first_action
    // Counter + status tag: agent.tool.calls
    // Gauge: agent.delegation.depth
    // Counter: agent.policy.denials
    // Timer: agent.approval.wait_time
    // Counter: agent.token.usage (by model)
    // Histogram: agent.retrieval.hit_quality (0–1)
    // Counter: agent.memory.promotions
    // Counter: agent.rollbacks
    // Counter: agent.release.regressions
}
```

**Test:** `products/aep/aep-agent-runtime/src/test/java/com/ghatana/agent/runtime/AgentRuntimeMetricsTest.java`

- Use `SimpleMeterRegistry` to assert each metric is registered and updates correctly.

**Acceptance:** All 11 metrics are registered. Prometheus endpoint serves them.

---

### P6-T5 — Instrument `DefaultToolExecutor` with Traces and Metrics

**What:**  
Add tracing and metrics to `DefaultToolExecutor` from Phase 3.

**Where:** `platform/java/tool-runtime/src/main/java/com/ghatana/platform/toolruntime/DefaultToolExecutor.java`

**How:**

- Inject `AgentRunTracer` (or `Tracer`) and `MeterRegistry`.
- On each `execute(...)`: record `SPAN_TOOL_EXECUTE` span with `ATTR_TOOL_ID`, `ATTR_ACTION_CLASS`, `ATTR_TENANT_ID`.
- On completion: increment `agent.tool.calls` counter with status tag.
- On `DENIED`: increment `agent.policy.denials`.

**Test:** Update `DefaultToolExecutorTest.java` to assert spans and metrics are emitted.

**Acceptance:** Every tool call produces a span and updates the right metrics. Denial counter increments on policy deny.

---

## Phase 7: Audio-Video as Domain Capability Provider

> **Goal:** Audio-Video exposes its speech, vision, and multimodal services as first-class agent tools and capability descriptors. Media artifacts integrate with the Data Cloud memory plane. No AEP runtime cloning.  
> **Why:** Cross-product multimodal orchestration requires audio-video capabilities to be discoverable and invocable via standard tool contracts.

### P7-T1 — Define Audio-Video Capability Descriptors

**What:**  
Add capability descriptor YAML files for all Audio-Video service capabilities, to be registered in the agent capability registry.

**Where:** `products/audio-video/libs/common/src/main/resources/capabilities/`

**How:**  
Create one YAML per capability domain:

- `speech-to-text-capability.yaml`
- `text-to-speech-capability.yaml`
- `vision-analysis-capability.yaml`
- `multimodal-inference-capability.yaml`
- `speaker-diarization-capability.yaml`

Each YAML follows `ToolContract` shape:

```yaml
toolId: "audio-video:speech-to-text:v1"
toolVersion: "1.0.0"
name: "Speech to Text"
description: "Transcribes audio input to text using the configured STT model."
actionClass: READ
requiresApproval: false
transport: REMOTE
inputSchema:
  type: object
  properties:
    audioUrl: { type: string, format: uri }
    language: { type: string, default: "en" }
  required: [audioUrl]
outputSchema:
  type: object
  properties:
    transcript: { type: string }
    confidence: { type: number }
```

**Test:** Add a capability schema validation test:
`products/audio-video/integration-tests/src/test/java/AudioVideoCapabilityDescriptorValidationTest.java`

- Load all capability YAMLs and validate against the `ToolContract` schema using `AgentSpecValidator`.

**Acceptance:** All YAMLs are schema-valid. No missing required fields.

---

### P7-T2 — Implement Audio-Video `ToolHandler` Adapters

**What:**  
Implement `ToolHandler` for each Audio-Video capability so that AEP agents can invoke them via `ToolExecutor`.

**Where:** `products/audio-video/libs/common/src/main/java/com/ghatana/audiovideo/tools/`

**How:**

- Create `SpeechToTextToolHandler implements ToolHandler`
- Create `TextToSpeechToolHandler implements ToolHandler`
- Create `VisionAnalysisToolHandler implements ToolHandler`
- Create `MultimodalInferenceToolHandler implements ToolHandler`

Each handler:

- Validates input against its `ToolContract.inputSchema`.
- Calls the corresponding Audio-Video service (e.g., the Rust STT module via JNI bridge or HTTP — use whichever is established).
- Maps the service response to the `ToolContract.outputSchema` shape.
- Returns `ToolExecutionResult` with proper status.

Add `AudioVideoToolHandlerFactory` that registers all handlers with a `ToolExecutor` instance:

```java
/**
 * @doc.type class
 * @doc.purpose Registers all Audio-Video tool handlers with the platform ToolExecutor.
 * @doc.layer product
 * @doc.pattern Factory
 */
public class AudioVideoToolHandlerFactory {
    public void register(ToolExecutor executor) { ... }
}
```

**Test:** `products/audio-video/integration-tests/src/test/java/AudioVideoToolHandlerIntegrationTest.java`

- WireMock the actual service endpoints.
- Test each handler: valid input → `SUCCESS` result.
- Test each handler: service error → `FAILED` result with error detail.

**Acceptance:** All handlers implement `ToolHandler`. Factory registers all of them. Integration tests pass.

---

### P7-T3 — Register Audio-Video Capabilities in the AEP Catalog

**What:**  
Add Audio-Video tool contracts to the AEP agent catalog so they are discoverable by the AEP central runtime.

**Where:** `products/aep/agent-catalog/capabilities/`

**How:**

- Copy or reference the YAML capability descriptors from P7-T1.
- Register them in `agent-catalog.yaml` as external capability providers.
- Configure `McpToolAdapter` or `RemoteToolHandler` for each Audio-Video capability, pointing to the Audio-Video service endpoint (read from config, never hardcoded).

**Test:** Update `products/aep/orchestrator/src/test/java/com/ghatana/aep/integration/registry/CatalogRegistryContractAdapterTest.java` to verify Audio-Video capabilities are discoverable.

**Acceptance:** Audio-Video tools appear in AEP capability discovery. Endpoints are config-driven.

---

### P7-T4 — Add Media Artifact Memory Contract in Data Cloud

**What:**  
Define a `media` memory class and add a `MediaMemoryContract` to handle media artifact retention (audio files, vision embeddings, transcripts) in the Data Cloud memory plane.

**Where:** `products/data-cloud/platform-api/src/main/java/com/ghatana/datacloud/memory/media/`

**How:**

- Add `MediaArtifactRecord`:
  ```java
  public record MediaArtifactRecord(
      String artifactId, String agentId, String tenantId,
      String mediaType,      // AUDIO, VIDEO, IMAGE, TRANSCRIPT, EMBEDDING
      String storageUri,     // opaque URI to underlying storage
      long sizeBytes,
      Instant capturedAt, Instant expiresAt,
      Map<String, String> metadata
  ) {}
  ```
- Add `MediaArtifactRepository` SPI in `agent-core` under `MemoryContract` extension.
- Implement `DataCloudMediaArtifactRepository` with PostgreSQL-backed metadata + pointer to object storage.
- Add Flyway migration for `media_artifacts` table.

**Test:** `products/data-cloud/platform-api/src/test/java/com/ghatana/datacloud/memory/media/DataCloudMediaArtifactRepositoryIT.java`

- Testcontainers PostgreSQL.
- CRUD operations for each media type.

**Acceptance:** Media artifacts are stored with retention metadata. Expired artifacts are queryable.

---

### P7-T5 — Add Optional Domain Orchestration Agents for Audio-Video

**What:**  
Add optional `PLANNING` and `COMPOSITE` type domain agents for common Audio-Video workflows (e.g., `AudioTranscriptionAgent`, `MultimodalAnalysisAgent`).

**Where:** `products/audio-video/modules/intelligence/src/main/java/com/ghatana/audiovideo/agents/`

**How:**

- Implement `AudioTranscriptionAgent extends AbstractTypedAgent<AudioTranscriptionRequest, AudioTranscriptionResult>`:
  - Plans: STT → speaker diarization → transcript post-processing
  - Compiles plan into `PlanGraph`, routes to AEP orchestrator via `PlanCompiler`.
- Implement `MultimodalAnalysisAgent extends AbstractTypedAgent<MultimodalInput, AnalysisResult>`:
  - Plans: vision analysis → context retrieval → synthesis
  - Uses `COMPOSITE` type with `AgentType.COMPOSITE`.
- Each agent has a proper `AgentDescriptor` with all required fields.
- Register agents via `AgentLogicProvider` SPI.

**Test:**

- `AudioTranscriptionAgentTest.java` — unit test with mocked tool handlers.
- `MultimodalAnalysisAgentTest.java` — unit test with mocked tool handlers.
- Both extend `EventloopTestBase`.

**Acceptance:** Domain agents produce valid `PlanGraph` objects. Plans compile via `DefaultPlanCompiler`. Agents have correct `AgentType`.

---

---

## Phase 8: Agent Pluggability and Inter-Agent Protocol

> **Goal:** Agents become dynamically loadable, hot-swappable, signed artifacts with explicit contracts covering every interaction mode (request/response, streaming, events), composition pattern, supervision hierarchy, handoff, repetition governance, self-learning signal routing, and scoped context sharing. These contracts are enforced at runtime, not just documented.  
> **Why:** Without concrete, machine-checked contracts for what each agent is capable of, the system cannot safely route, supervise, compose, or replace agents at production scale. The `KernelPluginRuntimeManager` already provides JAR-level hot-reload; this phase adds the agent-specific semantic layer on top of it.  
> **Blueprint references:** §19 (Agent Pluggability), §20 (Inter-Agent Protocol), §6.6 (platform-kernel packaging), §8 (runtime model)

**Dependencies:** Phase 1 (canonical enums), Phase 2 (AgentRelease + signing reference), Phase 3 (ToolContract + ActionClass). Tasks P8-T1 and P8-T2 may start after Phase 1. All others require Phase 2 and Phase 3.

---

### P8-T1 — Define `AgentCapabilityManifest` Model in `agent-core`

**What:**  
Create `AgentCapabilityManifest` as the machine-readable, version-stable runtime declaration of everything an agent participates in. This is separate from `AgentDescriptor` (identity/type/SLA) and `AgentSpec` (YAML spec). It is the **pluggability contract** read by the runtime before any interaction.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/pluggability/AgentCapabilityManifest.java`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Machine-readable runtime capability declaration for an agent artifact.
 *             Read by the runtime before any interaction is initiated.
 *             Lives inside the AgentPackage as META-INF/agent-manifest.yaml.
 * @doc.layer platform
 * @doc.pattern Contract
 */
public record AgentCapabilityManifest(
    String agentId,
    String agentVersion,
    String agentReleaseId,            // references AgentRelease from Phase 2
    String mainClass,                 // FQCN of the TypedAgent implementation

    // Interaction
    Set<InteractionMode> interactionModes,

    // Supervision
    Set<SupervisionRole> supervisionRoles,
    SupervisionStrategy defaultSupervisionStrategy,
    int maxRestarts,
    Duration restartWindow,

    // Composition
    Set<CompositionRole> compositionRoles,

    // Handoff
    HandoffCapability handoffCapability,
    Set<HandoffReason> supportedHandoffReasons,

    // Repetition
    RepetitionPolicy repetitionPolicy,

    // Self-learning
    LearningLevel learningLevel,      // matches LearningEngine.LearningLevel (L0–L5)
    boolean emitsLearningSignals,
    boolean receivesCrossAgentSignals,

    // Context sharing
    ContextSharingScope contextSharingScope,
    Set<String> sharedContextKeys,    // keys this agent publishes to SharedContext

    // Declared tools
    Set<String> declaredToolContractIds,

    // Manifest version
    String manifestVersion            // "1.0" — for forward-compat
) {}

public enum InteractionMode {
    REQUEST_RESPONSE, ASYNC_REQUEST, STREAMING, EVENT_DRIVEN, BROADCAST
}

public enum SupervisionRole {
    SUPERVISOR,     // oversees and manages supervisees
    SUPERVISEE,     // managed by a supervisor
    PEER_WATCHDOG   // monitors peers without coordinating their work
}

public enum HandoffCapability {
    NONE,           // does not participate in handoffs
    SEND_ONLY,      // can initiate handoffs but cannot receive
    RECEIVE_ONLY,   // can receive handoffs but cannot initiate
    BIDIRECTIONAL   // full handoff participation
}
```

Also create the supporting enums in the `pluggability` package:

- `InteractionMode.java`
- `SupervisionRole.java`
- `HandoffCapability.java`

And create a `AgentCapabilityManifestValidator`:

```java
/**
 * @doc.type class
 * @doc.purpose Validates an AgentCapabilityManifest for completeness and consistency.
 * @doc.layer platform
 * @doc.pattern Validator
 */
public class AgentCapabilityManifestValidator {
    public ValidationResult validate(AgentCapabilityManifest manifest) { ... }
}
```

Rules:

- `agentId`, `agentVersion`, `agentReleaseId`, `mainClass` must be non-null and non-empty.
- `interactionModes` must be non-empty.
- If `handoffCapability == BIDIRECTIONAL`, `supportedHandoffReasons` must not be empty.
- If `emitsLearningSignals == true`, `learningLevel` must be L1 or higher.
- `contextSharingScope != NONE` requires `sharedContextKeys` to be non-empty.

**Files (all new in `platform/java/agent-core/src/main/java/com/ghatana/agent/pluggability/`):**

- `AgentCapabilityManifest.java`
- `InteractionMode.java`
- `SupervisionRole.java`
- `HandoffCapability.java`
- `AgentCapabilityManifestValidator.java`

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/pluggability/AgentCapabilityManifestTest.java`

- JSON round-trip for all fields.
- `AgentCapabilityManifestValidatorTest.java`: valid manifest passes; each invalid condition produces a named `ValidationIssue`.

**Acceptance:** Record compiles, serializes, validator enforces all rules. All four `@doc.*` tags present.

---

### P8-T2 — Define `AgentPackage` and Packaging Format

**What:**  
Create `AgentPackage` as the atomic deployable artifact record, and define the standard JAR manifest extension entries an agent package must have.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/pluggability/AgentPackage.java`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Atomic deployable and swappable agent artifact.
 *             Contains the JAR reference, capability manifest, signing attestation,
 *             and release linkage.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentPackage(
    String packageId,                  // UUID
    String agentId,
    String packageVersion,             // semver, matches AgentRelease.releaseVersion
    String agentReleaseId,             // references AgentRelease (Phase 2)
    AgentCapabilityManifest manifest,  // loaded from META-INF/agent-manifest.yaml inside the JAR
    String packageDigest,              // SHA-256 of the JAR bytes
    String signingReference,           // Sigstore bundle reference (matches AgentRelease.signingReference)
    List<String> compatibleRuntimeVersions, // e.g., ["aep-runtime:2.x"]
    AgentPackageSource source,         // LOCAL_JAR, REMOTE_URI, OCI_IMAGE
    String sourceUri,                  // path or URI to the artifact
    Instant packagedAt
) {}

public enum AgentPackageSource { LOCAL_JAR, REMOTE_URI, OCI_IMAGE }
```

**Standard `META-INF/MANIFEST.MF` entries:**

```
Agent-Id: fraud-detector
Agent-Version: 2.1.0
Agent-Release-Id: rel-uuid-here
Agent-Main-Class: com.example.FraudDetectorAgent
Agent-Manifest-Path: META-INF/agent-manifest.yaml
Agent-Runtime-Compatibility: aep-runtime:2.x
```

**Standard `META-INF/agent-manifest.yaml`**: see blueprint §19.2 for the full YAML format.

Add `AgentPackageBuilder` — a fluent builder that:

1. Takes a `Path` to a JAR
2. Reads `MANIFEST.MF` entries
3. Reads `agent-manifest.yaml` from the JAR
4. Builds the `AgentPackage` record

**Files:**

- `AgentPackage.java`
- `AgentPackageSource.java`
- `AgentPackageBuilder.java`

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/pluggability/AgentPackageBuilderTest.java`

- Build from a test JAR in test resources.
- Verify manifest is parsed correctly.
- Test: malformed JAR → `AgentPackageLoadException`.

**Acceptance:** `AgentPackageBuilder` builds a complete record from a JAR. Digest is correct. Manifest validates.

---

### P8-T3 — Implement `AgentPackageLoader` in AEP

**What:**  
Implement the agent-specific loading facade over `KernelPluginRuntimeManager`. This is the entry point for dynamically loading, unloading, and hot-reloading agent packages into the AEP runtime.

**Where:** `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/runtime/pluggability/AgentPackageLoader.java`

**How:**

```java
/**
 * @doc.type class
 * @doc.purpose Agent-specific facade over KernelPluginRuntimeManager for dynamic
 *             agent loading, unloading, and hot-reload with capability validation.
 * @doc.layer product
 * @doc.pattern Facade, Strategy
 */
public class AgentPackageLoader {

    private final KernelPluginRuntimeManager pluginRuntime;
    private final AgentReleaseRepository releaseRepository;    // Phase 2
    private final AgentCapabilityManifestValidator manifestValidator;
    private final AgentRegistry agentRegistry;
    private final KernelRegistry kernelRegistry;

    /** Load an AgentPackage into the runtime. */
    public Promise<LoadedAgentEntry> load(AgentPackage pkg) {
        // 1. Validate manifest
        // 2. Verify signing reference against Sigstore (or stub)
        // 3. Check release state is VALIDATED, SHADOW, CANARY, or ACTIVE in releaseRepository
        // 4. Delegate to pluginRuntime.loadPlugin(jarPath)
        // 5. Register AgentCapabilityManifest in kernelRegistry under "agent:{agentId}"
        // 6. Register TypedAgent instance in agentRegistry
        // 7. Emit AGENT_PACKAGE_LOADED structured log event
    }

    /** Unload a loaded agent package. */
    public Promise<Void> unload(String agentId, AgentReleaseState targetState) {
        // 1. Transition release state to targetState (DEPRECATED or RETIRED)
        // 2. pluginRuntime.unloadPlugin(pluginId)
        // 3. Deregister from kernelRegistry and agentRegistry
        // 4. Emit AGENT_PACKAGE_UNLOADED event
    }

    /** Hot-reload — unload then load new version. */
    public Promise<LoadedAgentEntry> reload(AgentPackage newPkg) { ... }
}

public record LoadedAgentEntry(
    String agentId, String packageId, String releaseId,
    AgentCapabilityManifest manifest, Instant loadedAt
) {}
```

**Test:** `products/aep/aep-agent-runtime/src/test/java/com/ghatana/agent/runtime/pluggability/AgentPackageLoaderTest.java`

- Extend `EventloopTestBase`.
- Test: valid package with VALIDATED release → loaded, registered in registry.
- Test: package with BLOCKED release → load rejected.
- Test: manifest validation failure → `AgentPackageLoadException`.
- Test: reload → old version unloaded, new version loaded, registry updated.

**Acceptance:** Loader is async-safe (all ops return `Promise<>`). Release state gate is enforced. Registry entries are correct after load/unload.

---

### P8-T4 — Implement `AgentSwapCoordinator` for Zero-Downtime Hot-Swap

**What:**  
Implement the four-phase hot-swap protocol (Load New → Drain In-Flight → Handoff → Cut-Over) from blueprint §19.4.

**Where:** `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/runtime/pluggability/AgentSwapCoordinator.java`

**How:**

```java
/**
 * @doc.type class
 * @doc.purpose Orchestrates zero-downtime hot-swap of an agent version
 *             using a four-phase load/drain/handoff/cut-over protocol.
 *             Swap state is persisted in Data Cloud for resumability.
 * @doc.layer product
 * @doc.pattern Saga, Coordinator
 */
public class AgentSwapCoordinator {

    /** Phase 1: load v2 alongside v1 without activating it. */
    public Promise<SwapHandle> initiateSwap(AgentPackage newPackage);

    /** Phase 2: stop routing new requests to old version; wait for drain. */
    public Promise<Void> drainOldVersion(SwapHandle handle, Duration drainTimeout);

    /** Phase 3: issue VERSION_UPGRADE handoff from old to new. */
    public Promise<Void> executeHandoff(SwapHandle handle);

    /** Phase 4: activate new version, deactivate old, release resources. */
    public Promise<LoadedAgentEntry> cutOver(SwapHandle handle);

    /** Full sequence (convenience method — orchestrates all four phases). */
    public Promise<LoadedAgentEntry> performSwap(AgentPackage newPackage, SwapOptions options);

    /** Cancel an in-progress swap and roll back to the original version. */
    public Promise<Void> cancelSwap(SwapHandle handle);
}

public record SwapHandle(
    String swapId, String agentId,
    String fromReleaseId, String toReleaseId,
    SwapPhase currentPhase, Instant startedAt
) {}

public enum SwapPhase { INITIATED, DRAINING, HANDING_OFF, CUTTING_OVER, COMPLETE, CANCELLED }

public record SwapOptions(
    Duration drainTimeout,        // default: 30 seconds
    boolean requireAck,           // whether target must ACK handoff before cut-over
    boolean autoRollbackOnFailure // default: true
) {}
```

**Persistence:** Persist `SwapHandle` state in Data Cloud (add `agent_swap_operations` table via Flyway migration in Phase 5 work area) so that swaps survive coordinator restart.

**Test:** `products/aep/aep-agent-runtime/src/test/java/com/ghatana/agent/runtime/pluggability/AgentSwapCoordinatorTest.java`

- Extend `EventloopTestBase`.
- Test: happy path — all four phases complete, new version active.
- Test: drain timeout exceeded → rollback to old version.
- Test: handoff rejected by target → auto-rollback.
- Test: cancelSwap during DRAINING → old version remains active.
- Test: coordinator crash during HANDING_OFF phase is resumable from persisted state.

**Acceptance:** Swap is safe, resumable, and produces correct telemetry spans for all four phases.

---

### P8-T5 — Define Inter-Agent Message Envelope and `AgentInteractionProtocol`

**What:**  
Define the uniform interaction entry point and message envelope for all agent-to-agent communication modes: `REQUEST_RESPONSE`, `ASYNC_REQUEST`, `STREAMING`, `EVENT_DRIVEN`, `BROADCAST`.

**Where:** `platform/java/agent-core/src/main/java/com/ghatana/agent/interaction/`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Standard message envelope for all inter-agent communication.
 *             All modes carry a correlationId and a TTL for expiry enforcement.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentMessage(
    String messageId,
    String correlationId,
    String sessionId,
    String sourceAgentId,
    String targetAgentId,         // null for BROADCAST
    InteractionMode mode,
    Object payload,
    Map<String, String> headers,
    Instant sentAt,
    Duration ttl
) {}

/**
 * @doc.type record
 * @doc.purpose Typed event for EVENT_DRIVEN and BROADCAST interaction modes.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentEvent(
    String eventId,
    String correlationId,
    String sourceAgentId,
    String eventType,
    Object payload,
    Set<String> targetAgentIds,   // empty = broadcast to all subscribers
    Instant occurredAt
) {}

/**
 * @doc.type interface
 * @doc.purpose Uniform entry point for all inter-agent interaction modes.
 *             Any agent exposed via the runtime implements or is wrapped by this interface.
 * @doc.layer platform
 * @doc.pattern Facade
 */
public interface AgentInteractionProtocol {

    /** Request-response (SYNC or ASYNC depending on AgentMessage.mode). */
    Promise<AgentResponse> request(AgentMessage message);

    /** Streaming — returns a lazy producer of partial results. */
    Promise<AsyncStreamProducer<Object>> stream(AgentMessage message);

    /** Publish an event (EVENT_DRIVEN or BROADCAST modes). */
    Promise<Void> publish(AgentEvent event);

    /** Subscribe to events of given types. */
    void subscribe(String eventType, AgentEventHandler handler);

    /** Returns the interaction modes this agent currently supports. */
    Set<InteractionMode> supportedModes();
}

public record AgentResponse(
    String messageId,
    String correlationId,
    String respondingAgentId,
    AgentResponseStatus status,
    Object result,
    String errorMessage,
    Instant respondedAt
) {}

public enum AgentResponseStatus { SUCCESS, FAILED, DENIED, TIMEOUT, UNSUPPORTED_MODE }

@FunctionalInterface
public interface AgentEventHandler {
    Promise<Void> handle(AgentEvent event);
}
```

**Files (all new in `platform/java/agent-core/src/main/java/com/ghatana/agent/interaction/`):**

- `AgentMessage.java`
- `AgentEvent.java`
- `AgentResponse.java`
- `AgentResponseStatus.java`
- `AgentInteractionProtocol.java`
- `AgentEventHandler.java`
- `DefaultAgentInteractionProtocol.java` — wraps an existing `TypedAgent<I,O>` to expose all modes

**`DefaultAgentInteractionProtocol` behaviour:**

- `REQUEST_RESPONSE`: delegates to `TypedAgent.process(...)`.
- `ASYNC_REQUEST`: delegates to `TypedAgent.process(...)`, returns correlation ID immediately, delivers result via registered callback.
- `STREAMING`: not supported by default — return `UNSUPPORTED_MODE` unless overridden.
- `EVENT_DRIVEN`: routes to registered `AgentEventHandler`s.
- Validates that the requested mode is declared in `AgentCapabilityManifest.interactionModes` before delegating.

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/interaction/DefaultAgentInteractionProtocolTest.java`

- Extend `EventloopTestBase`.
- Test: mode not in manifest → `UNSUPPORTED_MODE` response.
- Test: `REQUEST_RESPONSE` → result returned.
- Test: `EVENT_DRIVEN` → registered handler called.
- Test: TTL expired message → rejected with `TIMEOUT`.

**Acceptance:** All interaction modes produce the correct `AgentResponse`. Manifest mode gate is enforced.

---

### P8-T6 — Define and Register `SupervisionContract`

**What:**  
Define the `SupervisionContract` model, `SupervisionRegistry`, and wire it into `GovernedAgentDispatcher` so that agent failures trigger the registered supervision strategy.

**Where:**

- Models: `platform/java/agent-core/src/main/java/com/ghatana/agent/supervision/`
- Registry: `platform/java/agent-core/src/main/java/com/ghatana/agent/supervision/SupervisionRegistry.java`
- Runtime integration: `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Defines the failure-management relationship between a supervisor
 *             agent and one or more supervisees.
 * @doc.layer platform
 * @doc.pattern Contract
 */
public record SupervisionContract(
    String contractId,
    String supervisorAgentId,
    Set<String> superviseeAgentIds,
    SupervisionStrategy strategy,
    int maxRestarts,
    Duration restartWindow,
    Set<String> supervisedFailureClasses,  // FQCN exception class names
    boolean escalateUnhandledFailures,
    String escalationTargetAgentId         // null → escalate to platform alert
) {}

public enum SupervisionStrategy {
    RESTART, RESTART_TASK, ESCALATE, ISOLATE, LOG_AND_CONTINUE, SHUTDOWN_ALL
}

/**
 * @doc.type interface
 * @doc.purpose Registry for supervision contracts. Queried by the governed dispatcher on failure.
 * @doc.layer platform
 * @doc.pattern Registry
 */
public interface SupervisionRegistry {
    void register(SupervisionContract contract);
    Optional<SupervisionContract> findContractForSupervisee(String superviseeAgentId);
    List<SupervisionContract> findContractsBySupervisor(String supervisorAgentId);
    void deregister(String contractId);
}
```

**Update `GovernedAgentDispatcher`:**

- Inject `SupervisionRegistry`.
- On any exception from `TypedAgent.process(...)`: call `supervisionRegistry.findContractForSupervisee(agentId)`.
- Apply strategy:
  - `RESTART`: re-invoke `AgentPackageLoader.reload(...)` then retry the request.
  - `RESTART_TASK`: retry the request with the same agent instance (without full reload).
  - `ESCALATE`: emit the failure as an `AgentEvent` to `escalationTargetAgentId` or the platform alert channel.
  - `ISOLATE`: call `AgentPackageLoader.unload(agentId, DEPRECATED)`, deny future requests.
  - `LOG_AND_CONTINUE`: log structured error, return `AgentResult.failed(...)`.
  - `SHUTDOWN_ALL`: unload all supervisees of this supervisor.
- Track restart counts per `restartWindow` using an in-memory sliding counter.
- Emit `ghatana.agent.supervision.decision` telemetry span for every strategy application.

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/supervision/SupervisionRegistryTest.java`  
And: `products/aep/aep-agent-runtime/src/test/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcherSupervisionTest.java`

- Test: each strategy applied on exception.
- Test: max restarts exceeded → strategy switches to `ESCALATE`.
- Test: `escalationTargetAgentId == null` → platform alert emitted.
- All tests extend `EventloopTestBase`.

**Acceptance:** All 6 strategies behave correctly in tests. Restart limit enforced. Telemetry emitted.

---

### P8-T7 — Define `CompositionPolicy` and Extend Orchestration

**What:**  
Extend the existing orchestration layer to support all composition patterns from blueprint §20.3, replacing the implicit strategy with an explicit, persisted `CompositionPolicy` record.

**Where:**

- Models: `platform/java/agent-core/src/main/java/com/ghatana/agent/composition/`
- Extend existing orchestration strategies in `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/coordination/`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Explicit, versioned composition policy governing how agents collaborate.
 * @doc.layer platform
 * @doc.pattern Contract
 */
public record CompositionPolicy(
    String compositionId,
    CompositionPattern pattern,
    List<String> memberAgentIds,
    Map<String, CompositionRole> roles,
    VotingPolicy votingPolicy,
    AggregationStrategy aggregation,
    boolean failFast,
    Duration compositionTimeout,
    int maxConcurrentMembers
) {}

public enum CompositionPattern {
    SEQUENTIAL, PARALLEL, FAN_OUT_FAN_IN, VOTING, PIPELINE, SCATTER_GATHER
}

public enum CompositionRole {
    LEADER, FOLLOWER, PEER, SEQUENCED, VOTER, AGGREGATOR
}

public enum VotingPolicy {
    MAJORITY, UNANIMOUS, WEIGHTED, FIRST_EXCEEDING_THRESHOLD
}

public enum AggregationStrategy {
    TAKE_ALL, TAKE_FIRST, MERGE, REDUCE_BY_CONFIDENCE, CUSTOM
}
```

Add concrete `OrchestrationStrategy` implementations for the two patterns not yet present:

- `VotingOrchestration` — implements `VOTING` pattern; applies `VotingPolicy` to results
- `ScatterGatherOrchestration` — implements `SCATTER_GATHER`; shards input, gathers partial results, applies `AggregationStrategy`

Update the existing `SequentialOrchestration`, `ParallelOrchestration`, and `HierarchicalOrchestration` to accept and enforce `CompositionPolicy` instead of implicit strategy flags.

Add `CompositeOrchestrator` — the single entry point that reads a `CompositionPolicy`, selects the right `OrchestrationStrategy`, and executes it:

```java
/**
 * @doc.type class
 * @doc.purpose Single entry point for composing agents using a CompositionPolicy.
 *             Validates that each member agent's manifest declares the required CompositionRole.
 * @doc.layer platform
 * @doc.pattern Facade
 */
public class CompositeOrchestrator {
    public <I, O> Promise<List<O>> execute(CompositionPolicy policy, I input, AgentContext ctx);
}
```

Before executing, `CompositeOrchestrator` must verify that each member agent's `AgentCapabilityManifest.compositionRoles` contains the required role from `policy.roles`. If not: fail fast with `CompositionContractViolationException`.

**Files (new in `platform/java/agent-core/src/main/java/com/ghatana/agent/composition/`):**

- `CompositionPolicy.java`, `CompositionPattern.java`, `CompositionRole.java`
- `VotingPolicy.java`, `AggregationStrategy.java`
- `CompositeOrchestrator.java`, `CompositionContractViolationException.java`

**New orchestration strategies:**

- `VotingOrchestration.java`
- `ScatterGatherOrchestration.java`

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/composition/CompositeOrchestratorTest.java`

- Extend `EventloopTestBase`.
- Test each of the 6 patterns with 3 mock agents.
- Test: member agent missing required role → `CompositionContractViolationException`.
- Test: `failFast=true` with one agent failing → composition aborts.
- Test: `VOTING` with `MAJORITY` → correct winner selected.
- Test: `SCATTER_GATHER` with `MERGE` aggregation → results correctly merged.

**Acceptance:** All 6 patterns produce correct results. Role contract is machine-enforced.

---

### P8-T8 — Define `AgentHandoff` and `HandoffCoordinator`

**What:**  
Implement the full handoff protocol from blueprint §20.4, covering task-and-context transfer between agents with optional acknowledgement and trace continuity.

**Where:**

- Models: `platform/java/agent-core/src/main/java/com/ghatana/agent/handoff/`
- Coordinator: `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/runtime/handoff/HandoffCoordinator.java`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Structured transfer of a task and context from one agent to another.
 *             Governed as a WRITE_IRREVERSIBLE action (requires policy approval for high-risk handoffs).
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentHandoff(
    String handoffId,
    String correlationId,
    String sourceAgentId,
    String sourceReleaseId,
    String targetAgentId,
    String targetReleaseId,
    HandoffReason reason,
    AgentContextSnapshot contextSnapshot,
    Object taskState,
    Set<String> requiredTargetCapabilities,  // target must declare these in its manifest
    HandoffAcknowledgement acknowledgement,
    Instant initiatedAt,
    Duration handoffTimeout
) {}

public enum HandoffReason {
    COMPLETION, DELEGATION, FAILURE_RECOVERY, SPECIALIZATION, LOAD_BALANCE, VERSION_UPGRADE
}

public enum HandoffAcknowledgement { NONE, REQUIRED }

/**
 * @doc.type record
 * @doc.purpose Snapshot of an agent's context and state at the moment of handoff.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentContextSnapshot(
    String snapshotId,
    String sourceAgentId,
    String sessionId,
    String correlationId,
    Map<String, Object> workingMemory,
    Map<String, Object> sharedContextRefs,
    List<String> episodicMemoryIds,
    Map<String, Object> planState,
    Instant capturedAt
) {}
```

**`HandoffCoordinator` steps:**

1. Verify target agent's manifest declares `RECEIVE_ONLY` or `BIDIRECTIONAL` handoff capability.
2. Verify target declares all `requiredTargetCapabilities`.
3. Classify handoff as `WRITE_IRREVERSIBLE` action → run through `ToolExecutor` policy gate (Phase 3).
4. Persist `AgentHandoff` record in Data Cloud (add `agent_handoffs` table via Flyway in Phase 5 area).
5. Snapshot context via `AgentContextSnapshot` and persist.
6. Notify target via `AgentInteractionProtocol.publish(handoffEvent)`.
7. If `acknowledgement == REQUIRED`: wait for ACK `AgentEvent` from target (with `handoffTimeout`).
8. Transfer `SharedContext` ownership (call `SharedContextRepository.transferOwnership(...)`).
9. Deactivate source agent's task lease.
10. Emit `ghatana.agent.handoff.complete` telemetry span with both agent IDs, reason, duration.

**Test:** `products/aep/aep-agent-runtime/src/test/java/com/ghatana/agent/runtime/handoff/HandoffCoordinatorTest.java`

- Extend `EventloopTestBase`.
- Test: target without RECEIVE capability → rejected.
- Test: missing required capability → rejected.
- Test: policy gate deny (high-risk) → rejected.
- Test: REQUIRED acknowledgement timeout → rollback (source re-activates).
- Test: VERSION_UPGRADE reason → shared context ownership transferred.
- Test: trace span emitted with correct attributes.

**Acceptance:** All 10 handoff steps are individually observable in telemetry. All rejection paths produce typed errors with actionable messages.

---

### P8-T9 — Define `RepetitionPolicy` and `RepetitionGovernor`

**What:**  
Implement loop, retry, and recursion governance so that agents cannot iterate indefinitely or recurse unboundedly.

**Where:**

- Model: `platform/java/agent-core/src/main/java/com/ghatana/agent/repetition/`
- Governor: `platform/java/tool-runtime/src/main/java/com/ghatana/platform/toolruntime/RepetitionGovernor.java`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Governs controlled iteration, retry, and recursion within an agent's execution.
 *             Declared in AgentCapabilityManifest and enforced by RepetitionGovernor.
 * @doc.layer platform
 * @doc.pattern Contract
 */
public record RepetitionPolicy(
    int maxIterations,            // 0 = unlimited (requires EXPLICIT_STOP termination)
    int maxRecursionDepth,        // hard cap on self-delegation depth
    int maxRetries,               // max retries before escalating
    Duration retryBackoffBase,
    RetryStrategy retryStrategy,
    Set<ActionClass> retryableActionClasses,
    TerminationCondition terminationCondition,
    double convergenceThreshold   // for CONVERGENCE: stop when |result[n] - result[n-1]| < threshold
) {
    public static RepetitionPolicy defaultSafe() {
        return new RepetitionPolicy(10, 5, 3,
            Duration.ofSeconds(1), RetryStrategy.EXPONENTIAL_BACKOFF,
            Set.of(ActionClass.READ, ActionClass.DRAFT),
            TerminationCondition.MAX_ITERATIONS, 0.0);
    }
}

public enum RetryStrategy {
    IMMEDIATE, LINEAR_BACKOFF, EXPONENTIAL_BACKOFF, CIRCUIT_BREAK
}

public enum TerminationCondition {
    MAX_ITERATIONS, CONVERGENCE, EXPLICIT_STOP, TIMEOUT, POLICY_GATE
}
```

**`RepetitionGovernor`** tracks per-`correlationId` execution state:

- Iteration counter (incremented by the agent's caller before each loop iteration)
- Recursion depth counter (incremented when an agent delegates to itself)
- Retry counter (incremented on transient failure)

```java
/**
 * @doc.type class
 * @doc.purpose Tracks and enforces RepetitionPolicy for agent executions.
 *             Keyed by correlationId. State is in-memory (non-durable).
 * @doc.layer platform
 * @doc.pattern Service
 */
public class RepetitionGovernor {
    public void recordIteration(String correlationId, RepetitionPolicy policy);
    public void recordRecursion(String correlationId, RepetitionPolicy policy);
    public void recordRetry(String correlationId, RepetitionPolicy policy);
    public void recordExplicitStop(String correlationId);
    public void clearState(String correlationId);      // call on run completion
}
```

On violation:

- `maxIterations` exceeded: throw `RepetitionLimitExceededException`, trigger `ESCALATE` in supervision.
- `maxRecursionDepth` exceeded: throw `RecursionDepthExceededException`, trigger `ISOLATE` in supervision.
- `maxRetries` exceeded with `CIRCUIT_BREAK`: set circuit open, emit `ghatana.agent.circuit.open` metric.

**Files (new in `platform/java/agent-core/src/main/java/com/ghatana/agent/repetition/`):**

- `RepetitionPolicy.java`, `RetryStrategy.java`, `TerminationCondition.java`
- `RepetitionLimitExceededException.java`, `RecursionDepthExceededException.java`

**New in `platform/java/tool-runtime/`:**

- `RepetitionGovernor.java`

**Test:** `platform/java/tool-runtime/src/test/java/com/ghatana/platform/toolruntime/RepetitionGovernorTest.java`

- Test: max iterations → exception.
- Test: max recursion → exception.
- Test: exponential backoff timing (mock clock).
- Test: `CIRCUIT_BREAK` after maxRetries → circuit open metric emitted.
- Test: `clearState` resets all counters.

**Acceptance:** All three counters enforce their limits independently. Exceptions are typed. Governor is thread-safe.

---

### P8-T10 — Define `LearningSignal` SPI and `LearningSignalRouter`

**What:**  
Define the structured cross-agent and cross-session learning signal contract, and implement the router that feeds signals into the target agent's `LearningEngine` reflect cycle.

**Where:**

- Models + SPI: `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/signal/`
- Router: `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/signal/LearningSignalRouter.java`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Typed, structured feedback signal for agent self-improvement.
 *             Consumed by LearningEngine during the next reflect cycle.
 *             Cross-agent signals are governance-checked before routing.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record LearningSignal(
    String signalId,
    String correlationId,
    String emittingAgentId,
    String targetAgentId,
    String agentReleaseId,
    LearningSignalType type,
    Object observation,
    Object expectedOutcome,
    Object actualOutcome,
    double confidence,
    Map<String, Object> features,
    LearningSignalSource source,
    Instant emittedAt
) {}

public enum LearningSignalType {
    POSITIVE_REINFORCEMENT, NEGATIVE_REINFORCEMENT, CORRECTION,
    OBSERVATION, PREFERENCE, FAILURE_SIGNAL, SUCCESS_SIGNAL
}

public enum LearningSignalSource {
    AGENT_SELF, PEER_AGENT, HUMAN_FEEDBACK, AUTOMATED_EVAL, PLATFORM_MONITOR
}

/**
 * @doc.type interface
 * @doc.purpose Routes LearningSignals to the appropriate LearningEngine instance.
 *             Validates governance rules before routing.
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public interface LearningSignalRouter {
    /**
     * Route a signal to the target agent's learning pipeline.
     * Validates: target's learningLevel >= L1, source has permission to signal target,
     *            PolicyPack allows cross-agent signals.
     */
    Promise<Void> route(LearningSignal signal);

    /** Emit a learning signal directly from an agent's own reflect phase. */
    Promise<Void> emitSelf(LearningSignal signal);
}
```

**`DefaultLearningSignalRouter` behaviour:**

1. Validate: target agent manifest has `emitsLearningSignals=false` for external signals → reject.
2. Validate: target `learningLevel >= L1` — L0 agents do not learn.
3. If `source == PEER_AGENT`: verify emitter was in a `CompositionPolicy` with target or supervised target.
4. If `source == HUMAN_FEEDBACK`: fast-path to `PromotionEvidence` (Phase 5-T3).
5. Buffer validated signals in an in-memory queue keyed by `targetAgentId`.
6. When `LearningEngine.reflect()` is called for the target, drain the queue and inject signals as additional episodes.
7. Emit `ghatana.agent.learning.signal_received` log with `signalType` and `source`.

**Files (all new in `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/signal/`):**

- `LearningSignal.java`
- `LearningSignalType.java`
- `LearningSignalSource.java`
- `LearningSignalRouter.java`
- `DefaultLearningSignalRouter.java`

**Test:** `platform/java/agent-core/src/test/java/com/ghatana/agent/learning/signal/DefaultLearningSignalRouterTest.java`

- Extend `EventloopTestBase`.
- Test: L0 target → signal rejected.
- Test: peer signal without composition relationship → rejected.
- Test: valid signal → buffered, drains into LearningEngine.
- Test: `HUMAN_FEEDBACK` → goes to `PromotionEvidence` (mock the Phase 5 service).
- Test: telemetry log emitted.

**Acceptance:** All governance rules enforced before routing. L0 protection is absolute.

---

### P8-T11 — Define `SharedContext` and `SharedContextRepository`

**What:**  
Implement scoped, explicitly-owned, multi-agent readable context storage that extends `AgentContext` with cross-agent read-through.

**Where:**

- Models + SPI: `platform/java/agent-core/src/main/java/com/ghatana/agent/context/`
- Data Cloud implementation: `products/data-cloud/platform-api/src/main/java/com/ghatana/datacloud/context/DataCloudSharedContextRepository.java`

**How:**

```java
/**
 * @doc.type record
 * @doc.purpose Explicitly-owned, scoped context shared across agents within a defined boundary.
 *             The ownerAgentId has write authority; other agents have read access by scope.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record SharedContext(
    String contextId,
    String ownerAgentId,
    String sessionId,
    String tenantId,
    ContextSharingScope scope,
    Map<String, SharedContextEntry> entries,
    Set<String> authorizedAgentIds,
    Instant createdAt,
    Instant expiresAt,
    boolean immutable
) {}

public record SharedContextEntry(
    String key,
    Object value,
    String writtenByAgentId,
    Instant writtenAt,
    MergeStrategy mergeStrategy
) {}

public enum ContextSharingScope {
    NONE, WITHIN_SESSION, WITHIN_TENANT, WITHIN_COMPOSITION, GLOBAL
}

public enum MergeStrategy {
    LAST_WRITER_WINS, FIRST_WRITER_WINS, OWNER_ONLY, APPEND_LIST, MERGE_MAP
}

/**
 * @doc.type interface
 * @doc.purpose SPI for shared context persistence and access.
 * @doc.layer platform
 * @doc.pattern Repository
 */
public interface SharedContextRepository {
    Promise<SharedContext> publish(SharedContext context);
    Promise<Optional<SharedContext>> findById(String contextId);
    Promise<List<SharedContext>> findBySession(String sessionId, ContextSharingScope scope);
    Promise<SharedContextEntry> writeEntry(String contextId, SharedContextEntry entry, String writingAgentId);
    Promise<Void> revoke(String contextId, String requestingAgentId);
    Promise<Void> transferOwnership(String contextId, String newOwnerAgentId);
    Promise<Void> expireSessionContexts(String sessionId);
}
```

**Update `AgentContext` (or `DefaultAgentContext`):**

- Add `readSharedContext(String contextId) → Promise<Optional<SharedContext>>`
- Add `writeSharedContextEntry(String contextId, String key, Object value) → Promise<SharedContextEntry>`
- Read-through checks: calling agent must be in `authorizedAgentIds` or within `scope`.
- Write checks: calling agent must be `ownerAgentId` or `immutable == false`.

**Data Cloud implementation** `DataCloudSharedContextRepository`:

- Flyway migration: `shared_contexts` table and `shared_context_entries` table (JSONB for entries).
- Index on `(session_id, scope)` for session-scoped lookups.
- Expiry enforced via a periodic cleanup job using the existing Data Cloud scheduling infrastructure.

**Files (new in `platform/java/agent-core/src/main/java/com/ghatana/agent/context/`):**

- `SharedContext.java`, `SharedContextEntry.java`
- `ContextSharingScope.java`, `MergeStrategy.java`
- `SharedContextRepository.java`
- `SharedContextRepositoryContractTest.java` (abstract contract test)

**Test:** Contract test + integration test.

- `products/data-cloud/platform-api/src/test/java/com/ghatana/datacloud/context/DataCloudSharedContextRepositoryIT.java`
- Testcontainers PostgreSQL.
- Test: `WITHIN_SESSION` scope — agents in same session can read, agents in other sessions cannot.
- Test: `immutable=true` — write from non-owner rejected.
- Test: `OWNER_ONLY` merge strategy — only owner writes accepted.
- Test: `transferOwnership` — new owner can write, old owner cannot.
- Test: session expiry clears all `WITHIN_SESSION` contexts.

**Acceptance:** All scope and ownership rules enforced at repository level. Expiry is automatic.

---

### P8-T12 — Wire Pluggability Into `AgentTelemetryContract` and Update ArchUnit

**What:**  
Extend `AgentTelemetryContract` (Phase 6) to cover all Phase 8 lifecycle events, and add ArchUnit rules enforcing that the interaction protocol is not bypassed.

**Where:**

- `platform/java/observability/src/main/java/com/ghatana/platform/observability/agent/AgentTelemetryContract.java`
- `products/aep/orchestrator/src/test/java/com/ghatana/aep/architecture/AepArchitectureTest.java`

**How — add telemetry constants:**

```java
// Package load/unload/swap
public static final String SPAN_PACKAGE_LOAD     = "ghatana.agent.package.load";
public static final String SPAN_PACKAGE_UNLOAD   = "ghatana.agent.package.unload";
public static final String SPAN_SWAP_INITIATE    = "ghatana.agent.swap.initiate";
public static final String SPAN_SWAP_DRAIN       = "ghatana.agent.swap.drain";
public static final String SPAN_SWAP_HANDOFF     = "ghatana.agent.swap.handoff";
public static final String SPAN_SWAP_CUTOVER     = "ghatana.agent.swap.cutover";

// Interaction
public static final String SPAN_INTERACTION      = "ghatana.agent.interaction";
public static final String ATTR_INTERACTION_MODE = "ghatana.agent.interaction.mode";

// Supervision
public static final String SPAN_SUPERVISION_DECISION = "ghatana.agent.supervision.decision";
public static final String ATTR_SUPERVISION_STRATEGY = "ghatana.agent.supervision.strategy";

// Composition
public static final String SPAN_COMPOSITION      = "ghatana.agent.composition";
public static final String ATTR_COMPOSITION_PATTERN = "ghatana.agent.composition.pattern";

// Handoff
public static final String SPAN_HANDOFF_COMPLETE = "ghatana.agent.handoff.complete";
public static final String ATTR_HANDOFF_REASON   = "ghatana.agent.handoff.reason";

// Repetition
public static final String SPAN_REPETITION_LIMIT = "ghatana.agent.repetition.limit_exceeded";

// Learning signal
public static final String SPAN_LEARNING_SIGNAL  = "ghatana.agent.learning.signal_received";
public static final String ATTR_SIGNAL_TYPE      = "ghatana.agent.learning.signal_type";
public static final String ATTR_SIGNAL_SOURCE    = "ghatana.agent.learning.signal_source";

// Context sharing
public static final String SPAN_CONTEXT_WRITE    = "ghatana.agent.context.write";
public static final String ATTR_CONTEXT_SCOPE    = "ghatana.agent.context.scope";
```

**New ArchUnit rules:**

- All agent-to-agent calls within `products.aep` must go through `AgentInteractionProtocol` — no direct invocation of `TypedAgent.process(...)` from outside the protocol layer.
- Classes in `com.ghatana.agent.pluggability` must not depend on any `products.*` class.
- Classes in `com.ghatana.agent.supervision` must not depend on any `products.*` class.
- Classes in `com.ghatana.agent.composition` must not depend on any `products.*` class.

**Test:** `AgentTelemetryContractTest.java` (extend Phase 6 test) — verify all new constants are non-null and non-empty with no duplicates.

**Acceptance:** All 8 Phase 8 lifecycle dimensions have span/attribute constants. ArchUnit rules pass on every commit.

---

## Track X: Trust, Privacy, Security, Explainability, and Capability Gates

> **Goal:** Make privacy, security, observability, explainability, and capability maturity mandatory release concerns across the whole agent lifecycle.  
> **Why:** The repo already has strong seams (`DataAccessBroker`, `PolicyAsCodeEngine`, `MemorySecurityManager`, `MemoryRedactionFilter`, `AgentTraceLedger`, `ApprovalGateway`), but they are not yet enforced as one coherent trust envelope.

### X-T1 — Publish the Trust Control Matrix and Threat/Privacy Model

**What:**  
Create one control matrix that maps agent lifecycle stages to concrete privacy, security, explainability, and observability controls.

**Where:**

- `docs/agent-system/agent-trust-control-matrix.md`
- `docs/adr/ADR-021-agent-trust-gates.md`

**How:**

- Map each lifecycle stage (spec load, release admission, context retrieval, planning, tool use, memory write, evaluation, promotion, rollout, incident response) to required controls.
- Base the matrix on NIST AI RMF, the NIST Generative AI profile, the NIST Privacy Framework, NIST explainability principles, and OWASP GenAI/agentic guidance.
- For each control, identify the current code seam that can enforce it now, and whether the seam is mandatory, partial, or missing.
- Explicitly call out known risks: prompt/tool injection, memory poisoning, cross-tenant leakage, secret exfiltration, telemetry data leakage, under-explained autonomous actions.

**Acceptance:** Control matrix exists, is architecture-reviewed, and is referenced by Phase 2, 3, 5, 6, and 8 tasks.

---

### X-T2 — Make Governed Data Access Mandatory for Context, Memory, and Tool Flows

**What:**  
Require all governed data retrieval and memory hydration paths to pass through consent and purpose checks before use.

**Where:**

- `platform/java/data-governance/src/main/java/com/ghatana/data/governance/DataAccessBroker.java`
- `products/data-cloud/platform-api/`
- `products/aep/aep-agent-runtime/`

**How:**

- Integrate `DataAccessBroker.checkAccess(...)` into:
  - context retrieval
  - durable memory retrieval
  - sensitive tool input materialization
- Add an explicit purpose field to the relevant retrieval and memory request types where missing.
- Ensure denials are observable and explainable without leaking the blocked payload.
- Add regression tests for tenant mismatch, missing consent, and purpose mismatch.

**Acceptance:** Governed data access is enforced on all sensitive retrieval paths. No governed data retrieval bypass exists outside approved seams.

---

### X-T3 — Harden Memory Against Leakage, Poisoning, and Unbounded Retention

**What:**  
Turn memory privacy and integrity from best effort into a required lifecycle.

**Where:**

- `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/memory/security/MemorySecurityManager.java`
- `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/memory/security/TenantIsolatingMemorySecurityManager.java`
- `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/memory/security/MemoryRedactionFilter.java`
- `products/data-cloud/platform-api/src/main/java/com/ghatana/datacloud/memory/`

**How:**

- Require provenance, release ID, determinism class, confidence, retention class, and shareability metadata on all durable memory writes.
- Redact sensitive fields before write; do not rely on downstream log scrubbing.
- Add poisoning and duplication heuristics to promotion candidates before procedural promotion.
- Close the gap between `canRead`/`canWrite` enforcement and broad search access by documenting and testing tenant-filter guarantees for `canSearch`.
- Add retention-expiry and deletion verification tests.

**Acceptance:** Memory writes and reads are tenant-safe, privacy-scoped, and promotion-safe by contract, with explicit tests for leakage and poisoning regressions.

---

### X-T4 — Define the Explainability Contract and Evidence Bundle

**What:**  
Create one portable explanation artifact that every advanced run, denial, approval, and promotion path can emit.

**Where:**

- `platform/java/agent-core/src/main/java/com/ghatana/agent/explainability/`
- `products/aep/aep-agent-runtime/`
- `products/data-cloud/platform-analytics/`

**How:**

- Define `ExplanationRecord` / `ExplanationBundle` types carrying:
  - goal summary
  - context sources used
  - memory artifacts read/written
  - plan or policy path
  - tool usage summary
  - approval/denial decisions
  - confidence and uncertainty summary
  - learning or promotion signals, if any
- Persist redacted explanation references alongside traces and release evidence.
- Ensure the explanation model covers DSLA/NDSLA concerns from the merged spec: partial observability, learning loops, memory promotion, and negative evidence.

**Acceptance:** Every advanced run and every blocked consequential action can produce a durable explanation artifact that operators can inspect without reconstructing prompts from raw logs.

---

### X-T5 — Make Telemetry Privacy-Safe and Release-Scoped

**What:**  
Extend Phase 6 so telemetry is both complete enough to govern the system and restrained enough not to become a data leak.

**Where:**

- `platform/java/observability/`
- `platform/java/tool-runtime/`
- `products/aep/aep-agent-runtime/`

**How:**

- Require release ID, policy pack ID, explanation contract version, and redaction profile ID on every governed run span.
- Emit policy-denial, approval, and memory-mutation spans even when actions are blocked.
- Add redaction tests to verify prompts, memory payloads, and sensitive tool inputs are not exported raw.
- Define which telemetry attributes are allowed in cleartext, hashed, redacted, or disallowed.

**Acceptance:** Telemetry is usable for debugging and governance without exposing sensitive content by default.

---

### X-T6 — Build a Capability-Maturity Gate Against the Merged Self-Learning Spec

**What:**  
Make capability claims measurable against `Unified_Self_Learning_Agents_Spec_Merged.md`.

**Where:**

- `docs/agent-system/capability-maturity-profile.md`
- `platform/java/agent-core/`
- `products/aep/aep-agent-runtime/`
- `products/data-cloud/`

**How:**

- Define a capability maturity profile that scores whether a release actually implements:
  - DSLA/NDSLA memory hierarchy
  - learning level (L0-L5)
  - learning signal routing
  - procedural promotion path
  - explainability evidence
  - partial-observability handling
  - explore-tier vs authority-tier separation
- Require every `AgentRelease` to declare its maturity profile.
- Add eval gates so an agent cannot claim higher autonomy or self-learning capability than the runtime and evidence plane support.

**Acceptance:** Capability labels such as "autonomous", "self-learning", and "bounded autonomous" are backed by measurable release criteria rather than prose.

---

## Cross-Cutting Tasks (Any Phase)

### CC-T1 — Add ArchUnit Boundary Enforcement Tests

**What:**  
Add ArchUnit tests to enforce architectural boundaries: no product-local schema drift, no tool calls outside `ToolExecutor`, no agent-core depending on AEP, etc.

**Where:**

- `products/aep/orchestrator/src/test/java/com/ghatana/aep/architecture/AepArchitectureTest.java`
- `platform/java/agent-core/src/test/java/com/ghatana/agent/architecture/AgentCoreArchitectureTest.java`

**Rules to enforce:**

- `agent-core` must not depend on any `aep`, `data-cloud`, or product package.
- All tool calls in `aep-agent-runtime` must go through `com.ghatana.platform.toolruntime.ToolExecutor`.
- No `platform.agent.*` class may import from `com.ghatana.aep.*`.
- All public platform classes must have at least one `@doc.` Javadoc tag.

**Acceptance:** ArchUnit tests run in CI. Any violation fails the build.

---

### CC-T2 — Update Version Catalog for New Dependencies

**What:**  
Add any new library versions to `gradle/libs.versions.toml`. This task is picked up whenever a Phase adds a new dependency.

**Where:** `gradle/libs.versions.toml`

**Expected additions:**

- `opentelemetry-sdk-testing` (for Phase 6 tests)
- `openfeature` Java SDK (for future rollout abstraction — add version, no code yet in Phase 7 scope)
- Validate that `sigstore` has a suitable Java client if signing is needed in Phase 2

**Rules:**

- Always add to the `[versions]` section before adding to `[libraries]`.
- Add a `# Phase N` comment above the version entry.
- Never hardcode a version string in a `build.gradle.kts`.

**Acceptance:** All new dependencies are version-cataloged. No hardcoded versions anywhere.

---

### CC-T3 — Add Doc-Tag Baseline Check for New Public Classes

**What:**  
Ensure the existing `gradle/doc-tag-check.gradle` baseline is updated for every new public class added across all phases.

**Where:** `gradle/doc-tag-baseline.txt`

**How:**

- After each Phase's code changes, run: `./gradlew checkDocTags` from the repo root.
- Add any newly approved classes to the baseline.
- Never suppress a missing tag without a documented reason.

**Acceptance:** `./gradlew checkDocTags` passes with zero violations after each phase.

---

### CC-T4 — TypeScript/React Agent UI Updates (AEP UI)

**What:**  
Update the AEP UI (`products/aep/ui/`) to surface the new release lifecycle states and rollout controls introduced in Phase 2.

**Where:** `products/aep/ui/src/`

**How:**

- Add `AgentReleaseState` TypeScript enum matching the Java enum (use a Zod schema for runtime validation):
  ```ts
  import { z } from "zod";
  export const AgentReleaseStateSchema = z.enum([
    "DRAFT",
    "VALIDATED",
    "SHADOW",
    "CANARY",
    "ACTIVE",
    "DEPRECATED",
    "RETIRED",
    "BLOCKED",
  ]);
  export type AgentReleaseState = z.infer<typeof AgentReleaseStateSchema>;
  ```
- Add `AgentReleaseBadge` component to display state with color coding.
- Add `AgentReleaseTransitionButton` component for authorized state transitions.
- Use `TanStack Query` for fetching release data. Type all `useQuery` hooks explicitly.
- No `any` types. All component props are typed interfaces.

**Test:** `products/aep/ui/src/components/__tests__/AgentReleaseBadge.test.tsx`

- Test all 8 states render correct colors and labels.
- Use React Testing Library.

**Acceptance:** TypeScript compiles with `strict: true`. ESLint passes with zero warnings.

---

## Rollout and Risk Notes

### Backward Compatibility During Migration

The repo should stay **fix-forward for canonical stored values and runtime behavior**, while allowing bounded compatibility at ingestion and migration boundaries. In practice:

- Phase 1 (enum aliases) must be merged before Phase 6 (catalog validation in CI) to avoid breaking existing YAML loading.
- Phase 3 (`ToolExecutor`) must be merged and all AEP tool calls routed before Phase 6 tool metrics instrumentation.
- Data Cloud Flyway migrations are additive (new tables, new columns). Existing tables are not modified in any breaking way.
- `AgentReleaseRepository` dispatch checks (Phase 2-T5) must be feature-flagged during rollout to prevent breaking agents that have no releases yet. Use `if (release.isEmpty()) allowLegacyDispatch()` with a deprecation log.
- Phase 8 `AgentCapabilityManifest` is validated-but-optional initially: agents without a manifest are assigned `defaultSafe()` values and a deprecation warning is logged. Full enforcement (reject agents without a manifest) lands in v2.1.

Compatibility is acceptable only when it helps migrate old definitions into one canonical vocabulary. It is not acceptable as a permanent second storage format or alternate runtime contract.

### Feature Flag Strategy for Release-Aware Dispatch (Phase 2-T5)

Since existing agents have no `AgentRelease` records, introduce a temporary legacy bypass:

```java
// In GovernedAgentDispatcher, after release lookup:
if (activeRelease.isEmpty()) {
    log.warn("LEGACY_DISPATCH: agent={} has no active release. Allow legacy path. " +
             "This will become an error in release 2.1.", agentId);
    return legacyDispatch(ctx, agent, input);
}
```

Remove `legacyDispatch` once all agents have releases registered (tracked as `CC-T5`).

### Phase 8 Rollout Strategy

Phase 8 introduces significant new runtime components. Use a staged rollout:

| Stage | What lands                                                                 | Risk                                       |
| ----- | -------------------------------------------------------------------------- | ------------------------------------------ |
| 8-A   | P8-T1, P8-T2 (models only, no runtime)                                     | None                                       |
| 8-B   | P8-T5 (`AgentInteractionProtocol`, wraps existing TypedAgent calls)        | Low — additive                             |
| 8-C   | P8-T3 (`AgentPackageLoader`, opt-in via feature flag)                      | Medium — new loading path                  |
| 8-D   | P8-T6 (`SupervisionContract`, enforcement on new agents only)              | Medium                                     |
| 8-E   | P8-T7 (`CompositionPolicy`, replaces existing OrchestrationStrategy calls) | Medium — existing callers updated          |
| 8-F   | P8-T8 (`HandoffCoordinator`), P8-T9 (`RepetitionGovernor`)                 | Medium                                     |
| 8-G   | P8-T4 (`AgentSwapCoordinator`, hot-swap)                                   | High — requires full Phase 8-A through 8-F |
| 8-H   | P8-T10 (`LearningSignalRouter`), P8-T11 (`SharedContextRepository`)        | Low — additive                             |
| 8-I   | P8-T12 (telemetry + ArchUnit enforcement)                                  | Low                                        |

Stage 8-G (hot-swap) must not be deployed until all earlier stages are stable in staging for at least 2 weeks.

---

## Definition of Done per Task

A task is **done** when ALL of the following are true:

- [ ] Code compiles with `./gradlew build` (or `pnpm build` for TypeScript).
- [ ] TypeScript: `tsc --noEmit` passes with `strict: true`. Zero ESLint warnings.
- [ ] Java: `./gradlew checkstyle` passes. Zero PMD/SpotBugs violations on new code.
- [ ] All new public Java classes have all four `@doc.*` Javadoc tags.
- [ ] Tests are written for every meaningful behavior path. Async tests extend `EventloopTestBase`.
- [ ] `./gradlew checkDocTags` passes (update baseline if needed).
- [ ] Structured logs are emitted for every important state change.
- [ ] No hardcoded secrets, endpoints, or version strings.
- [ ] PR description references the task ID (e.g., `P2-T4` or `P8-T6`) and links to this document.
- [ ] ArchUnit tests (CC-T1 + P8-T12) still pass after the change.
- [ ] Privacy, security, observability, and explainability impact is explicitly documented in the task notes or PR description.
- [ ] Redaction, retention, and tenant-isolation behavior is tested for any task that touches memory, context, telemetry, or tool payloads.
- [ ] Capability claims remain honest: release metadata, UI labels, and docs do not overstate autonomy or self-learning behavior beyond what the runtime and evidence plane actually enforce.
- [ ] **Phase 8 tasks additionally:** `AgentCapabilityManifest` is validated before any runtime interaction. Governance violations produce typed exceptions, never silent nulls.

---

## Appendix: Key File Paths Reference

| Artifact                                 | Path                                                                                                                                 |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `TypedAgent`                             | `platform/java/agent-core/src/main/java/com/ghatana/agent/TypedAgent.java`                                                           |
| `AgentType`                              | `platform/java/agent-core/src/main/java/com/ghatana/agent/AgentType.java`                                                            |
| `AgentDescriptor`                        | `platform/java/agent-core/src/main/java/com/ghatana/agent/AgentDescriptor.java`                                                      |
| `AgentLogicProvider` SPI                 | `platform/java/agent-core/src/main/java/com/ghatana/agent/spi/AgentLogicProvider.java`                                               |
| `LearningEngine`                         | `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/learning/LearningEngine.java`                                    |
| `DelegationManager`                      | `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/coordination/DelegationManager.java`                             |
| `OrchestrationStrategy`                  | `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/coordination/OrchestrationStrategy.java`                         |
| `KernelPluginRuntimeManager`             | `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/plugin/runtime/KernelPluginRuntimeManager.java`                        |
| **Phase 2**                              |                                                                                                                                      |
| `AgentRelease` (new)                     | `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentRelease.java`                                                 |
| `AgentReleaseState` (new)                | `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentReleaseState.java`                                            |
| `PolicyPack` (new)                       | `platform/java/agent-core/src/main/java/com/ghatana/agent/release/PolicyPack.java`                                                   |
| `EvaluationPack` (new)                   | `platform/java/agent-core/src/main/java/com/ghatana/agent/release/EvaluationPack.java`                                               |
| `MemoryContract` (new)                   | `platform/java/agent-core/src/main/java/com/ghatana/agent/release/MemoryContract.java`                                               |
| `AgentInstanceConfig` (new)              | `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentInstanceConfig.java`                                          |
| `AgentReleaseRepository` SPI (new)       | `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentReleaseRepository.java`                                       |
| `DataCloudAgentReleaseRepository` (new)  | `products/data-cloud/agent-registry/src/main/java/com/ghatana/datacloud/agent/registry/release/DataCloudAgentReleaseRepository.java` |
| **Phase 3**                              |                                                                                                                                      |
| `ActionClass` (new)                      | `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/tools/ActionClass.java`                                          |
| `ToolContract` (new)                     | `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/tools/ToolContract.java`                                         |
| `ToolExecutionEnvelope` (new)            | `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/tools/ToolExecutionEnvelope.java`                                |
| `ToolExecutionResult` (new)              | `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/tools/ToolExecutionResult.java`                                  |
| `ToolExecutor` (new)                     | `platform/java/tool-runtime/src/main/java/com/ghatana/platform/toolruntime/ToolExecutor.java`                                        |
| **Phase 4**                              |                                                                                                                                      |
| `PlanGraph` (new)                        | `platform/java/agent-core/src/main/java/com/ghatana/agent/planning/PlanGraph.java`                                                   |
| `PlanCompiler` (new)                     | `platform/java/workflow/src/main/java/com/ghatana/platform/workflow/planning/PlanCompiler.java`                                      |
| **Phase 5**                              |                                                                                                                                      |
| `MemoryPromotionService` (new)           | `products/data-cloud/platform-api/src/main/java/com/ghatana/datacloud/memory/promotion/MemoryPromotionService.java`                  |
| **Phase 6**                              |                                                                                                                                      |
| `AgentRunTracer` (new)                   | `platform/java/observability/src/main/java/com/ghatana/platform/observability/agent/AgentRunTracer.java`                             |
| `AgentTelemetryContract` (new)           | `platform/java/observability/src/main/java/com/ghatana/platform/observability/agent/AgentTelemetryContract.java`                     |
| **Phase 7**                              |                                                                                                                                      |
| A/V Capability Descriptors (new)         | `products/audio-video/libs/common/src/main/resources/capabilities/`                                                                  |
| A/V Tool Handlers (new)                  | `products/audio-video/libs/common/src/main/java/com/ghatana/audiovideo/tools/`                                                       |
| **Phase 8 — Pluggability**               |                                                                                                                                      |
| `AgentCapabilityManifest` (new)          | `platform/java/agent-core/src/main/java/com/ghatana/agent/pluggability/AgentCapabilityManifest.java`                                 |
| `InteractionMode` (new)                  | `platform/java/agent-core/src/main/java/com/ghatana/agent/pluggability/InteractionMode.java`                                         |
| `SupervisionRole` (new)                  | `platform/java/agent-core/src/main/java/com/ghatana/agent/pluggability/SupervisionRole.java`                                         |
| `HandoffCapability` (new)                | `platform/java/agent-core/src/main/java/com/ghatana/agent/pluggability/HandoffCapability.java`                                       |
| `AgentCapabilityManifestValidator` (new) | `platform/java/agent-core/src/main/java/com/ghatana/agent/pluggability/AgentCapabilityManifestValidator.java`                        |
| `AgentPackage` (new)                     | `platform/java/agent-core/src/main/java/com/ghatana/agent/pluggability/AgentPackage.java`                                            |
| `AgentPackageBuilder` (new)              | `platform/java/agent-core/src/main/java/com/ghatana/agent/pluggability/AgentPackageBuilder.java`                                     |
| `AgentPackageLoader` (new)               | `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/runtime/pluggability/AgentPackageLoader.java`                        |
| `AgentSwapCoordinator` (new)             | `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/runtime/pluggability/AgentSwapCoordinator.java`                      |
| **Phase 8 — Interaction**                |                                                                                                                                      |
| `AgentMessage` (new)                     | `platform/java/agent-core/src/main/java/com/ghatana/agent/interaction/AgentMessage.java`                                             |
| `AgentEvent` (new)                       | `platform/java/agent-core/src/main/java/com/ghatana/agent/interaction/AgentEvent.java`                                               |
| `AgentResponse` (new)                    | `platform/java/agent-core/src/main/java/com/ghatana/agent/interaction/AgentResponse.java`                                            |
| `AgentInteractionProtocol` (new)         | `platform/java/agent-core/src/main/java/com/ghatana/agent/interaction/AgentInteractionProtocol.java`                                 |
| `DefaultAgentInteractionProtocol` (new)  | `platform/java/agent-core/src/main/java/com/ghatana/agent/interaction/DefaultAgentInteractionProtocol.java`                          |
| **Phase 8 — Supervision**                |                                                                                                                                      |
| `SupervisionContract` (new)              | `platform/java/agent-core/src/main/java/com/ghatana/agent/supervision/SupervisionContract.java`                                      |
| `SupervisionStrategy` (new)              | `platform/java/agent-core/src/main/java/com/ghatana/agent/supervision/SupervisionStrategy.java`                                      |
| `SupervisionRegistry` (new)              | `platform/java/agent-core/src/main/java/com/ghatana/agent/supervision/SupervisionRegistry.java`                                      |
| **Phase 8 — Composition**                |                                                                                                                                      |
| `CompositionPolicy` (new)                | `platform/java/agent-core/src/main/java/com/ghatana/agent/composition/CompositionPolicy.java`                                        |
| `CompositionPattern` (new)               | `platform/java/agent-core/src/main/java/com/ghatana/agent/composition/CompositionPattern.java`                                       |
| `CompositeOrchestrator` (new)            | `platform/java/agent-core/src/main/java/com/ghatana/agent/composition/CompositeOrchestrator.java`                                    |
| `VotingOrchestration` (new)              | `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/coordination/VotingOrchestration.java`                           |
| `ScatterGatherOrchestration` (new)       | `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/coordination/ScatterGatherOrchestration.java`                    |
| **Phase 8 — Handoff**                    |                                                                                                                                      |
| `AgentHandoff` (new)                     | `platform/java/agent-core/src/main/java/com/ghatana/agent/handoff/AgentHandoff.java`                                                 |
| `AgentContextSnapshot` (new)             | `platform/java/agent-core/src/main/java/com/ghatana/agent/handoff/AgentContextSnapshot.java`                                         |
| `HandoffReason` (new)                    | `platform/java/agent-core/src/main/java/com/ghatana/agent/handoff/HandoffReason.java`                                                |
| `HandoffCoordinator` (new)               | `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/runtime/handoff/HandoffCoordinator.java`                             |
| **Phase 8 — Repetition**                 |                                                                                                                                      |
| `RepetitionPolicy` (new)                 | `platform/java/agent-core/src/main/java/com/ghatana/agent/repetition/RepetitionPolicy.java`                                          |
| `RetryStrategy` (new)                    | `platform/java/agent-core/src/main/java/com/ghatana/agent/repetition/RetryStrategy.java`                                             |
| `TerminationCondition` (new)             | `platform/java/agent-core/src/main/java/com/ghatana/agent/repetition/TerminationCondition.java`                                      |
| `RepetitionGovernor` (new)               | `platform/java/tool-runtime/src/main/java/com/ghatana/platform/toolruntime/RepetitionGovernor.java`                                  |
| **Phase 8 — Learning Signals**           |                                                                                                                                      |
| `LearningSignal` (new)                   | `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/signal/LearningSignal.java`                                       |
| `LearningSignalType` (new)               | `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/signal/LearningSignalType.java`                                   |
| `LearningSignalSource` (new)             | `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/signal/LearningSignalSource.java`                                 |
| `LearningSignalRouter` (new)             | `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/signal/LearningSignalRouter.java`                                 |
| `DefaultLearningSignalRouter` (new)      | `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/signal/DefaultLearningSignalRouter.java`                          |
| **Phase 8 — Context Sharing**            |                                                                                                                                      |
| `SharedContext` (new)                    | `platform/java/agent-core/src/main/java/com/ghatana/agent/context/SharedContext.java`                                                |
| `SharedContextEntry` (new)               | `platform/java/agent-core/src/main/java/com/ghatana/agent/context/SharedContextEntry.java`                                           |
| `ContextSharingScope` (new)              | `platform/java/agent-core/src/main/java/com/ghatana/agent/context/ContextSharingScope.java`                                          |
| `MergeStrategy` (new)                    | `platform/java/agent-core/src/main/java/com/ghatana/agent/context/MergeStrategy.java`                                                |
| `SharedContextRepository` SPI (new)      | `platform/java/agent-core/src/main/java/com/ghatana/agent/context/SharedContextRepository.java`                                      |
| `DataCloudSharedContextRepository` (new) | `products/data-cloud/platform-api/src/main/java/com/ghatana/datacloud/context/DataCloudSharedContextRepository.java`                 |
| **Infrastructure**                       |                                                                                                                                      |
| `GovernedAgentDispatcher`                | `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`                         |
| AEP Agent Catalog                        | `products/aep/agent-catalog/agent-catalog.yaml`                                                                                      |
| Version Catalog                          | `gradle/libs.versions.toml`                                                                                                          |
| Doc-Tag Baseline                         | `gradle/doc-tag-baseline.txt`                                                                                                        |
| AEP UI (TypeScript)                      | `products/aep/ui/src/`                                                                                                               |

---

## Sources

- NIST AI Risk Management Framework:
  - https://www.nist.gov/itl/ai-risk-management-framework
- NIST Generative AI Profile:
  - https://www.nist.gov/publications/artificial-intelligence-risk-management-framework-generative-artificial-intelligence
- NIST Privacy Framework:
  - https://www.nist.gov/privacy-framework
- NIST Explainable AI principles:
  - https://www.nist.gov/publications/four-principles-explainable-artificial-intelligence
- OWASP GenAI and agentic application guidance:
  - https://genai.owasp.org/
  - https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/
- OpenTelemetry semantic conventions:
  - https://opentelemetry.io/docs/specs/semconv/
  - https://opentelemetry.io/docs/specs/semconv/gen-ai/

_This document is the authoritative implementation guide for the Agent System Modernization initiative. All tasks reference the blueprint at `docs/agent-system/AGENT_SYSTEM_MODERNIZATION_BLUEPRINT_2026-04-06.md` — specifically §15 (Concrete Work Plan), §19 (Agent Pluggability), and §20 (Inter-Agent Protocol). Update this document as tasks are completed, deferred, or discovered to need splitting._
