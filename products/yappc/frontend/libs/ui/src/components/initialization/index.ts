/**
 * Initialization Components
 *
 * @description Components for Phase 2 - Project initialization, configuration,
 * and deployment setup flows.
 *
 * @doc.module initialization
 * @doc.phase 2
 */

// ConfigurationWizard - Main multi-step wizard container
export {
  ConfigurationWizard,
  WizardContext,
  useWizard,
  type WizardStepDefinition,
  type WizardStepState,
  type WizardContextValue,
  type StepValidation,
  type ConfigurationWizardProps,
} from './ConfigurationWizard';

// StepProgress - Step progress indicator
export {
  StepProgress,
  type WizardStep,
  type StepStatus,
  type StepProgressProps,
} from './StepProgress';

// PresetCard - Quick-start preset selection
export {
  PresetCard,
  type PresetCategory,
  type TechStack,
  type ResourceEstimate,
  type InitializationPreset,
  type PresetCardProps,
} from './PresetCard';

// EnvironmentTabs - Environment tab navigation
export {
  EnvironmentTabs,
  type EnvironmentStatus,
  type EnvironmentType,
  type Environment,
  type EnvironmentTabsProps,
} from './EnvironmentTabs';

// ResourcesList - Provisioned resources display
export {
  ResourcesList,
  type ResourceType,
  type ResourceStatus,
  type ResourceProvider,
  type Resource,
  type ResourcesListProps,
} from './ResourcesList';

// RollbackConfirmDialog - Rollback confirmation dialog
export {
  RollbackConfirmDialog,
  type RollbackStep,
  type AffectedResource,
  type RollbackConfirmDialogProps,
} from './RollbackConfirmDialog';

// CostEstimator - Cost estimation display
export {
  CostEstimator,
  type CostCategory,
  type CostBreakdownItem,
  type ProviderCost,
  type EnvironmentCost,
  type CostOptimization,
  type CostEstimates,
  type CostEstimatorProps,
} from './CostEstimator';

// ProviderSelector - Provider selection component
export {
  ProviderSelector,
  type ProviderCategory,
  type PricingTier,
  type ProviderFeature,
  type Provider,
  type ProviderSelectorProps,
} from './ProviderSelector';

// InfrastructureForm - Infrastructure configuration form
export {
  InfrastructureForm,
  type CloudProvider,
  type DatabaseType,
  type DeployEnvironment,
  type Region,
  type ScalingOption,
  type FieldError,
  type InfrastructureValues,
  type InfrastructureFormProps,
} from './InfrastructureForm';

// LiveProgressViewer - Real-time progress display
export {
  LiveProgressViewer,
  type ProgressStepStatus,
  type LogLevel,
  type LogEntry,
  type ProgressStep,
  type ProgressSummary,
  type LiveProgressViewerProps,
} from './LiveProgressViewer';
