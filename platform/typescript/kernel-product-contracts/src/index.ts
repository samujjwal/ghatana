// Existing lifecycle contracts
export type { ProductLifecyclePhase } from './lifecycle/ProductLifecyclePhase.js';
export type { ProductSurface, ProductSurfaceType } from './surface/ProductSurface.js';
export type { ProductArtifact } from './artifact/ProductArtifact.js';
export type { ProductEnvironment } from './environment/ProductEnvironment.js';
export type { ProductGate } from './gate/ProductGate.js';
export type { ProductDeployment } from './deployment/ProductDeployment.js';

// New ProductUnit contracts (explicit re-exports to avoid naming conflicts)
export {
  ProductUnitKind,
  isProductUnitKind,
  getProductUnitKindLabel,
  ProductUnitSurface,
  ProductUnitSurfaceType,
  isProductUnitSurfaceType,
  ImplementationStatus,
  isImplementationStatus,
  ProductUnit,
  ProductUnitConformance,
  ProductUnitGovernance,
  LifecycleStatus,
  ProviderRef as ProductUnitProviderRef,
  isProductUnit,
  createMinimalProductUnit,
  ProductUnitIntent,
  ProductUnitDraft,
  TargetProviders,
  Producer,
  ProducerType,
  RequestedLifecycle,
  isProductUnitIntent,
} from './product-unit/index.js';

// New provider contracts
export {
  ProviderRef,
  KernelProvider,
  RegistryProvider,
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
} from './provider/index.js';

// New event contracts
export {
  KernelEventMetadata,
  KernelLifecycleEvent,
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
} from './events/index.js';

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
} from './health/index.js';

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
} from './plugin/index.js';
