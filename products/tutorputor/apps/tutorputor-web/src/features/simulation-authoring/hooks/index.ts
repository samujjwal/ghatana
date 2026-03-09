/**
 * Simulation Authoring Hooks
 * 
 * @doc.type module
 * @doc.purpose Barrel exports for simulation authoring hooks
 * @doc.layer product
 * @doc.pattern Exports
 */

export { useSimulationTimeline } from "./useSimulationTimeline";
export type {
  UseSimulationTimelineOptions,
  UseSimulationTimelineReturn,
  TimelineState,
  TimelineHistoryEntry,
} from "./useSimulationTimeline";

export { useNLAuthoring } from "./useNLAuthoring";
export type {
  UseNLAuthoringOptions,
  UseNLAuthoringReturn,
  NLAuthoringState,
  AuthoringMessage,
  GenerationConstraints,
  ParameterSuggestion,
} from "./useNLAuthoring";

export { useSimulationValidation, useQuickValidation, useFullValidation } from "./useSimulationValidation";
export type {
  UseSimulationValidationOptions,
  UseSimulationValidationReturn,
  ValidationError,
  ValidationSeverity,
} from "./useSimulationValidation";
