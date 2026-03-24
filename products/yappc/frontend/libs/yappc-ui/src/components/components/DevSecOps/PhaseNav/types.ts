/**
 * PhaseNav Component Types
 *
 * @module DevSecOps/PhaseNav/types
 */

/**
 * DevSecOps phase keys
 */
export type PhaseKey =
  | 'ideation'
  | 'planning'
  | 'development'
  | 'security'
  | 'testing'
  | 'deployment'
  | 'operations';

/**
 * Phase information for navigation
 *
 * @property id - Unique phase identifier
 * @property title - Display name
 * @property key - Phase key for color mapping
 */
export interface Phase {
  /** Unique identifier */
  id: string;

  /** Display name */
  title: string;

  /** Phase key */
  key: PhaseKey;

  /** Optional description */
  description?: string;
}

/**
 * Props for the PhaseNav component
 *
 * @property phases - Array of available phases
 * @property activePhaseId - Currently active phase ID
 * @property completedPhaseIds - Array of completed phase IDs
 * @property onPhaseClick - Callback when phase is clicked
 *
 * @example
 * ```typescript
 * <PhaseNav
 *   phases={phases}
 *   activePhaseId="planning"
 *   completedPhaseIds={['ideation']}
 *   onPhaseClick={(id) => navigate(`/phase/${id}`)}
 * />
 * ```
 */
export interface PhaseNavProps {
  /** Available phases */
  phases: Phase[];

  /** Active phase ID */
  activePhaseId: string;

  /** Completed phase IDs */
  completedPhaseIds?: string[];

  /** Phase click handler */
  onPhaseClick: (phaseId: string) => void;
}
