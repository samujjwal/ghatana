# Kernel Product Contracts Inventory

> **Last Updated:** 2026-04-20
> **Purpose:** Complete inventory of all exported contract types from `@ghatana/kernel-product-contracts`

## Overview

This document provides a complete inventory of all exported contract types from the `@ghatana/kernel-product-contracts` package. This inventory is used to ensure every public contract has TypeScript types, Zod schemas, and parse functions as required by Phase 2, Workstream 2.1.

## Existing Lifecycle Contracts

These are the original lifecycle contracts that were already in place:

1. **ProductLifecyclePhase** - `./lifecycle/ProductLifecyclePhase.js`
2. **ProductSurface** - `./surface/ProductSurface.js`
3. **ProductSurfaceType** - `./surface/ProductSurface.js`
4. **ProductArtifact** - `./artifact/ProductArtifact.js`
5. **ProductEnvironment** - `./environment/ProductEnvironment.js`
6. **ProductGate** - `./gate/ProductGate.js`
7. **ProductDeployment** - `./deployment/ProductDeployment.js`

## ProductUnit Contracts (New)

These contracts define product units and their lifecycle:

### Types
- ProductUnitKind
- ProductUnitSurface
- ProductUnitSurfaceType
- ImplementationStatus
- ProductUnit
- ProductUnitScope
- ProductUnitDraft
- ProductUnitConformance
- ProductUnitGovernance
- ProductUnitDetailedValidationResult
- LifecycleStatus
- ExecutableProductUnit
- ProductUnitProviderRef (alias for ProviderRef)
- ProductUnitValidationIssue
- ProductUnitValidationReasonCode
- ProductUnitValidationSeverity
- ProductUnitIntent
- TargetProviders
- Producer
- ProducerType
- ProductUnitIntentType
- ProductUnitIntentApplyMode
- ProductUnitIntentStatus
- ProductUnitIntentApplicationStatus
- ProductUnitIntentApplicationReasonCode
- ProductUnitIntentApplicationResult
- RequestedLifecycle
- ProductUnitGovernanceHints
- IntentProvenance
- ProductUnitIntentDetailedValidationResult
- ProductUnitIntentValidationIssue
- ProductUnitIntentValidationReasonCode

### Schemas
- ProductUnitSchema
- ProductUnitScopeSchema
- ProductUnitSurfaceSchema
- ProductUnitDraftSchema
- ProviderRefSchema
- ProductUnitIntentApplicationResultSchema
- ProductUnitIntentSchema
- ProducerSchema
- TargetProvidersSchema
- RequestedLifecycleSchema
- ProductUnitGovernanceHintsSchema
- IntentProvenanceSchema

### Functions
- isProductUnitKind
- getProductUnitKindLabel
- isProductUnitSurfaceType
- isImplementationStatus
- isProductUnit
- validateProductUnit
- validateProductUnitDetailed
- createMinimalProductUnit
- createProductUnitDraftSkeleton
- createExecutableProductUnit
- isProductUnitIntent
- validateProductUnitIntent
- validateProductUnitIntentDetailed

## Provider Contracts (New)

These contracts define kernel providers and their interfaces:

### Types
- ProviderRef
- KernelProvider
- RegistryProvider
- ProductUnitIntentCapableRegistryProvider
- SourceProvider
- ToolchainProvider
- ArtifactProvider
- ArtifactMetadata
- DeploymentProvider
- DeploymentConfig
- DeploymentResult
- EnvironmentProvider
- EnvironmentConfig
- SecretsProvider
- TelemetryProvider
- TelemetryEvent
- MetricValue
- ApprovalProvider
- ApprovalRequest
- ApprovalDecision
- HealthProvider
- HealthCheckResult
- ProviderDeploymentHealthSnapshot (alias for DeploymentHealthSnapshot)
- GateProvider
- GateEvaluationRequest
- GateEvaluationResult
- KernelProviderMode
- KernelProviderModeRequirements
- KernelLifecycleProviderName
- KernelLifecycleProviderContextReasonCode
- KernelLifecycleProviderContextValidationResult
- LifecycleProviderWriteOptions
- LifecycleProviderResult
- LifecycleProviderQuery
- LifecycleArtifactManifestRef
- LifecycleHealthSnapshotRef
- LifecycleProvenanceRecord
- LifecycleMemoryRecord
- LifecycleRuntimeTruthSnapshot
- KernelLifecycleProviderContext

### Constants
- KERNEL_PROVIDER_MODES

### Functions
- isProductUnitIntentCapableRegistryProvider
- isKernelProviderMode
- requireLifecycleProvider
- requireLifecycleProviderSet
- validateKernelLifecycleProviderContext

### Provider Interfaces
- ProductUnitIntentApplyOptions
- ProductUnitIntentPreviewResult
- ProductUnitIntentApplyResult
- LifecycleEventProvider
- LifecycleArtifactProvider
- LifecycleHealthProvider
- LifecycleApprovalProvider
- LifecycleProvenanceProvider
- LifecycleMemoryProvider
- LifecycleRuntimeTruthProvider

## Event Contracts (New)

These contracts define kernel lifecycle events:

### Types
- KernelEventMetadata
- KernelLifecycleEvent
- KernelLifecycleEventType
- KernelLifecycleEventPayload
- KernelGateEvent
- GateEventPayload
- KernelArtifactEvent
- ArtifactEventPayload
- KernelDeploymentEvent
- DeploymentEventPayload
- KernelHealthEvent
- HealthEventPayload
- KernelAgentGovernanceEvent
- AgentGovernanceEventPayload
- KernelPreviewSecurityEvent
- PreviewSecurityEventPayload

### Schemas
- KernelLifecycleEventSchema
- KernelEventMetadataSchema

### Constants
- KERNEL_EVENT_SCHEMA_VERSION
- KERNEL_LIFECYCLE_EVENT_TYPES

### Types
- KernelLifecycleEventValidationResult

### Functions
- isKernelLifecycleEvent
- validateKernelLifecycleEvent

## Health Snapshot Contracts (New)

These contracts define health snapshot structures:

### Types
- HealthStatus
- ProductUnitHealthSnapshot
- SurfaceHealthStatus
- LifecycleHealthSnapshot
- PhaseHealthStatus
- GateHealthSnapshot
- GateEvaluationStatus
- ArtifactHealthSnapshot
- ArtifactHealthStatus
- DeploymentHealthSnapshot
- DeploymentHealthStatus
- PluginHealthSnapshot
- PluginHealthStatus
- AgentGovernanceHealthSnapshot
- AgentGovernanceStatus
- GovernanceState
- LearningHealthSnapshot
- LearningDeltaStatus
- PreviewSecurityHealthSnapshot
- SecurityCheckStatus

## Plugin Contracts (New)

These contracts define kernel plugins:

### Types
- PluginKind
- KernelPlugin
- PluginExecutionContext
- PluginExecutionResult
- PluginRef
- KernelPluginCapability
- KernelPluginBinding
- KernelPluginHealthSnapshot
- KernelPluginLifecycleHook
- KernelPluginGateResult
- PluginBindingCondition
- GateEvidence

### Functions
- isKernelPluginCapability
- isKernelPluginBinding
- isKernelPluginHealthSnapshot
- isKernelPluginLifecycleHook
- getLifecycleHookLabel
- isKernelPluginGateResult

## Artifact Intelligence Contracts (New)

These contracts define artifact intelligence and semantic analysis:

### Constants
- ARTIFACT_INTELLIGENCE_SCHEMA_VERSION
- ARTIFACT_KINDS
- PRODUCT_SHAPE_KINDS
- LIFECYCLE_READINESS_STATES
- RISK_LEVELS

### Types
- ArtifactKind
- ProductShapeKind
- LifecycleReadinessState
- RiskLevel
- ArtifactIntelligenceEvidenceBase
- SemanticArtifactReference
- ArtifactGraphNode
- ArtifactGraphEdge
- ArtifactGraphSummary
- ProductShapeEvidence
- DependencyGraphEvidence
- ResidualIslandReport
- RiskHotspotReport
- GeneratedChangeSetSummary

### Schemas
- ArtifactIntelligenceEvidenceBaseSchema
- SemanticArtifactReferenceSchema
- ArtifactGraphNodeSchema
- ArtifactGraphEdgeSchema
- ArtifactGraphSummarySchema
- ProductShapeEvidenceSchema
- DependencyGraphEvidenceSchema
- ResidualIslandReportSchema
- RiskHotspotReportSchema
- GeneratedChangeSetSummarySchema

### Functions
- isSemanticArtifactReference
- isArtifactGraphSummary
- isProductShapeEvidence
- isDependencyGraphEvidence
- isResidualIslandReport
- isRiskHotspotReport
- isGeneratedChangeSetSummary

## Agentic Lifecycle Contracts (New)

These contracts define agentic lifecycle actions:

### Schemas
- AgentLifecycleActionRequestSchema
- AgentLifecycleActionRequestValidationError
- AgentLifecycleApprovalRequirementSchema
- AgentLifecycleVerificationRequirementSchema
- AgentLifecycleActionResultSchema
- AgentLifecycleActionFailureSchema

### Types
- AgentLifecycleActionRequest
- AgentLifecycleActionRequestReasonCode
- AgentLifecycleActionRequestValidationIssue
- AgentLifecycleApprovalRequirement
- AgentLifecycleRequestedAction
- AgentLifecycleRiskLevel
- AgentLifecycleVerificationRequirement
- AgentLifecycleActionResult
- AgentLifecycleActionFailure
- AgentLifecycleApprovalDecision
- AgentLifecycleDecision
- AgentLifecycleHealthStatus
- AgentLifecycleRequiredNextAction
- AgentLifecycleRollbackReadiness

### Functions
- isAgentLifecycleActionRequest
- isAgentLifecycleActionResult
- parseAgentLifecycleActionRequest

## Summary

Total contract types exported: **100+**

**Phase 2, Workstream 2.1 Task Status:**
- ✅ Task 1: Inventory all exported contract types - **COMPLETE**
- ⏳ Task 2: Ensure every public contract has TypeScript type, Zod schema, parse function - **PENDING**
- ⏳ Task 3: Normalize and validate all contract types with discriminated unions - **PENDING**
- ⏳ Task 4: Add migration/version policy fields to contracts - **PENDING**
- ⏳ Task 5: Add contract test fixtures for valid and negative cases - **PENDING**
