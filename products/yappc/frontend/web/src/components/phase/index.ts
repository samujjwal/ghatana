/**
 * Phase Cockpit Components
 *
 * Reusable components for phase-specific cockpits with consistent structure
 * and low cognitive load.
 *
 * @doc.type module
 * @doc.purpose Phase cockpit component exports
 * @doc.layer product
 */

export { PhaseCockpitLayout } from './PhaseCockpitLayout';
export type { PhaseCockpitLayoutProps } from './PhaseCockpitLayout';

export { PhasePrimaryActionCard } from './PhasePrimaryActionCard';
export type { PhasePrimaryActionCardProps } from './PhasePrimaryActionCard';

export { PhaseBlockerPanel } from './PhaseBlockerPanel';
export type { PhaseBlockerPanelProps, Blocker } from './PhaseBlockerPanel';

export { PhaseEvidencePanel } from './PhaseEvidencePanel';
export type { PhaseEvidencePanelProps, EvidenceItem } from './PhaseEvidencePanel';

export { PhaseSuggestedNextStep } from './PhaseSuggestedNextStep';
export type { PhaseSuggestedNextStepProps, SuggestedStep } from './PhaseSuggestedNextStep';

export { PhaseAdvancedDisclosure } from './PhaseAdvancedDisclosure';
export type { PhaseAdvancedDisclosureProps, AdvancedSection } from './PhaseAdvancedDisclosure';

export { PhaseGovernanceTrace } from './PhaseGovernanceTrace';
export type { PhaseGovernanceTraceProps, GovernanceRecord } from './PhaseGovernanceTrace';

// Phase-specific cockpits
export { IntentCockpit } from './IntentCockpit';
export type { IntentCockpitProps } from './IntentCockpit';

export { ShapeCockpit } from './ShapeCockpit';
export type { ShapeCockpitProps } from './ShapeCockpit';

export { ValidateCockpit } from './ValidateCockpit';
export type { ValidateCockpitProps } from './ValidateCockpit';

export { GenerateCockpit } from './GenerateCockpit';
export type { GenerateCockpitProps } from './GenerateCockpit';

export { RunCockpit } from './RunCockpit';
export type { RunCockpitProps } from './RunCockpit';

export { ObserveCockpit } from './ObserveCockpit';
export type { ObserveCockpitProps } from './ObserveCockpit';

export { LearnCockpit } from './LearnCockpit';
export type { LearnCockpitProps } from './LearnCockpit';

export { EvolveCockpit } from './EvolveCockpit';
export type { EvolveCockpitProps } from './EvolveCockpit';

// Phase-native detail panels
export { ValidateApprovalPanel } from './ValidateApprovalPanel';
export type {
  ValidateApprovalPanelProps,
  ApprovalDecision,
  ApprovalReviewer,
  ApprovalGate,
} from './ValidateApprovalPanel';

export { GeneratePackagePanel } from './GeneratePackagePanel';
export type {
  GeneratePackagePanelProps,
  GenerationStatus,
  OutputFile,
  ResidualIsland,
} from './GeneratePackagePanel';

export { RunReadinessPanel } from './RunReadinessPanel';
export type {
  RunReadinessPanelProps,
  DeploymentMode,
  GateStatus,
  CapabilityGate,
  DeploymentStep,
} from './RunReadinessPanel';

export { ObserveSignalsPanel } from './ObserveSignalsPanel';
export type {
  ObserveSignalsPanelProps,
  HealthStatus,
  MetricTrend,
  SignalMetric,
  IncidentSeverity,
  IncidentStatus,
  Incident,
  TraceLink,
} from './ObserveSignalsPanel';

export { LearnRetrospectivePanel } from './LearnRetrospectivePanel';
export type {
  LearnRetrospectivePanelProps,
  LessonCategory,
  LessonImpact,
  Lesson,
  ReusablePattern,
  NewLessonDraft,
} from './LearnRetrospectivePanel';

export { EvolveRoadmapPanel } from './EvolveRoadmapPanel';
export type {
  EvolveRoadmapPanelProps,
  RoadmapPhase,
  RoadmapItemStatus,
  RoadmapItemPriority,
  RoadmapItem,
  BacklogCategory,
  BacklogItem,
} from './EvolveRoadmapPanel';
