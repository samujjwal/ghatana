/**
 * FOW (Future of Work) Stage Type System
 * 
 * Maps the 10 FOW lifecycle stages (0-9) to Canvas phases and defines
 * gates, required artifacts, and stage progression logic.
 * 
 * Aligned with yappc_unified_ux_and_execution_spec.md Section 5.
 * 
 * @doc.type types
 * @doc.purpose FOW stage definitions and mappings
 * @doc.layer product
 * @doc.pattern Type System
 */

import { LifecyclePhase } from './lifecycle';

// ============================================================================
// Types
// ============================================================================

/**
 * FOW Stage IDs (0-9)
 */
export enum FOWStage {
    FOUNDATION = 0,
    IDEATION = 1,
    DISCOVERY = 2,
    DEFINITION = 3,
    SOLUTION_DESIGN = 4,
    DELIVERY_PLANNING = 5,
    BUILD_INTEGRATE = 6,
    VERIFY_RELEASE = 7,
    OPERATE_OBSERVE = 8,
    ENHANCE_EVOLVE = 9,
}

/**
 * Artifact types used across FOW stages
 */
export enum ArtifactType {
    // Foundation
    WORKSPACE = 'WORKSPACE',
    PROJECT = 'PROJECT',

    // Ideation
    IDEA_BRIEF = 'IDEA_BRIEF',
    INSIGHT = 'INSIGHT',
    PREDICTION = 'PREDICTION',

    // Discovery
    PROBLEM_STATEMENT_METRICS = 'PROBLEM_STATEMENT_METRICS',
    RESEARCH_PACK = 'RESEARCH_PACK',

    // Definition
    REQUIREMENT = 'REQUIREMENT',

    // Solution Design
    ARCHITECTURE_DECISION_RECORD = 'ARCHITECTURE_DECISION_RECORD',

    // Delivery Planning
    PLAN = 'PLAN',
    DEVSECOPS_ITEM = 'DEVSECOPS_ITEM',

    // Build & Integrate
    DELIVERY_EVIDENCE = 'DELIVERY_EVIDENCE',

    // Verify & Release
    RELEASE_PACKET = 'RELEASE_PACKET',

    // Operate & Observe
    OPS_BASELINE = 'OPS_BASELINE',
    INCIDENT = 'INCIDENT',

    // Enhance & Evolve
    ENHANCEMENT = 'ENHANCEMENT',
}

/**
 * Gate definition for stage completion
 */
export interface GateDefinition {
    /** Gate ID */
    id: string;
    /** Required artifacts with their statuses */
    requiredArtifacts: {
        type: ArtifactType;
        minCount: number;
        requiredStatus?: 'draft' | 'review' | 'approved';
    }[];
    /** Additional conditions (optional) */
    conditions?: {
        id: string;
        description: string;
        check: () => Promise<boolean>;
    }[];
}

/**
 * Gate result after checking
 */
export interface GateResult {
    /** Overall readiness percentage (0-100) */
    readiness: number;
    /** Can proceed to next stage */
    canProceed: boolean;
    /** Missing required artifacts */
    missingArtifacts: {
        type: ArtifactType;
        required: number;
        current: number;
    }[];
    /** Failed conditions */
    failedConditions: string[];
}

/**
 * Complete FOW stage configuration
 */
export interface FOWStageConfig {
    /** Stage ID */
    id: FOWStage;
    /** Stage name */
    name: string;
    /** Stage description */
    description: string;
    /** Canvas phases used in this stage */
    canvasPhases: LifecyclePhase[];
    /** Primary canvas phase for this stage */
    primaryPhase: LifecyclePhase;
    /** Required artifacts to complete this stage */
    requiredArtifacts: ArtifactType[];
    /** Gate definition */
    gate: GateDefinition;
    /** Audit event prefix */
    auditEventPrefix: string;
    /** Icon name */
    icon: string;
    /** Color for UI */
    color: string;
}

// ============================================================================
// Constants
// ============================================================================

/**
 * Stage labels
 */
export const FOW_STAGE_LABELS: Record<FOWStage, string> = {
    [FOWStage.FOUNDATION]: 'Foundation Setup',
    [FOWStage.IDEATION]: 'Ideation & Intake',
    [FOWStage.DISCOVERY]: 'Discovery & Research',
    [FOWStage.DEFINITION]: 'Definition & Requirements',
    [FOWStage.SOLUTION_DESIGN]: 'Solution Design',
    [FOWStage.DELIVERY_PLANNING]: 'Delivery Planning',
    [FOWStage.BUILD_INTEGRATE]: 'Build & Integrate',
    [FOWStage.VERIFY_RELEASE]: 'Verify & Release',
    [FOWStage.OPERATE_OBSERVE]: 'Operate & Observe',
    [FOWStage.ENHANCE_EVOLVE]: 'Enhance & Evolve',
};

/**
 * Stage descriptions
 */
export const FOW_STAGE_DESCRIPTIONS: Record<FOWStage, string> = {
    [FOWStage.FOUNDATION]: 'Enable workspace, AI, data, and audit foundations',
    [FOWStage.IDEATION]: 'Capture and structure ideas with AI insights',
    [FOWStage.DISCOVERY]: 'Research problem space and define metrics',
    [FOWStage.DEFINITION]: 'Define requirements with acceptance criteria',
    [FOWStage.SOLUTION_DESIGN]: 'Design architecture and make technical decisions',
    [FOWStage.DELIVERY_PLANNING]: 'Plan delivery with milestones and work items',
    [FOWStage.BUILD_INTEGRATE]: 'Build components and integrate with evidence',
    [FOWStage.VERIFY_RELEASE]: 'Verify quality and create release packet',
    [FOWStage.OPERATE_OBSERVE]: 'Deploy, monitor, and observe operations',
    [FOWStage.ENHANCE_EVOLVE]: 'Gather insights and plan enhancements',
};

/**
 * Complete FOW stage configurations
 */
export const FOW_STAGE_CONFIGS: Record<FOWStage, FOWStageConfig> = {
    [FOWStage.FOUNDATION]: {
        id: FOWStage.FOUNDATION,
        name: FOW_STAGE_LABELS[FOWStage.FOUNDATION],
        description: FOW_STAGE_DESCRIPTIONS[FOWStage.FOUNDATION],
        canvasPhases: [], // Setup doesn't use canvas
        primaryPhase: LifecyclePhase.INTENT,
        requiredArtifacts: [ArtifactType.WORKSPACE, ArtifactType.PROJECT],
        gate: {
            id: 'foundation-gate',
            requiredArtifacts: [
                { type: ArtifactType.WORKSPACE, minCount: 1 },
                { type: ArtifactType.PROJECT, minCount: 1 },
            ],
        },
        auditEventPrefix: 'FOUNDATION',
        icon: 'Settings',
        color: '#6B7280',
    },

    [FOWStage.IDEATION]: {
        id: FOWStage.IDEATION,
        name: FOW_STAGE_LABELS[FOWStage.IDEATION],
        description: FOW_STAGE_DESCRIPTIONS[FOWStage.IDEATION],
        canvasPhases: [LifecyclePhase.INTENT],
        primaryPhase: LifecyclePhase.INTENT,
        requiredArtifacts: [ArtifactType.IDEA_BRIEF, ArtifactType.INSIGHT],
        gate: {
            id: 'ideation-gate',
            requiredArtifacts: [
                { type: ArtifactType.IDEA_BRIEF, minCount: 1 },
                { type: ArtifactType.INSIGHT, minCount: 1 },
            ],
        },
        auditEventPrefix: 'IDEATION',
        icon: 'Lightbulb',
        color: '#F59E0B',
    },

    [FOWStage.DISCOVERY]: {
        id: FOWStage.DISCOVERY,
        name: FOW_STAGE_LABELS[FOWStage.DISCOVERY],
        description: FOW_STAGE_DESCRIPTIONS[FOWStage.DISCOVERY],
        canvasPhases: [LifecyclePhase.INTENT, LifecyclePhase.SHAPE],
        primaryPhase: LifecyclePhase.INTENT,
        requiredArtifacts: [ArtifactType.PROBLEM_STATEMENT_METRICS, ArtifactType.RESEARCH_PACK],
        gate: {
            id: 'discovery-gate',
            requiredArtifacts: [
                { type: ArtifactType.PROBLEM_STATEMENT_METRICS, minCount: 1 },
                { type: ArtifactType.RESEARCH_PACK, minCount: 1 },
            ],
        },
        auditEventPrefix: 'DISCOVERY',
        icon: 'Search',
        color: '#10B981',
    },

    [FOWStage.DEFINITION]: {
        id: FOWStage.DEFINITION,
        name: FOW_STAGE_LABELS[FOWStage.DEFINITION],
        description: FOW_STAGE_DESCRIPTIONS[FOWStage.DEFINITION],
        canvasPhases: [LifecyclePhase.SHAPE],
        primaryPhase: LifecyclePhase.SHAPE,
        requiredArtifacts: [ArtifactType.REQUIREMENT],
        gate: {
            id: 'definition-gate',
            requiredArtifacts: [
                { type: ArtifactType.REQUIREMENT, minCount: 1, requiredStatus: 'approved' },
            ],
        },
        auditEventPrefix: 'DEFINITION',
        icon: 'Description',
        color: '#3B82F6',
    },

    [FOWStage.SOLUTION_DESIGN]: {
        id: FOWStage.SOLUTION_DESIGN,
        name: FOW_STAGE_LABELS[FOWStage.SOLUTION_DESIGN],
        description: FOW_STAGE_DESCRIPTIONS[FOWStage.SOLUTION_DESIGN],
        canvasPhases: [LifecyclePhase.SHAPE, LifecyclePhase.VALIDATE],
        primaryPhase: LifecyclePhase.SHAPE,
        requiredArtifacts: [ArtifactType.ARCHITECTURE_DECISION_RECORD],
        gate: {
            id: 'design-gate',
            requiredArtifacts: [
                { type: ArtifactType.ARCHITECTURE_DECISION_RECORD, minCount: 1, requiredStatus: 'approved' },
            ],
        },
        auditEventPrefix: 'DESIGN',
        icon: 'Architecture',
        color: '#8B5CF6',
    },

    [FOWStage.DELIVERY_PLANNING]: {
        id: FOWStage.DELIVERY_PLANNING,
        name: FOW_STAGE_LABELS[FOWStage.DELIVERY_PLANNING],
        description: FOW_STAGE_DESCRIPTIONS[FOWStage.DELIVERY_PLANNING],
        canvasPhases: [LifecyclePhase.SHAPE],
        primaryPhase: LifecyclePhase.SHAPE,
        requiredArtifacts: [ArtifactType.PLAN, ArtifactType.DEVSECOPS_ITEM],
        gate: {
            id: 'planning-gate',
            requiredArtifacts: [
                { type: ArtifactType.PLAN, minCount: 1, requiredStatus: 'approved' },
                { type: ArtifactType.DEVSECOPS_ITEM, minCount: 1 },
            ],
        },
        auditEventPrefix: 'PLANNING',
        icon: 'CalendarToday',
        color: '#EC4899',
    },

    [FOWStage.BUILD_INTEGRATE]: {
        id: FOWStage.BUILD_INTEGRATE,
        name: FOW_STAGE_LABELS[FOWStage.BUILD_INTEGRATE],
        description: FOW_STAGE_DESCRIPTIONS[FOWStage.BUILD_INTEGRATE],
        canvasPhases: [LifecyclePhase.SHAPE, LifecyclePhase.VALIDATE, LifecyclePhase.GENERATE],
        primaryPhase: LifecyclePhase.GENERATE,
        requiredArtifacts: [ArtifactType.DELIVERY_EVIDENCE],
        gate: {
            id: 'build-gate',
            requiredArtifacts: [
                { type: ArtifactType.DELIVERY_EVIDENCE, minCount: 1 },
            ],
        },
        auditEventPrefix: 'BUILD',
        icon: 'Build',
        color: '#F97316',
    },

    [FOWStage.VERIFY_RELEASE]: {
        id: FOWStage.VERIFY_RELEASE,
        name: FOW_STAGE_LABELS[FOWStage.VERIFY_RELEASE],
        description: FOW_STAGE_DESCRIPTIONS[FOWStage.VERIFY_RELEASE],
        canvasPhases: [LifecyclePhase.VALIDATE, LifecyclePhase.RUN],
        primaryPhase: LifecyclePhase.VALIDATE,
        requiredArtifacts: [ArtifactType.RELEASE_PACKET],
        gate: {
            id: 'release-gate',
            requiredArtifacts: [
                { type: ArtifactType.RELEASE_PACKET, minCount: 1, requiredStatus: 'approved' },
            ],
        },
        auditEventPrefix: 'RELEASE',
        icon: 'RocketLaunch',
        color: '#14B8A6',
    },

    [FOWStage.OPERATE_OBSERVE]: {
        id: FOWStage.OPERATE_OBSERVE,
        name: FOW_STAGE_LABELS[FOWStage.OPERATE_OBSERVE],
        description: FOW_STAGE_DESCRIPTIONS[FOWStage.OPERATE_OBSERVE],
        canvasPhases: [LifecyclePhase.RUN, LifecyclePhase.OBSERVE],
        primaryPhase: LifecyclePhase.OBSERVE,
        requiredArtifacts: [ArtifactType.OPS_BASELINE],
        gate: {
            id: 'ops-gate',
            requiredArtifacts: [
                { type: ArtifactType.OPS_BASELINE, minCount: 1 },
            ],
        },
        auditEventPrefix: 'OPS',
        icon: 'MonitorHeart',
        color: '#06B6D4',
    },

    [FOWStage.ENHANCE_EVOLVE]: {
        id: FOWStage.ENHANCE_EVOLVE,
        name: FOW_STAGE_LABELS[FOWStage.ENHANCE_EVOLVE],
        description: FOW_STAGE_DESCRIPTIONS[FOWStage.ENHANCE_EVOLVE],
        canvasPhases: [LifecyclePhase.OBSERVE, LifecyclePhase.IMPROVE, LifecyclePhase.INTENT],
        primaryPhase: LifecyclePhase.IMPROVE,
        requiredArtifacts: [ArtifactType.ENHANCEMENT],
        gate: {
            id: 'enhance-gate',
            requiredArtifacts: [
                { type: ArtifactType.ENHANCEMENT, minCount: 1 },
            ],
        },
        auditEventPrefix: 'ENHANCEMENT',
        icon: 'TrendingUp',
        color: '#A855F7',
    },
};

/**
 * Ordered list of FOW stages
 */
export const FOW_STAGE_ORDER: FOWStage[] = [
    FOWStage.FOUNDATION,
    FOWStage.IDEATION,
    FOWStage.DISCOVERY,
    FOWStage.DEFINITION,
    FOWStage.SOLUTION_DESIGN,
    FOWStage.DELIVERY_PLANNING,
    FOWStage.BUILD_INTEGRATE,
    FOWStage.VERIFY_RELEASE,
    FOWStage.OPERATE_OBSERVE,
    FOWStage.ENHANCE_EVOLVE,
];

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Get FOW stage for a given canvas phase
 */
export function getFOWStageForPhase(phase: LifecyclePhase, currentStage?: FOWStage): FOWStage {
    // If current stage uses this phase, stay in that stage
    if (currentStage !== undefined) {
        const config = FOW_STAGE_CONFIGS[currentStage];
        if (config.canvasPhases.includes(phase)) {
            return currentStage;
        }
    }

    // Find first stage that uses this phase
    for (const stageConfig of Object.values(FOW_STAGE_CONFIGS)) {
        if (stageConfig.canvasPhases.includes(phase)) {
            return stageConfig.id;
        }
    }

    // Default fallback
    return FOWStage.IDEATION;
}

/**
 * Get next FOW stage
 */
export function getNextFOWStage(currentStage: FOWStage): FOWStage | null {
    const currentIndex = FOW_STAGE_ORDER.indexOf(currentStage);
    if (currentIndex < FOW_STAGE_ORDER.length - 1) {
        return FOW_STAGE_ORDER[currentIndex + 1];
    }
    return null; // Last stage
}

/**
 * Get previous FOW stage
 */
export function getPreviousFOWStage(currentStage: FOWStage): FOWStage | null {
    const currentIndex = FOW_STAGE_ORDER.indexOf(currentStage);
    if (currentIndex > 0) {
        return FOW_STAGE_ORDER[currentIndex - 1];
    }
    return null; // First stage
}

/**
 * Check if phase is available in current FOW stage
 */
export function isPhaseAvailableInStage(phase: LifecyclePhase, stage: FOWStage): boolean {
    const config = FOW_STAGE_CONFIGS[stage];
    return config.canvasPhases.includes(phase);
}
