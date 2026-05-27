import { isProductUnit as isProductUnitContract } from "./product-unit/ProductUnit.js";

// Route contracts (K-001)
export type {
  ProductRoute,
  ProductRouteContract,
  RouteStability,
  RouteGroup,
  RouteAction,
  RouteCard,
  RouteMetadata,
} from "./route/ProductRouteContract.js";
export {
  RouteStabilityValues,
  RouteGroupValues,
  isRouteStability,
  isRouteGroup,
  validateProductRouteContract,
} from "./route/ProductRouteContract.js";

// Route contract generator (K-002)
export type {
  GeneratedTSManifest,
  GeneratedBackendEntitlement,
  GeneratedRouteDocs,
} from "./route/RouteContractGenerator.js";
export {
  RouteContractGenerator,
  createRouteContractGenerator,
} from "./route/RouteContractGenerator.js";

// Route lifecycle mode (K-005)
export type {
  RouteLifecycleMode,
  DeprecatedRouteState,
  RouteLifecycleConfig,
} from "./route/RouteLifecycleMode.js";
export {
  createFixForwardLifecycleConfig,
  createLegacyCompatibleLifecycleConfig,
  validateRouteLifecycleConfig,
  isRouteAllowedInLifecycle,
} from "./route/RouteLifecycleMode.js";

// Policy contracts (K-003)
export type {
  PolicyDecision,
  PolicyReasonCode,
  PolicyRequirement,
  ConsentPolicy,
  TreatmentRelationshipPolicy,
  FacilityScopePolicy,
  EmergencyPolicy,
  FchvCommunityPolicy,
  ProductPolicy,
  ProductPolicyEvaluationRequest,
  ProductPolicyEvaluationResult,
} from "./policy/ProductPolicyContract.js";
export {
  createPolicyDecision,
  createDefaultPolicyRequirements,
  isPolicyDecisionAllowed,
  isPolicyDecisionDenied,
} from "./policy/ProductPolicyContract.js";

// Mobile PHI policy contracts (K-004)
export type {
  PhiStoragePolicy,
  PhiCachePolicy,
  PhiFieldPolicy,
  MobilePhiPolicy,
  MobilePhiPolicyCheckRequest,
  MobilePhiPolicyCheckResult,
} from "./policy/MobilePhiPolicyContract.js";
export {
  createDefaultPhiStoragePolicy,
  createDefaultPhiCachePolicy,
  validatePhiStoragePolicy,
  validatePhiCachePolicy,
} from "./policy/MobilePhiPolicyContract.js";

// Correlation contracts (K-006)
export type {
  CorrelationId,
  CorrelationContext,
  CorrelationHeaders,
} from "./correlation/CorrelationContract.js";
export {
  generateCorrelationId,
  createCorrelationContext,
  correlationContextToHeaders,
  headersToCorrelationContext,
  isValidCorrelationId,
} from "./correlation/CorrelationContract.js";

// Action contracts (K-007)
export type {
  HttpMethod,
  ActionVisibility,
  ActionConfirmation,
  ProductAction,
  ProductActionContract,
} from "./action/ProductActionContract.js";
export {
  createAction,
  validateProductAction,
  isActionIdempotent,
  isActionDangerous,
  isActionPhiAccess,
} from "./action/ProductActionContract.js";

// UI state contracts (K-008)
export type {
  UIStateType,
  UIStateRequirement,
  ProductUIState,
  ProductUIStateContract,
} from "./ui/ProductUIStateContract.js";
export {
  createUIStateRequirement,
  createProductUIState,
  validateProductUIState,
  getStateRequirement,
} from "./ui/ProductUIStateContract.js";

// i18n/a11y contracts (K-009)
export type {
  I18nKey,
  I18nEntry,
  A11yLabel,
  A11yRequirement,
  ProductI18nA11yContract,
} from "./i18n/ProductI18nA11yContract.js";
export {
  createI18nEntry,
  createA11yLabel,
  createA11yRequirement,
  validateI18nEntry,
  validateA11yLabel,
  hasI18nKey,
  getI18nEntry,
} from "./i18n/ProductI18nA11yContract.js";

// Artifact cleanup contracts (K-010)
export type {
  CleanupArtifactType,
  ArtifactCleanupMode,
  CleanupArtifactMetadata,
  ArtifactCleanupPolicy,
  ArtifactCleanupResult,
} from "./artifact/ArtifactCleanupContract.js";
export {
  createArtifactCleanupPolicy,
  isArtifactStale,
  isArtifactProtected,
  shouldDeleteArtifact,
  createArtifactCleanupResult,
} from "./artifact/ArtifactCleanupContract.js";

// Existing lifecycle contracts
export type { ProductLifecyclePhase } from "./lifecycle/ProductLifecyclePhase.js";
export type {
  ProductSurface,
  ProductSurfaceType,
  ProductLanguage,
  ProductRuntime,
  ProductBuildSystem,
  JavaConfig,
  TypeScriptConfig,
  RustConfig,
  PythonConfig,
  KotlinConfig,
  SwiftConfig,
} from "./surface/ProductSurface.js";
export {
  ProductSurfaceSchema,
  getValidCombinationsForLanguage,
  getCombinationRecoveryGuidance,
} from "./surface/ProductSurfaceSchema.js";
export type {
  ProductSurfaceInput,
} from "./surface/ProductSurfaceSchema.js";
export type { ProductArtifact } from "./artifact/ProductArtifact.js";
export type { ProductEnvironment } from "./environment/ProductEnvironment.js";
export type { ProductGate } from "./gate/ProductGate.js";
export type { ProductDeployment } from "./deployment/ProductDeployment.js";

// New ProductUnit contracts (explicit re-exports to avoid naming conflicts)
export {
  isProductUnitKind,
  getProductUnitKindLabel,
  isProductUnitSurfaceType,
  isImplementationStatus,
  ProductUnitSchema,
  ProductUnitSourceRefSchema,
  ProductUnitScopeSchema,
  ProductUnitSurfaceSchema,
  ProductUnitDraftSchema,
  PRODUCT_SHAPES,
  isProductShape,
  PRODUCT_UNIT_SOURCE_REF_KINDS,
  isProductUnitSourceRef,
  ProviderRefSchema,
  validateProductUnit,
  validateProductUnitDetailed,
  createMinimalProductUnit,
  createProductUnitDraftSkeleton,
  createExecutableProductUnit,
  ProductUnitIntentApplicationResultSchema,
  ProductUnitIntentSchema,
  ProducerSchema,
  TargetProvidersSchema,
  RequestedLifecycleSchema,
  ProductUnitGovernanceHintsSchema,
  IntentProvenanceSchema,
  isProductUnitIntent,
  validateProductUnitIntent,
  validateProductUnitIntentDetailed,
} from "./product-unit/index.js";
export const isProductUnit = isProductUnitContract;
export type {
  ProductUnitKind,
  ProductShape,
  ProductUnitSourceRef,
  ProductUnitSourceRefKind,
  ProductUnitSurface,
  ProductUnitSurfaceType,
  ImplementationStatus,
  ProductUnit,
  ProductUnitScope,
  ProductUnitDraft,
  ProductUnitConformance,
  ProductUnitGovernance,
  ProductUnitDetailedValidationResult,
  LifecycleStatus,
  ExecutableProductUnit,
  ProviderRef as ProductUnitProviderRef,
  ProductUnitValidationIssue,
  ProductUnitValidationReasonCode,
  ProductUnitValidationSeverity,
  ProductUnitIntent,
  TargetProviders,
  Producer,
  ProducerType,
  ProductUnitIntentType,
  ProductUnitIntentApplyMode,
  ProductUnitIntentStatus,
  ProductUnitIntentApplicationStatus,
  ProductUnitIntentApplicationReasonCode,
  ProductUnitIntentApplicationResult,
  RequestedLifecycle,
  ProductUnitGovernanceHints,
  IntentProvenance,
  ProductUnitIntentDetailedValidationResult,
  ProductUnitIntentValidationIssue,
  ProductUnitIntentValidationReasonCode,
} from "./product-unit/index.js";

export {
  ProductInteractionContractSchema,
  ProductInteractionDeclarationSchema,
  ProductInteractionEvidenceSchema,
  ProductInteractionEvidenceRecordSchema,
  ProductInteractionFailureCodeSchema,
  ProductInteractionModeSchema,
  ProductInteractionOutcomeStatusSchema,
  ProductInteractionPolicySchema,
  isProductInteractionContract,
  parseProductInteractionEvidenceRecord,
  parseProductInteractionContract,
} from "./product-interaction/index.js";
export type {
  ProductInteractionContract,
  ProductInteractionDeclaration,
  ProductInteractionEvidence,
  ProductInteractionEvidenceRecord,
  ProductInteractionFailureCode,
  ProductInteractionMode,
  ProductInteractionOutcomeStatus,
  ProductInteractionPolicy,
} from "./product-interaction/index.js";

// New provider contracts
export {
  ProviderRef,
  KernelProvider,
  RegistryProvider,
  ProductUnitIntentCapableRegistryProvider,
  ProductUnitIntentApplyOptions,
  ProductUnitIntentPreviewResult,
  ProductUnitIntentApplyResult,
  isProductUnitIntentCapableRegistryProvider,
  SourceProvider,
  ToolchainProvider,
  ArtifactProvider,
  ArtifactMetadata,
  DeploymentProvider,
  DeploymentConfig,
  DeploymentResult,
  EnvironmentProvider,
  EnvironmentConfig,
  SecretsProvider,
  TelemetryProvider,
  TelemetryEvent,
  MetricValue,
  ApprovalProvider,
  ApprovalRequest,
  ApprovalDecision,
  HealthProvider,
  HealthCheckResult,
  DeploymentHealthSnapshot as ProviderDeploymentHealthSnapshot,
  GateProvider,
  GateEvaluationRequest,
  GateEvaluationResult,
  KernelProviderMode,
  KERNEL_PROVIDER_MODES,
  KernelProviderModeRequirements,
  KernelLifecycleProviderName,
  KernelLifecycleProviderContextReasonCode,
  KernelLifecycleProviderContextValidationResult,
  LifecycleProviderWriteOptions,
  LifecycleProviderResult,
  LifecycleProviderQuery,
  LifecycleArtifactManifestRefSchema,
  LifecycleHealthSnapshotRefSchema,
  LifecycleProvenanceRecordSchema,
  LifecycleMemoryRecordSchema,
  LifecycleRuntimeTruthSnapshotSchema,
  KernelBridgeProviderHealthResultSchema,
  KernelBridgeProviderErrorSchema,
  LifecycleArtifactManifestRef,
  LifecycleHealthSnapshotRef,
  LifecycleProvenanceRecord,
  LifecycleMemoryRecord,
  LifecycleRuntimeTruthSnapshot,
  KernelBridgeProviderMode,
  KernelBridgeProviderStatus,
  KernelBridgeProviderErrorCode,
  KernelBridgeProviderHealthResult,
  KernelBridgeProviderError,
  LifecycleEventProvider,
  LifecycleArtifactProvider,
  LifecycleHealthProvider,
  LifecycleApprovalProvider,
  LifecycleProvenanceProvider,
  LifecycleMemoryProvider,
  LifecycleRuntimeTruthProvider,
  KernelEventProvider,
  KernelArtifactProvider,
  KernelHealthProvider,
  KernelProvenanceProvider,
  KernelTelemetryProvider,
  KernelPolicyEvidenceProvider,
  KernelRuntimeTruthProvider,
  KernelLifecycleProviderContext,
  isKernelProviderMode,
  requireLifecycleProvider,
  requireLifecycleProviderSet,
  validateKernelLifecycleProviderContext,
} from "./provider/index.js";

// KernelProviderHealthMatrix contracts
export {
  KernelProviderHealthMatrix,
  ProviderCapability,
  ProviderCapabilityDeclaration,
  ProviderHealthEntry,
  ProviderHealthStatus,
  parseKernelProviderHealthMatrix,
  isKernelProviderHealthMatrix,
} from "./provider/KernelProviderHealthMatrix.js";

// New lifecycle contracts
export {
  KernelEventMetadata,
  KernelLifecycleEvent,
  KernelLifecycleEventType,
  KernelLifecycleEventPayload,
  KERNEL_EVENT_SCHEMA_VERSION,
  KERNEL_LIFECYCLE_EVENT_TYPES,
  KernelLifecycleEventSchema,
  KernelEventMetadataSchema,
  KernelLifecycleEventValidationResult,
  isKernelLifecycleEvent,
  validateKernelLifecycleEvent,
  KernelGateEvent,
  GateEventPayload,
  KernelArtifactEvent,
  ArtifactEventPayload,
  KernelDeploymentEvent,
  DeploymentEventPayload,
  KernelHealthEvent,
  HealthEventPayload,
  KernelAgentGovernanceEvent,
  AgentGovernanceEventPayload,
  KernelPreviewSecurityEvent,
  PreviewSecurityEventPayload,
} from "./events/index.js";

// Enhanced lifecycle failure model
export {
  LifecycleFailureClassifier,
  LifecycleFailureClassifierSchema,
  FAILURE_CATEGORIES,
  FAILURE_SEVERITIES,
  DependencyGraph,
  DependencyGraphSchema,
  DependencyGraphNode,
  DependencyGraphNodeSchema,
  ProviderCheck,
  ProviderCheckSchema,
  ProviderChecks,
  ProviderChecksSchema,
  GateCheck,
  GateCheckSchema,
  GateChecks,
  GateChecksSchema,
  ArtifactExpectation,
  ArtifactExpectationSchema,
  ArtifactExpectations,
  ArtifactExpectationsSchema,
  ApprovalPolicy,
  ApprovalPolicySchema,
  ApprovalStatus,
  ApprovalStatusSchema,
  EnvironmentPreflight,
  EnvironmentPreflightSchema,
  EnvironmentPreflightCheck,
  EnvironmentPreflightCheckSchema,
  PlanExplain,
  PlanExplainSchema,
  parsePlanExplain,
  isPlanExplain,
} from "./lifecycle/LifecycleContracts.js";

// New health snapshot contracts
export {
  HealthStatus,
  ProductUnitHealthSnapshot,
  SurfaceHealthStatus,
  LifecycleHealthSnapshot,
  PhaseHealthStatus,
  GateHealthSnapshot,
  GateEvaluationStatus,
  ArtifactHealthSnapshot,
  ArtifactHealthStatus,
  DeploymentHealthSnapshot,
  DeploymentHealthStatus,
  PluginHealthSnapshot,
  PluginHealthStatus,
  AgentGovernanceHealthSnapshot,
  AgentGovernanceStatus,
  GovernanceState,
  LearningHealthSnapshot,
  LearningDeltaStatus,
  PreviewSecurityHealthSnapshot,
  SecurityCheckStatus,
  KernelHealthStatusSchema,
  KernelLifecycleHealthSnapshotSchema,
  KernelProductHealthSnapshotSchema,
  KernelProviderHealthSnapshotSchema,
  KernelGateHealthSnapshotSchema,
  KernelDeploymentHealthSnapshotSchema,
  KernelHealthSnapshotSchema,
  type KernelHealthStatus,
  type KernelLifecycleHealthSnapshot,
  type KernelProductHealthSnapshot,
  type KernelProviderHealthSnapshot,
  type KernelGateHealthSnapshot,
  type KernelDeploymentHealthSnapshot,
  type KernelHealthSnapshot,
} from "./health/index.js";

// HealthSnapshot is the canonical alias for LifecycleHealthSnapshot
export type { LifecycleHealthSnapshot as HealthSnapshot } from "./health/LifecycleHealthSnapshot.js";

export {
  ProvenanceReferenceSchema,
  ProvenanceSubjectSchema,
  redactEvidenceRef,
  redactProvenanceReference,
  type EvidenceRedactionOptions,
  type ProvenanceReference,
  type ProvenanceSubject,
} from "./provenance/index.js";

export {
  ScopedRuntimeTruthIndex,
  type RuntimeTruthScope,
  type RuntimeTruthQueryIndex,
} from "./runtime-truth/index.js";

// New plugin contracts
export {
  PluginKind,
  KernelPlugin,
  PluginExecutionContext,
  PluginExecutionResult,
  PluginRef,
  KernelPluginCapability,
  KernelPluginBinding,
  KernelPluginHealthSnapshot,
  KernelPluginLifecycleHook,
  KernelPluginGateResult,
  PluginBindingCondition,
  GateEvidence,
  isKernelPluginCapability,
  isKernelPluginBinding,
  isKernelPluginHealthSnapshot,
  isKernelPluginLifecycleHook,
  getLifecycleHookLabel,
  isKernelPluginGateResult,
} from "./plugin/index.js";

// Artifact intelligence contracts
export {
  ARTIFACT_INTELLIGENCE_SCHEMA_VERSION,
  ARTIFACT_KINDS,
  PRODUCT_SHAPE_KINDS,
  LIFECYCLE_READINESS_STATES,
  RISK_LEVELS,
  EVIDENCE_TYPES,
  ArtifactIntelligenceEvidenceBaseSchema,
  SemanticArtifactReferenceSchema,
  ArtifactGraphNodeSchema,
  ArtifactGraphEdgeSchema,
  ArtifactGraphSummarySchema,
  ProductShapeEvidenceSchema,
  DependencyGraphEvidenceSchema,
  ResidualIslandReportSchema,
  RiskHotspotReportSchema,
  GeneratedChangeSetSummarySchema,
  SemanticArtifactEvidenceBundleSchema,
  ArtifactIntelligenceEvidenceEnvelopeSchema,
  ArtifactEvidenceEnvelopeSchema,
  isSemanticArtifactReference,
  isArtifactGraphSummary,
  isProductShapeEvidence,
  isDependencyGraphEvidence,
  isResidualIslandReport,
  isRiskHotspotReport,
  isGeneratedChangeSetSummary,
  isSemanticArtifactEvidenceBundle,
  isArtifactIntelligenceEvidenceEnvelope,
  isArtifactEvidenceEnvelope,
  type ArtifactKind,
  type ProductShapeKind,
  type LifecycleReadinessState,
  type RiskLevel,
  type PrivacyClassification,
  type EvidenceType,
  type ArtifactIntelligenceEvidenceBase,
  type SemanticArtifactReference,
  type ArtifactGraphNode,
  type ArtifactGraphEdge,
  type ArtifactGraphSummary,
  type ProductShapeEvidence,
  type DependencyGraphEvidence,
  type ResidualIslandReport,
  type RiskHotspotReport,
  type GeneratedChangeSetSummary,
  type SemanticArtifactEvidenceBundle,
  type ArtifactIntelligenceEvidenceEnvelope,
  type ArtifactEvidenceEnvelope,
} from "./artifact-intelligence/index.js";

// Agentic lifecycle contracts
export {
  AgentLifecycleActionRequestSchema,
  AgentLifecycleActionRequestValidationError,
  AgentLifecycleMasteryEvidenceSchema,
  AgentLifecyclePolicyDecisionEvidenceSchema,
  AgentLifecycleApprovalRequirementSchema,
  AgentLifecycleToolPermissionSchema,
  AgentLifecycleVerificationRequirementSchema,
  AgentLifecycleActionResultSchema,
  AgentLifecycleActionFailureSchema,
  isAgentLifecycleActionRequest,
  isAgentLifecycleActionResult,
  parseAgentLifecycleActionRequest,
  type AgentLifecycleActionRequest,
  type AgentLifecycleActionRequestReasonCode,
  type AgentLifecycleActionRequestValidationIssue,
  type AgentLifecycleApprovalRequirement,
  type AgentLifecycleFallbackMode,
  type AgentLifecycleMasteryEvidence,
  type AgentLifecycleMasteryState,
  type AgentLifecyclePolicyDecision,
  type AgentLifecyclePolicyDecisionEvidence,
  type AgentLifecycleRequestedAction,
  type AgentLifecycleRiskLevel,
  type AgentLifecycleToolPermission,
  type AgentLifecycleVerificationRequirement,
  type AgentLifecycleActionResult,
  type AgentLifecycleActionFailure,
  type AgentLifecycleApprovalDecision,
  type AgentLifecycleDecision,
  type AgentLifecycleHealthStatus,
  type AgentLifecycleRequiredNextAction,
  type AgentLifecycleRollbackReadiness,
} from "./agentic/index.js";

// Lifecycle plan, execution, and result contracts (§2.2)
export {
  createLifecycleRunId,
  createLifecycleCorrelationId,
  LIFECYCLE_RUN_STATUSES,
  LIFECYCLE_FAILURE_REASON_CODES,
  LifecycleRunStatusSchema,
  LifecycleProfileSchema,
  parseLifecycleProfile,
  LifecyclePlanStepSchema,
  LifecyclePlanSchema,
  parseLifecyclePlan,
  LifecycleExecutionRequestSchema,
  parseLifecycleExecutionRequest,
  LifecycleStepResultSchema,
  LifecycleExecutionResultSchema,
  LifecycleFailureSchema,
  LifecycleResultSchema,
  LifecycleExecutionContextSchema,
  parseLifecycleExecutionResult,
  type LifecycleRunId,
  type LifecycleCorrelationId,
  type LifecycleRunStatus,
  type LifecycleProfile,
  type LifecyclePlanStep,
  type LifecyclePlan,
  type LifecycleExecutionRequest,
  type LifecycleExecutionContext,
  type LifecycleStepResult,
  type LifecycleExecutionResult,
  type LifecycleResult,
  type LifecycleFailure,
  type LifecycleFailureReasonCode,
} from "./lifecycle/LifecycleContracts.js";

// Canonical alias: LifecyclePhase = ProductLifecyclePhase
// Re-exported for convenience and spec alignment
export type { ProductLifecyclePhase as LifecyclePhase } from "./lifecycle/ProductLifecyclePhase.js";

// Gate definition, result manifest, and reference contracts (§2.2)
export {
  GATE_KINDS,
  GateDefinitionSchema,
  RequiredGateReferenceSchema,
  GateResultEntrySchema,
  GateResultSchema,
  GateResultManifestSchema,
  ApprovalRequirementSchema,
  parseGateResultManifest,
  type GateKind,
  type GateDefinition,
  type GateFailureReason,
  type RequiredGateReference,
  type GateResultEntry,
  type GateResult,
  type GateResultManifest,
  type ApprovalRequirement,
} from "./gate/GateContracts.js";

// Artifact reference and fingerprint contracts (§2.2)
export {
  DIGEST_ALGORITHMS,
  ArtifactDigestSchema,
  ArtifactFingerprintSchema,
  ARTIFACT_TYPES,
  LifecycleArtifactReferenceSchema,
  ArtifactManifestReferenceSchema,
  parseArtifactManifestReference,
  type DigestAlgorithm,
  type ArtifactDigest,
  type ArtifactFingerprint,
  type ArtifactType,
  type LifecycleArtifactReference,
  type ArtifactManifestReference,
} from "./artifact/ArtifactReferences.js";

// ArtifactReference is the canonical alias for LifecycleArtifactReference
export type { LifecycleArtifactReference as ArtifactReference } from "./artifact/ArtifactReferences.js";

// Deployment, environment, health report, and rollback reference contracts (§2.2)
export {
  EnvironmentReferenceSchema,
  DeploymentReferenceSchema,
  DeploymentManifestReferenceSchema,
  parseDeploymentManifestReference,
  VerifyHealthReportReferenceSchema,
  parseVerifyHealthReportReference,
  RollbackManifestReferenceSchema,
  parseRollbackManifestReference,
  type EnvironmentReference,
  type DeploymentReference,
  type DeploymentManifestReference,
  type VerifyHealthReportReference,
  type RollbackManifestReference,
} from "./deployment/DeploymentReferences.js";

// Lifecycle event envelope contracts (§2.2)
export {
  LifecycleEventEnvelopeSchema,
  parseLifecycleEventEnvelope,
  type LifecycleEventType,
  type LifecycleEventEnvelope,
} from "./events/LifecycleEventEnvelope.js";

// Agent lifecycle action evidence contracts (§2.2)
export {
  AGENT_EVIDENCE_KINDS,
  AgentLifecycleActionEvidenceSchema,
  parseAgentLifecycleActionEvidence,
  type AgentEvidenceKind,
  type AgentLifecycleActionEvidence,
} from "./agentic/AgentLifecycleActionEvidence.js";

// UI-facing lifecycle summary contracts (§2.6)
export {
  LifecycleGateSummarySchema,
  LifecycleArtifactSummarySchema,
  LifecycleDeploymentSummarySchema,
  LifecycleHealthSummarySchema,
  LifecycleRecoveryHintSchema,
  LifecycleRunSummarySchema,
  LifecycleUserSummarySchema,
  parseLifecycleRunSummary,
  parseLifecycleUserSummary,
  type LifecycleGateSummary,
  type LifecycleArtifactSummary,
  type LifecycleDeploymentSummary,
  type LifecycleHealthSummary,
  type LifecycleRecoveryHint,
  type LifecycleRunSummary,
  type LifecycleUserSummary,
} from "./ui-summary/LifecycleSummaries.js";
