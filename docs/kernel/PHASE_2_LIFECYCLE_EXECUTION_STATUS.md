# Phase 2: Kernel Lifecycle Execution and Adapter Development - Status

**Purpose**: Document the current state of Phase 2 implementation and integration opportunities.

**Last Updated**: 2026-05-20

---

## Phase 2 Objective

Improve real execution semantics for phases, adapters, artifacts, deployments, and failure classification.

## Validation Commands

```bash
pnpm check:phase2
pnpm validate:digital-marketing
pnpm validate:phr
pnpm build:digital-marketing
pnpm build:phr
```

The `check:phase2` command includes:
- `check:digital-marketing-lifecycle-pilot`
- `check:phr-lifecycle-pilot`
- `check:kernel-platform-lifecycle`
- `check:toolchain-adapter-contracts`
- `check:product-artifact-contracts`
- `check:product-deployment-contracts`
- `check:product-environment-contracts`
- `check:secret-default-credentials`

---

## Current Execution Infrastructure

### Kernel Lifecycle Engine

**Location**: `platform/typescript/kernel-lifecycle/src/`

**Key Components**:
- `execution/ProductLifecycleExecutor.ts` - Orchestrates phase execution
- `execution/ProductLifecycleStepRunner.ts` - Runs individual steps via adapters
- `execution/ExecutionFailureHandler.ts` - Handles step failures with retry logic
- `execution/ExecutionLogger.ts` - Structured logging for execution
- `execution/ExecutionResultCollector.ts` - Collects and aggregates results
- `planning/ProductLifecyclePlanner.ts` - Generates execution plans
- `gates/GateExecutor.ts` - Evaluates lifecycle gates
- `manifest/LifecycleManifestWriter.ts` - Writes lifecycle manifests

### Current Execution Flow

1. **Plan Generation**: `ProductLifecyclePlanner` creates a `ProductLifecyclePlan`
2. **Gate Evaluation**: `GateExecutor` checks required gates before execution
3. **Step Execution**: `ProductLifecycleStepRunner` executes steps via registered adapters
4. **Failure Handling**: `ExecutionFailureHandler` applies failure policies (fail-closed, fail-open, continue-on-error)
5. **Result Collection**: `ExecutionResultCollector` aggregates step results
6. **Manifest Writing**: `LifecycleManifestWriter` persists authoritative results

### Current Failure Handling

The `ExecutionFailureHandler` currently supports:
- **Fail-closed**: Stop execution on first failure
- **Fail-open**: Continue execution despite failure
- **Continue-on-error**: Continue execution on errors
- **Retry logic**: Exponential backoff with configurable max retries
- **Notification**: Optional notification on failure

---

## Phase 1.5 Integration Opportunities

### LifecycleFailureClassifier Integration

**Current State**: `ExecutionFailureHandler` uses basic failure policies without detailed classification.

**Integration Points**:
1. **Enhance Failure Handling**: Integrate `LifecycleFailureClassifier` into `ExecutionFailureHandler`
   - Map error types to failure categories (config, adapter, command, gate, artifact, dependency, environment, approval, policy, security, provider, infrastructure)
   - Assign severity levels (critical, high, medium, low, info)
   - Determine retryability and human intervention requirements
   - Provide remediation steps and known workarounds

2. **Adapter Failure Classification**: Enhance `AdapterResult` to include `LifecycleFailureClassifier`
   - Classify adapter-specific failures with component context
   - Provide actionable messages for common failure patterns
   - Link failures to related failure codes for grouping

### PlanExplain Integration

**Current State**: `ProductLifecyclePlanner` generates `ProductLifecyclePlan` but lacks detailed explain output.

**Integration Points**:
1. **Enhanced Plan Output**: Add `PlanExplain` generation to `ProductLifecyclePlanner`
   - Generate dependency graph from plan steps
   - Include provider health checks in plan output
   - Add gate check results to plan explain
   - Include artifact expectations and validation status
   - Add approval policy and status when required
   - Include environment preflight checks

2. **Execution Readiness**: Use `PlanExplain` to determine execution readiness
   - Check overall readiness before execution
   - Report blocking reasons when not ready
   - Provide warnings for non-blocking issues
   - Estimate total execution duration

---

## Required Enhancements

### 1. ExecutionFailureHandler Enhancement

**File**: `platform/typescript/kernel-lifecycle/src/execution/ExecutionFailureHandler.ts`

**Changes Required**:
- Import `LifecycleFailureClassifier` from `@ghatana/kernel-product-contracts`
- Add `classifyFailure()` method to map errors to failure categories
- Integrate classification into `handleFailure()` method
- Add retryability and human intervention flags from classifier
- Include remediation steps and known workarounds in failure output

**Example Integration**:
```typescript
import { LifecycleFailureClassifier, FAILURE_CATEGORIES, FAILURE_SEVERITIES } from '@ghatana/kernel-product-contracts';

export class ExecutionFailureHandler {
  private failurePolicy: ProductFailurePolicy;

  classifyFailure(error: Error, stepId: string): LifecycleFailureClassifier {
    // Map error patterns to failure categories
    const category = this.determineCategory(error);
    const severity = this.determineSeverity(error);
    const retryable = this.isRetryable(error);
    const requiresHumanIntervention = this.requiresHumanIntervention(error);

    return {
      category,
      severity,
      retryable,
      requiresHumanIntervention,
      remediationSteps: this.getRemediationSteps(error, category),
      relatedFailureCodes: this.getRelatedFailureCodes(category),
      component: this.extractComponent(error, stepId),
      knownWorkaround: this.findKnownWorkaround(error),
    };
  }
}
```

### 2. ProductLifecyclePlanner Enhancement

**File**: `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts`

**Changes Required**:
- Import `PlanExplain`, `DependencyGraph`, `ProviderChecks`, `GateChecks`, `ArtifactExpectations`, `ApprovalPolicy`, `EnvironmentPreflight` from `@ghatana/kernel-product-contracts`
- Add `generatePlanExplain()` method to create `PlanExplain` output
- Build dependency graph from plan steps
- Integrate with provider health matrix
- Include gate evaluation results
- Add artifact expectations from plan
- Include approval policy when required
- Add environment preflight checks

**Example Integration**:
```typescript
import { PlanExplain, DependencyGraph, ProviderChecks, GateChecks } from '@ghatana/kernel-product-contracts';

export class ProductLifecyclePlanner {
  generatePlanExplain(plan: ProductLifecyclePlan, providerHealth: ProviderHealthMatrix): PlanExplain {
    const dependencyGraph = this.buildDependencyGraph(plan);
    const providerChecks = this.buildProviderChecks(providerHealth);
    const gateChecks = this.buildGateChecks(plan);
    const artifactExpectations = this.buildArtifactExpectations(plan);
    const approvalPolicy = this.buildApprovalPolicy(plan);
    const environmentPreflight = this.buildEnvironmentPreflight(plan);

    const overallReadiness = this.determineReadiness(
      dependencyGraph,
      providerChecks,
      gateChecks,
      artifactExpectations,
      environmentPreflight
    );

    return {
      schemaVersion: "1.0.0",
      runId: plan.runId,
      correlationId: plan.correlationId,
      productUnitId: plan.productUnitId,
      phase: plan.phase,
      environment: plan.environment,
      lifecycleProfile: plan.lifecycleProfile,
      generatedAt: new Date().toISOString(),
      dependencyGraph,
      providerChecks,
      gateChecks,
      artifactExpectations,
      approvalPolicy,
      environmentPreflight,
      overallReadiness,
      blockingReasons: overallReadiness === 'not-ready' ? this.getBlockingReasons(...) : undefined,
      estimatedTotalDurationMs: plan.estimatedDurationMs,
      warnings: plan.warnings,
    };
  }
}
```

### 3. Adapter Enhancement

**Location**: `platform/typescript/kernel-toolchains/src/`

**Changes Required**:
- Add `AdapterCapabilityMetadata` to declare adapter capabilities
- Add `AdapterPreflightResult` for environment preflight
- Add `AdapterSafetyPolicy` for safe command execution
- Add `AdapterOutputValidator` for artifact output verification
- Add `AdapterFailureClassification` for adapter-specific failure classification

---

## Current Status

### Completed (Phase 1.5)
- ✅ `LifecycleFailureClassifier` contract defined
- ✅ `PlanExplain` contract with all components defined
- ✅ Conformance tests for all new contracts
- ✅ Exports from kernel-product-contracts

### Pending (Phase 2)
- ⏸️ Integration of `LifecycleFailureClassifier` into `ExecutionFailureHandler`
- ⏸️ Integration of `PlanExplain` generation into `ProductLifecyclePlanner`
- ⏸️ Adapter capability metadata and preflight checks
- ⏸️ Adapter safety policies and output validators
- ⏸️ Adapter-specific failure classification
- ⏸️ Enhanced execution semantics with detailed failure classification

---

## Next Steps

1. **Integrate LifecycleFailureClassifier**: Enhance `ExecutionFailureHandler` to use detailed failure classification
2. **Add PlanExplain Generation**: Enhance `ProductLifecyclePlanner` to generate `PlanExplain` output
3. **Adapter Enhancements**: Add capability metadata, preflight, safety policies, and failure classification to adapters
4. **Validation**: Run `pnpm check:phase2` to validate all enhancements
5. **Product Validation**: Run `pnpm validate:digital-marketing` and `pnpm validate:phr` to validate with real products

---

## Complexity Assessment

Phase 2 involves significant runtime execution changes, not just contract definitions. This requires:
- Deep integration with existing execution infrastructure
- Adapter-level changes across multiple toolchains
- Runtime failure classification logic
- Enhanced planning and explainability

This phase is substantially more complex than Phase 1 (contract definitions) and should be approached incrementally with careful testing at each step.

---

## Conclusion

Phase 1.5 successfully defined the contracts for enhanced failure classification and plan explainability. Phase 2 requires integrating these contracts into the execution infrastructure, which is a significant undertaking involving runtime behavior changes across multiple components. The integration points have been identified, but implementation requires careful planning and incremental development.
