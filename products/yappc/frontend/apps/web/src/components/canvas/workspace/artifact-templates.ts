/**
 * Artifact Templates - Shared Definitions
 * 
 * Single source of truth for artifact templates used across:
 * - ArtifactPalette
 * - QuickCreateMenu
 * - Canvas drag and drop
 * 
 * @doc.type module
 * @doc.purpose Centralized artifact template definitions
 * @doc.layer product
 * @doc.pattern Shared Constants
 */

import { LifecyclePhase } from '@/types/lifecycle';
import { PHASE_COLORS, PHASE_LABELS } from '../../../styles/design-tokens';

export type ArtifactType =
    | 'brief'
    | 'user-story'
    | 'requirement'
    | 'design'
    | 'mockup'
    | 'api-spec'
    | 'code'
    | 'test'
    | 'deployment'
    | 'metric';

export interface ArtifactTemplate {
    type: ArtifactType;
    icon: string;
    label: string;
    description: string;
    phase: LifecyclePhase;
    defaultTitle: string;
}

export interface PhaseGroup {
    phase: LifecyclePhase;
    label: string;
    color: string;
}

/**
 * All available artifact templates organized by lifecycle phase
 */
export const ARTIFACT_TEMPLATES: ArtifactTemplate[] = [
    // INTENT Phase
    {
        type: 'brief',
        icon: '📄',
        label: 'Brief',
        description: 'Project or feature brief',
        phase: LifecyclePhase.INTENT,
        defaultTitle: 'New Brief',
    },
    {
        type: 'user-story',
        icon: '📖',
        label: 'User Story',
        description: 'User story or epic',
        phase: LifecyclePhase.INTENT,
        defaultTitle: 'As a user...',
    },

    // SHAPE Phase
    {
        type: 'requirement',
        icon: '📋',
        label: 'Requirement',
        description: 'Functional requirement',
        phase: LifecyclePhase.SHAPE,
        defaultTitle: 'New Requirement',
    },
    {
        type: 'design',
        icon: '🎨',
        label: 'Design',
        description: 'Design specification',
        phase: LifecyclePhase.SHAPE,
        defaultTitle: 'New Design',
    },

    // VALIDATE Phase
    {
        type: 'mockup',
        icon: '🖼️',
        label: 'Mockup',
        description: 'UI mockup or wireframe',
        phase: LifecyclePhase.VALIDATE,
        defaultTitle: 'New Mockup',
    },
    {
        type: 'api-spec',
        icon: '🔌',
        label: 'API Spec',
        description: 'API specification',
        phase: LifecyclePhase.VALIDATE,
        defaultTitle: 'New API',
    },

    // GENERATE Phase
    {
        type: 'code',
        icon: '💻',
        label: 'Code',
        description: 'Implementation code',
        phase: LifecyclePhase.GENERATE,
        defaultTitle: 'New Component',
    },
    {
        type: 'test',
        icon: '🧪',
        label: 'Test',
        description: 'Test suite or case',
        phase: LifecyclePhase.GENERATE,
        defaultTitle: 'New Test',
    },

    // RUN Phase
    {
        type: 'deployment',
        icon: '🚀',
        label: 'Deployment',
        description: 'Deployment config',
        phase: LifecyclePhase.RUN,
        defaultTitle: 'New Deployment',
    },

    // OBSERVE Phase
    {
        type: 'metric',
        icon: '📊',
        label: 'Metric',
        description: 'Performance metric',
        phase: LifecyclePhase.OBSERVE,
        defaultTitle: 'New Metric',
    },
];

/**
 * Phase groups for organizing artifacts
 * Colors from design-tokens.ts PHASE_COLORS
 */
export const PHASE_GROUPS: PhaseGroup[] = [
    { phase: LifecyclePhase.INTENT, label: PHASE_LABELS.INTENT, color: PHASE_COLORS.INTENT.background },
    { phase: LifecyclePhase.SHAPE, label: PHASE_LABELS.SHAPE, color: PHASE_COLORS.SHAPE.background },
    { phase: LifecyclePhase.VALIDATE, label: PHASE_LABELS.VALIDATE, color: PHASE_COLORS.VALIDATE.background },
    { phase: LifecyclePhase.GENERATE, label: PHASE_LABELS.GENERATE, color: PHASE_COLORS.GENERATE.background },
    { phase: LifecyclePhase.RUN, label: PHASE_LABELS.RUN, color: PHASE_COLORS.RUN.background },
    { phase: LifecyclePhase.OBSERVE, label: PHASE_LABELS.OBSERVE, color: PHASE_COLORS.OBSERVE.background },
    { phase: LifecyclePhase.IMPROVE, label: PHASE_LABELS.IMPROVE, color: PHASE_COLORS.IMPROVE.background },
];

/**
 * Get all templates for a specific phase
 */
export function getTemplatesForPhase(phase: LifecyclePhase): ArtifactTemplate[] {
    return ARTIFACT_TEMPLATES.filter(template => template.phase === phase);
}

/**
 * Get template by type
 */
export function getTemplateByType(type: ArtifactType): ArtifactTemplate | undefined {
    return ARTIFACT_TEMPLATES.find(template => template.type === type);
}

/**
 * Get phase group info
 */
export function getPhaseGroup(phase: LifecyclePhase): PhaseGroup | undefined {
    return PHASE_GROUPS.find(group => group.phase === phase);
}
