export * from "./domain/ProductLifecyclePhase.js";

export { ProductLifecyclePlanner } from "./planning/ProductLifecyclePlanner.js";
export { SurfaceSelector } from "./planning/SurfaceSelector.js";
export { LifecycleProfileResolver } from "./planning/LifecycleProfileResolver.js";
export { GateResolver } from "./planning/GateResolver.js";
export { ArtifactResolver } from "./planning/ArtifactResolver.js";
export { EnvironmentResolver } from "./planning/EnvironmentResolver.js";
export { ToolchainResolver } from "./planning/ToolchainResolver.js";

export { ProductLifecycleExecutor } from "./execution/ProductLifecycleExecutor.js";
export {
  ProductLifecycleStepRunner,
  AdapterRegistry,
  Adapter,
  AdapterResult,
  AdapterArtifact,
  AdapterContext,
  StepContext,
} from "./execution/ProductLifecycleStepRunner.js";
export {
  ConsoleExecutionLogger,
  FileExecutionLogger,
} from "./execution/ExecutionLogger.js";
export {
  ExecutionFailureHandler,
  FailureHandlingResult,
} from "./execution/ExecutionFailureHandler.js";
export { ExecutionResultCollector } from "./execution/ExecutionResultCollector.js";
export {
  AgentLifecycleActionService,
  type AgentLifecycleActionChecks,
  type AgentLifecycleActionExecutor,
  type AgentLifecycleActionPlanner,
  type AgentLifecycleActionServiceOptions,
} from "./agentic/AgentLifecycleActionService.js";

export {
  LifecycleManifestWriter,
  type LifecycleManifestWriterOptions,
  type LifecycleManifestWriteRequest,
  type LifecycleManifestWriteResult,
} from "./manifest/LifecycleManifestWriter.js";
export {
  GateExecutor,
  type GateExecutorOptions,
  type GateExecutionRequest,
  type GateExecutionResult,
} from "./gates/GateExecutor.js";
export {
  requireLifecycleContextProvider,
  requireBootstrapLifecycleContext,
  requirePlatformLifecycleContext,
  validateLifecycleProviderContext,
  type LifecycleProviderContext,
  type LifecycleProviderName,
} from "./providers/LifecycleProviderContext.js";

export {
  KernelLifecycleService,
  type ApprovalResult,
  type CreateLifecyclePlanOptions,
  type ExecuteLifecyclePlanOptions,
  type KernelLifecycleLogger,
  type KernelLifecycleScopeQuery,
  type KernelLifecycleServiceOptions,
  type LifecycleRunQuery,
  type LifecycleRunSummary,
  type RunLifecyclePhaseOptions,
} from "./service/KernelLifecycleService.js";
export {
  ManifestPointerStore,
  type ManifestPointerStoreOptions,
  type ManifestPointers,
} from "./service/ManifestPointerStore.js";
export {
  ApprovalRequiredError,
  ArtifactMissingError,
  ExecutionFailedError,
  GateFailedError,
  KernelLifecycleError,
  LifecycleNotEnabledError,
  ManifestNotFoundError,
  ProductUnitNotFoundError,
  ProviderUnavailableError,
  type KernelLifecycleErrorOptions,
} from "./service/KernelLifecycleErrors.js";
export {
  FAILURE_REASON_CODES,
  KernelLifecycleApiHandlers,
  LIFECYCLE_RUN_STATUSES,
  PRODUCT_LIFECYCLE_PHASES,
  createKernelLifecycleApiHandlers,
  type KernelLifecycleApiHandlersOptions,
  type KernelLifecycleApiRequest,
  type KernelLifecycleApiResponse,
  type KernelLifecycleRouteMetadata,
} from "./api/KernelLifecycleApiHandlers.js";

export { CanonicalRegistryLoader } from "./io/CanonicalRegistryLoader.js";
export { LifecycleProfileLoader } from "./io/LifecycleProfileLoader.js";
export { ToolchainAdapterRegistryLoader } from "./io/ToolchainAdapterRegistryLoader.js";
export { EnvironmentLoader } from "./io/EnvironmentLoader.js";
export { PlanWriter } from "./io/PlanWriter.js";
export { ResultWriter } from "./io/ResultWriter.js";
export { ArtifactWriter, ArtifactManifest } from "./io/ArtifactWriter.js";
export {
  LifecycleTruthWriter,
  FileLifecycleTruthWriter,
  LifecycleTruthWriteResult,
  LifecycleTruthWriteStatus,
  CANONICAL_TRUTH_FILE_NAMES,
  CanonicalTruthFileName,
} from "./io/LifecycleTruthWriter.js";

export { ProductLifecycleContractValidator } from "./validation/ProductLifecycleContractValidator.js";
export { ProductRegistryLifecycleValidator } from "./validation/ProductRegistryLifecycleValidator.js";
export { ProductSurfaceValidator } from "./validation/ProductSurfaceValidator.js";
export { ProductEnvironmentValidator } from "./validation/ProductEnvironmentValidator.js";
export { ProductArtifactValidator } from "./validation/ProductArtifactValidator.js";
export { ProductGateValidator } from "./validation/ProductGateValidator.js";

export { SchemaValidator } from "./SchemaValidator.js";
