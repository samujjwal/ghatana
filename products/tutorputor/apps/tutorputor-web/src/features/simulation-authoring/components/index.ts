/**
 * Simulation Authoring Components
 * 
 * @doc.type module
 * @doc.purpose Barrel exports for simulation authoring components
 * @doc.layer product
 * @doc.pattern Exports
 */

export { SimulationTimelineEditor } from "./SimulationTimelineEditor";
export type { SimulationTimelineEditorProps } from "./SimulationTimelineEditor";

export { StepPalette } from "./StepPalette";
export type { StepPaletteProps, ActionDefinition } from "./StepPalette";

export { SimulationAuthoringWorkspace } from "./SimulationAuthoringWorkspace";
export type { SimulationAuthoringWorkspaceProps } from "./SimulationAuthoringWorkspace";

export { NLAuthorPanel } from "./NLAuthorPanel";
export type { NLAuthorPanelProps } from "./NLAuthorPanel";

export { SimulationPreviewPanel } from "./SimulationPreviewPanel";
export type { SimulationPreviewPanelProps } from "./SimulationPreviewPanel";

// Domain-specific parameter widgets
export * from "./domain-widgets";
