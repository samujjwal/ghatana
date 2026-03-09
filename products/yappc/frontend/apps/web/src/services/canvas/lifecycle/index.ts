/**
 * Lifecycle Services Index
 *
 * Centralized exports for lifecycle management services.
 *
 * @doc.type module
 * @doc.purpose Lifecycle services barrel export
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

// Canvas Lifecycle Manager
export { CanvasLifecycle, useCanvasLifecycle } from './CanvasLifecycle';
export type { PhaseChangeEvent, PhaseChangeListener } from './CanvasLifecycle';

// Lifecycle Artifact Service
export {
    LifecycleArtifactService,
    InMemoryLifecycleArtifactRepository,
    useLifecycleArtifacts,
} from './LifecycleArtifactService';
export type {
    LifecycleArtifact,
    ArtifactStatus,
    CreateArtifactRequest,
    UpdateArtifactRequest,
    ArtifactFilter,
    ArtifactSummary,
    ArtifactNode,
    ArtifactEdge,
    ArtifactGraph,
    ILifecycleArtifactRepository,
} from './LifecycleArtifactService';

// Phase Gate Service
export {
    PhaseGateService,
    InMemoryProjectPhaseRepository,
    usePhaseGates,
} from './PhaseGateService';
export type {
    ProjectPhaseState,
    PhaseTransitionRecord,
    TransitionRequest,
    TransitionResult,
    PhaseProgress,
    LifecycleProgress,
    IProjectPhaseRepository,
} from './PhaseGateService';
