/**
 * @fileoverview YAPPC product-local phase-specific canvas actions.
 *
 * Defines the action sets for each phase of the YAPPC lifecycle:
 * INTENT → SHAPE → VALIDATE → GENERATE → RUN → IMPROVE
 *
 * These are product-specific and must NOT live in platform canvas.
 * Inject into the canvas action registry via `initializeActionRegistry({ phaseActions })`.
 *
 * Phase availability is gated by feature flags to prevent use of incomplete phases.
 * See `usePhaseFeatureGate` for the phase enablement registry.
 *
 * @doc.type actions
 * @doc.purpose YAPPC lifecycle phase action definitions
 * @doc.layer product
 */

import type { ActionDefinition } from '@ghatana/canvas';
import { PHASE_ACTION_HANDLERS } from '@/services/canvas/phase-actions/PhaseActionService';

// ============================================================================
// INTENT Phase Actions — Ideation, requirements, vision
// ============================================================================

export const INTENT_ACTIONS: ActionDefinition[] = [
  {
    id: 'intent-create-vision',
    label: 'Create Vision Statement',
    icon: '👁️',
    shortcut: 'V',
    category: 'phase',
    description: 'Create a vision statement for the project',
    priority: 10,
    handler: PHASE_ACTION_HANDLERS['intent-create-vision'],
    isEnabled: (ctx) => ctx.phase === 'INTENT',
    isVisible: (ctx) => ctx.phase === 'INTENT',
  },
  {
    id: 'intent-add-user-story',
    label: 'Add User Story',
    icon: '📖',
    shortcut: 'U',
    category: 'phase',
    description: 'Add a user story',
    priority: 9,
    handler: PHASE_ACTION_HANDLERS['intent-add-user-story'],
    isEnabled: (ctx) => ctx.phase === 'INTENT',
    isVisible: (ctx) => ctx.phase === 'INTENT',
  },
  {
    id: 'intent-define-requirement',
    label: 'Define Requirement',
    icon: '📋',
    shortcut: 'R',
    category: 'phase',
    description: 'Define a functional or non-functional requirement',
    priority: 8,
    handler: PHASE_ACTION_HANDLERS['intent-define-requirement'],
    isEnabled: (ctx) => ctx.phase === 'INTENT',
    isVisible: (ctx) => ctx.phase === 'INTENT',
  },
  {
    id: 'intent-add-stakeholder',
    label: 'Add Stakeholder',
    icon: '👤',
    shortcut: 'S',
    category: 'phase',
    description: 'Add a project stakeholder',
    priority: 7,
    handler: PHASE_ACTION_HANDLERS['intent-add-stakeholder'],
    isEnabled: (ctx) => ctx.phase === 'INTENT',
    isVisible: (ctx) => ctx.phase === 'INTENT',
  },
  {
    id: 'intent-create-goal',
    label: 'Create Goal',
    icon: '🎯',
    shortcut: 'G',
    category: 'phase',
    description: 'Create a project goal',
    priority: 6,
    handler: PHASE_ACTION_HANDLERS['intent-create-goal'],
    isEnabled: (ctx) => ctx.phase === 'INTENT',
    isVisible: (ctx) => ctx.phase === 'INTENT',
  },
];

// ============================================================================
// SHAPE Phase Actions — Architecture, design, structure
// ============================================================================

export const SHAPE_ACTIONS: ActionDefinition[] = [
  {
    id: 'shape-create-diagram',
    label: 'Create Architecture Diagram',
    icon: '📐',
    shortcut: 'D',
    category: 'phase',
    description: 'Create an architecture diagram',
    priority: 10,
    handler: PHASE_ACTION_HANDLERS['shape-create-diagram'],
    isEnabled: (ctx) => ctx.phase === 'SHAPE',
    isVisible: (ctx) => ctx.phase === 'SHAPE',
  },
  {
    id: 'shape-add-service',
    label: 'Add Service',
    icon: '🔷',
    shortcut: 'S',
    category: 'phase',
    description: 'Add a service to the architecture',
    priority: 9,
    handler: PHASE_ACTION_HANDLERS['shape-add-service'],
    isEnabled: (ctx) => ctx.phase === 'SHAPE',
    isVisible: (ctx) => ctx.phase === 'SHAPE',
  },
  {
    id: 'shape-define-api-contract',
    label: 'Define API Contract',
    icon: '🔌',
    shortcut: 'A',
    category: 'phase',
    description: 'Define an API contract',
    priority: 8,
    handler: PHASE_ACTION_HANDLERS['shape-define-api-contract'],
    isEnabled: (ctx) => ctx.phase === 'SHAPE',
    isVisible: (ctx) => ctx.phase === 'SHAPE',
  },
  {
    id: 'shape-add-data-model',
    label: 'Add Data Model',
    icon: '🗄️',
    shortcut: 'M',
    category: 'phase',
    description: 'Add a data model',
    priority: 7,
    handler: PHASE_ACTION_HANDLERS['shape-add-data-model'],
    isEnabled: (ctx) => ctx.phase === 'SHAPE',
    isVisible: (ctx) => ctx.phase === 'SHAPE',
  },
  {
    id: 'shape-create-component',
    label: 'Create Component',
    icon: '🧩',
    shortcut: 'C',
    category: 'phase',
    description: 'Create a component design',
    priority: 6,
    handler: PHASE_ACTION_HANDLERS['shape-create-component'],
    isEnabled: (ctx) => ctx.phase === 'SHAPE',
    isVisible: (ctx) => ctx.phase === 'SHAPE',
  },
];

// ============================================================================
// VALIDATE Phase Actions — Testing, verification, acceptance
// ============================================================================

export const VALIDATE_ACTIONS: ActionDefinition[] = [
  {
    id: 'validate-add-rule',
    label: 'Add Validation Rule',
    icon: '✓',
    shortcut: 'R',
    category: 'phase',
    description: 'Add a validation rule',
    priority: 10,
    handler: PHASE_ACTION_HANDLERS['validate-add-rule'],
    isEnabled: (ctx) => ctx.phase === 'VALIDATE',
    isVisible: (ctx) => ctx.phase === 'VALIDATE',
  },
  {
    id: 'validate-create-test-case',
    label: 'Create Test Case',
    icon: '🧪',
    shortcut: 'T',
    category: 'phase',
    description: 'Create a test case',
    priority: 9,
    handler: PHASE_ACTION_HANDLERS['validate-create-test-case'],
    isEnabled: (ctx) => ctx.phase === 'VALIDATE',
    isVisible: (ctx) => ctx.phase === 'VALIDATE',
  },
  {
    id: 'validate-add-acceptance-criteria',
    label: 'Add Acceptance Criteria',
    icon: '📝',
    shortcut: 'A',
    category: 'phase',
    description: 'Add acceptance criteria',
    priority: 8,
    handler: PHASE_ACTION_HANDLERS['validate-add-acceptance-criteria'],
    isEnabled: (ctx) => ctx.phase === 'VALIDATE',
    isVisible: (ctx) => ctx.phase === 'VALIDATE',
  },
];

// ============================================================================
// GENERATE Phase Actions — Code generation, scaffolding
// ============================================================================

export const GENERATE_ACTIONS: ActionDefinition[] = [
  {
    id: 'generate-code',
    label: 'Generate Code',
    icon: '⚙️',
    shortcut: 'G',
    category: 'phase',
    description: 'Generate code from design',
    priority: 10,
    handler: PHASE_ACTION_HANDLERS['generate-code'],
    isEnabled: (ctx) => ctx.phase === 'GENERATE',
    isVisible: (ctx) => ctx.phase === 'GENERATE',
  },
  {
    id: 'generate-create-scaffold',
    label: 'Create Scaffold',
    icon: '🏗️',
    shortcut: 'S',
    category: 'phase',
    description: 'Create project scaffold',
    priority: 9,
    handler: PHASE_ACTION_HANDLERS['generate-create-scaffold'],
    isEnabled: (ctx) => ctx.phase === 'GENERATE',
    isVisible: (ctx) => ctx.phase === 'GENERATE',
  },
  {
    id: 'generate-tests',
    label: 'Generate Tests',
    icon: '🧪',
    shortcut: 'T',
    category: 'phase',
    description: 'Generate test files',
    priority: 7,
    handler: PHASE_ACTION_HANDLERS['generate-tests'],
    isEnabled: (ctx) => ctx.phase === 'GENERATE',
    isVisible: (ctx) => ctx.phase === 'GENERATE',
  },
];

// ============================================================================
// RUN Phase Actions — Execution, deployment, operations
// ============================================================================

export const RUN_ACTIONS: ActionDefinition[] = [
  {
    id: 'run-deploy-service',
    label: 'Deploy Service',
    icon: '🚀',
    shortcut: 'D',
    category: 'phase',
    description: 'Deploy service to environment',
    priority: 10,
    handler: PHASE_ACTION_HANDLERS['run-deploy-service'],
    isEnabled: (ctx) => ctx.phase === 'RUN',
    isVisible: (ctx) => ctx.phase === 'RUN',
  },
  {
    id: 'run-execute-tests',
    label: 'Run Tests',
    icon: '🧪',
    shortcut: 'T',
    category: 'phase',
    description: 'Execute test suite',
    priority: 8,
    handler: PHASE_ACTION_HANDLERS['run-execute-tests'],
    isEnabled: (ctx) => ctx.phase === 'RUN',
    isVisible: (ctx) => ctx.phase === 'RUN',
  },
  {
    id: 'run-monitor-logs',
    label: 'Monitor Logs',
    icon: '📝',
    shortcut: 'L',
    category: 'phase',
    description: 'Monitor application logs',
    priority: 6,
    handler: PHASE_ACTION_HANDLERS['run-monitor-logs'],
    isEnabled: (ctx) => ctx.phase === 'RUN',
    isVisible: (ctx) => ctx.phase === 'RUN',
  },
];

// ============================================================================
// IMPROVE Phase Actions — Optimization, refactoring, enhancement
// ============================================================================

export const IMPROVE_ACTIONS: ActionDefinition[] = [
  {
    id: 'improve-create-enhancement',
    label: 'Create Enhancement',
    icon: '✨',
    shortcut: 'E',
    category: 'phase',
    description: 'Create enhancement proposal',
    priority: 10,
    handler: PHASE_ACTION_HANDLERS['improve-create-enhancement'],
    isEnabled: (ctx) => ctx.phase === 'IMPROVE',
    isVisible: (ctx) => ctx.phase === 'IMPROVE',
  },
  {
    id: 'improve-refactor-code',
    label: 'Refactor Code',
    icon: '🔧',
    shortcut: 'R',
    category: 'phase',
    description: 'Refactor existing code',
    priority: 8,
    handler: PHASE_ACTION_HANDLERS['improve-refactor-code'],
    isEnabled: (ctx) => ctx.phase === 'IMPROVE',
    isVisible: (ctx) => ctx.phase === 'IMPROVE',
  },
  {
    id: 'improve-add-feature',
    label: 'Add Feature',
    icon: '🎁',
    shortcut: 'F',
    category: 'phase',
    description: 'Add new feature',
    priority: 6,
    handler: PHASE_ACTION_HANDLERS['improve-add-feature'],
    isEnabled: (ctx) => ctx.phase === 'IMPROVE',
    isVisible: (ctx) => ctx.phase === 'IMPROVE',
  },
];

// ============================================================================
// Aggregation helpers
// ============================================================================

export function getAllPhaseActions(): Record<string, ActionDefinition[]> {
  return {
    INTENT: INTENT_ACTIONS,
    SHAPE: SHAPE_ACTIONS,
    VALIDATE: VALIDATE_ACTIONS,
    GENERATE: GENERATE_ACTIONS,
    RUN: RUN_ACTIONS,
    IMPROVE: IMPROVE_ACTIONS,
  };
}

export function getPhaseActions(phase: string): ActionDefinition[] {
  return getAllPhaseActions()[phase] ?? [];
}
