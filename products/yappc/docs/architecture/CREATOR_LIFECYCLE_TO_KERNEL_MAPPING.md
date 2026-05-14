# Creator Lifecycle to Kernel Mapping

**Status:** Active  
**Last Updated:** 2026-05-13  
**Owner:** YAPPC Architecture Team

---

## Purpose

This document defines the mapping between YAPPC's 8-phase Creator Lifecycle and Kernel's Product Lifecycle. It clarifies YAPPC's responsibilities at each phase and how YAPPC interacts with Kernel when generating lifecycle-governed ProductUnits.

---

## Lifecycle Phase Mapping

| YAPPC Phase | YAPPC Responsibility | Kernel Interaction |
|-------------|---------------------|-------------------|
| **Intent** | Capture product/app goal, requirements, and success criteria | None or draft ProductUnitIntent for validation |
| **Shape** | Architecture/product model, surface selection, runtime choices | Validate ProductUnit profile possibilities against Kernel registry |
| **Validate** | Validate shape/spec against constraints and quality gates | Optional Kernel plan validation (via CLI/API) |
| **Generate** | Generate code/artifacts from intent and shape | Emit ProductUnitIntent and source output for Kernel consumption |
| **Run** | Run generated output (local testing, preview) | For lifecycle-governed ProductUnits: Kernel dev/test/build/package/deploy |
| **Observe** | Observe execution results, metrics, and behavior | Kernel health snapshots/manifests, lifecycle events |
| **Learn** | Learn from failure/results, update models | Kernel events, gate failures, artifact metadata |
| **Evolve** | Update intent/design/code based on learning | Updated ProductUnitIntent/change request to Kernel |

---

## Phase-by-Phase Details

### Intent Phase

**YAPPC Responsibility:**
- Capture product/app goals from natural language or structured input
- Define success criteria and constraints
- Identify target surfaces (web API, frontend, backend, mobile, etc.)
- Specify runtime preferences (language, framework, deployment target)

**Kernel Interaction:**
- **None** for generic projects
- **Draft ProductUnitIntent** for lifecycle-governed ProductUnits (optional early validation)

**Output:**
- YAPPC project intent document
- Optional draft ProductUnitIntent for Kernel validation

---

### Shape Phase

**YAPPC Responsibility:**
- Generate architecture/product model from intent
- Select appropriate surfaces and technologies
- Define module structure and dependencies
- Specify runtime configuration

**Kernel Interaction:**
- **Validate ProductUnit profile** against Kernel registry (optional)
- Check if target provider exists or is explicitly external
- Validate surface list compatibility with known profiles

**Output:**
- YAPPC shape specification
- Validated ProductUnit profile (if lifecycle-governed)

---

### Validate Phase

**YAPPC Responsibility:**
- Validate shape against YAPPC creator/SDLC gates
- Check artifact presence from prior phases
- Ensure entry/exit criteria are satisfied
- Run quality checks (linting, formatting, static analysis)

**Kernel Interaction:**
- **Optional Kernel plan validation** via CLI/API
- Validate that ProductUnit intent is compatible with Kernel capabilities
- Check lifecycle profile availability

**Output:**
- YAPPC validation result
- Kernel plan validation result (if lifecycle-governed)

---

### Generate Phase

**YAPPC Responsibility:**
- Generate code/artifacts from validated shape
- Apply templates and generators
- Produce source code, configuration, documentation
- Create build scripts and CI configuration

**Kernel Interaction:**
- **Emit ProductUnitIntent** for lifecycle-governed ProductUnits
- Write intent to `targetPath/.yappc/product-unit-intent.yaml`
- Include provenance: producer=yappc, source phase, project ID, workspace ID

**Output:**
- Generated source code and artifacts
- ProductUnitIntent YAML file (if lifecycle-governed)
- Instructions for Kernel CLI: `pnpm kernel product create --from-intent <file>`

---

### Run Phase

**YAPPC Responsibility:**
- Run generated output locally for testing
- Execute preview builds and deployments
- Monitor local execution results
- Capture metrics and logs

**Kernel Interaction:**
- For **generic projects**: YAPPC manages execution directly
- For **lifecycle-governed ProductUnits**: 
  - User executes Kernel CLI: `pnpm kernel product create --from-intent <file>`
  - Kernel executes Product Lifecycle: dev → validate → test → build → package → deploy → verify
  - YAPPC observes Kernel lifecycle events and manifests

**Output:**
- Local execution results (generic projects)
- Kernel lifecycle execution results (lifecycle-governed ProductUnits)

---

### Observe Phase

**YAPPC Responsibility:**
- Observe execution results and metrics
- Analyze behavior against success criteria
- Identify issues and opportunities
- Surface insights to user

**Kernel Interaction:**
- **Consume Kernel health snapshots/manifests** from `.kernel/out/products/<productId>/**`
- Parse public lifecycle JSON files (plan, result, artifacts, deployment, health)
- Normalize into YAPPC read model for display
- Display gate failures, artifact states, deployment status

**Output:**
- YAPPC observation dashboard
- Kernel lifecycle health visualization

---

### Learn Phase

**YAPPC Responsibility:**
- Learn from execution results and failures
- Update internal models and recommendations
- Improve intent generation and shaping
- Refine quality checks and validation

**Kernel Interaction:**
- **Consume Kernel events** (phase transitions, gate failures, artifact production)
- Analyze gate failure patterns to improve recommendations
- Learn from artifact metadata and deployment outcomes
- Incorporate agent governance learning evidence

**Output:**
- Updated YAPPC learning models
- Improved recommendations and suggestions

---

### Evolve Phase

**YAPPC Responsibility:**
- Update intent based on learning
- Modify design and code as needed
- Propagate changes through lifecycle
- Trigger new lifecycle run if needed

**Kernel Interaction:**
- **Updated ProductUnitIntent** for lifecycle-governed ProductUnits
- Change request to Kernel for ProductUnit updates
- Trigger new Kernel lifecycle run via CLI/API

**Output:**
- Updated YAPPC project intent
- Updated ProductUnitIntent (if lifecycle-governed)

---

## Key Principles

### 1. Clear Separation of Concerns

- **YAPPC Creator Lifecycle**: Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve
- **Kernel Product Lifecycle**: dev → validate → test → build → package → deploy → verify

YAPPC manages creation and observation. Kernel manages delivery execution.

### 2. ProductUnitIntent as Boundary

ProductUnitIntent is the clean boundary between YAPPC and Kernel:
- YAPPC generates ProductUnitIntent in Generate phase
- Kernel consumes ProductUnitIntent to execute Product Lifecycle
- YAPPC does not mutate Kernel registry files directly

### 3. Observability Without Duplication

YAPPC observes Kernel lifecycle through public contracts:
- Health snapshots and manifests (filesystem)
- Lifecycle events (future: Data Cloud/Event Cloud)
- Public APIs (future: Kernel health endpoints)

YAPPC does not duplicate Kernel execution logic.

### 4. Advisory Recommendations

YAPPC provides recommendations based on Kernel state:
- Suggest next actions based on gate failures
- Recommend fixes for missing artifacts
- Advise on deployment issues

YAPPC does not execute Kernel gates or lifecycle phases.

---

## Example Flow: Lifecycle-Governed ProductUnit

```
1. Intent Phase
   User: "Create a web API for digital marketing campaigns"
   YAPPC: Captures intent, defines success criteria

2. Shape Phase
   YAPPC: Generates architecture, selects surfaces (web API, frontend)
   Kernel: Validates ProductUnit profile against registry

3. Validate Phase
   YAPPC: Validates shape against creator gates
   Kernel: Optional plan validation

4. Generate Phase
   YAPPC: Generates code, templates, configuration
   YAPPC: Writes ProductUnitIntent to .yappc/product-unit-intent.yaml
   YAPPC: Prints: "pnpm kernel product create --from-intent .yappc/product-unit-intent.yaml"

5. Run Phase
   User: Executes Kernel CLI command
   Kernel: Executes dev → validate → test → build → package → deploy → verify
   Kernel: Writes lifecycle results to .kernel/out/products/<productId>/

6. Observe Phase
   YAPPC: Reads .kernel/out/products/<productId>/health-snapshot.json
   YAPPC: Displays lifecycle health, gate failures, deployment status

7. Learn Phase
   YAPPC: Analyzes gate failures, updates recommendations
   YAPPC: Learns from artifact metadata and deployment outcomes

8. Evolve Phase
   YAPPC: Updates intent based on learning
   YAPPC: Generates updated ProductUnitIntent
   User: Re-triggers Kernel lifecycle with updated intent
```

---

## Example Flow: Generic Project

```
1. Intent Phase
   User: "Create a simple Node.js web service"
   YAPPC: Captures intent

2. Shape Phase
   YAPPC: Generates architecture, selects Node.js runtime

3. Validate Phase
   YAPPC: Validates shape against creator gates

4. Generate Phase
   YAPPC: Generates code, package.json, README
   YAPPC: Does NOT generate ProductUnitIntent (generic project)

5. Run Phase
   YAPPC: Executes local build and test
   YAPPC: Manages execution directly (no Kernel involvement)

6. Observe Phase
   YAPPC: Observes local execution results
   YAPPC: Displays metrics and logs

7. Learn Phase
   YAPPC: Learns from local execution results

8. Evolve Phase
   YAPPC: Updates intent and regenerates code
```

---

## References

- [Kernel Visibility and Control Plane](KERNEL_VISIBILITY_AND_CONTROL_PLANE.md)
- [YAPPC Architecture](../ARCHITECTURE.md)
- [Kernel Product Contracts](../../../../platform/typescript/kernel-product-contracts)
- [Implementation Plan 02](../../../../../../Downloads/implementation_plan_02_yappc_visibility_shared_libraries.md)

---

**Status:** Living Document  
**Owner:** YAPPC Architecture Team  
**Review Cycle:** As needed during implementation
