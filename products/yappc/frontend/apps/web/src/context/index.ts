/**
 * Context Exports
 * 
 * Central export for all context providers and hooks.
 * 
 * @doc.type module
 * @doc.purpose Context barrel export
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

// Workflow Context
export {
    WorkflowContextProvider,
    useWorkflowContext,
    usePhaseContext,
    useGuidanceContext,
    useSelectionContext,
    useCapabilitiesContext,
} from './WorkflowContextProvider';

export type {
    RouteContext,
    ProjectContext,
    SelectionContext,
    CapabilitiesContext,
    GuidanceStep,
    GuidanceContext,
    WorkflowContextValue,
} from './WorkflowContextProvider';
