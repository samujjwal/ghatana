/**
 * Simulation Authoring Feature
 * 
 * Visual timeline-based authoring for simulation manifests.
 * Includes timeline editor, step palette, entity inspector, and preview.
 * 
 * @doc.type module
 * @doc.purpose Feature exports for simulation authoring
 * @doc.layer product
 * @doc.pattern Feature
 */

// Components
export {
  SimulationTimelineEditor,
  StepPalette,
  SimulationAuthoringWorkspace,
  NLAuthorPanel,
  SimulationPreviewPanel,
} from "./components";
export type {
  SimulationTimelineEditorProps,
  StepPaletteProps,
  ActionDefinition,
  SimulationAuthoringWorkspaceProps,
  NLAuthorPanelProps,
  SimulationPreviewPanelProps,
} from "./components";

// Domain widgets
export * from "./components/domain-widgets";

// Hooks
export { useSimulationTimeline, useNLAuthoring, useSimulationValidation, useQuickValidation, useFullValidation } from "./hooks";
export type {
  UseSimulationTimelineOptions,
  UseSimulationTimelineReturn,
  TimelineState,
  TimelineHistoryEntry,
  UseNLAuthoringOptions,
  UseNLAuthoringReturn,
  NLAuthoringState,
  AuthoringMessage,
  GenerationConstraints,
  ParameterSuggestion,
  UseSimulationValidationOptions,
  UseSimulationValidationReturn,
  ValidationError,
  ValidationSeverity,
} from "./hooks";
