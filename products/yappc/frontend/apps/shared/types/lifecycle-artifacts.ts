/**
 * Lifecycle Artifact Type System
 *
 * Defines the 17 lifecycle artifacts used across YAPPC's 7-phase lifecycle model.
 * These artifacts are stored as Items tagged with `artifact:<kind>`.
 *
 * @doc.type shared-types
 * @doc.purpose Lifecycle artifact taxonomy definitions
 * @doc.layer shared
 * @doc.pattern Type System
 */

import { LifecyclePhase } from './lifecycle';

// ============================================================================
// Lifecycle Artifact Kind Enum
// ============================================================================

/**
 * The 17 lifecycle artifacts organized by phase.
 *
 * - INTENT: idea_brief, research_pack, problem_statement
 * - SHAPE: requirements, adr, ux_spec, threat_model
 * - VALIDATE: validation_report, simulation_results
 * - GENERATE: delivery_plan, release_strategy
 * - RUN: evidence_pack, release_packet
 * - OBSERVE: ops_baseline, incident_report
 * - IMPROVE: enhancement_requests, learning_record
 */
export enum LifecycleArtifactKind {
    // INTENT Phase
    IDEA_BRIEF = 'idea_brief',
    RESEARCH_PACK = 'research_pack',
    PROBLEM_STATEMENT = 'problem_statement',

    // SHAPE Phase
    REQUIREMENTS = 'requirements',
    ADR = 'adr',
    UX_SPEC = 'ux_spec',
    THREAT_MODEL = 'threat_model',

    // VALIDATE Phase
    VALIDATION_REPORT = 'validation_report',
    SIMULATION_RESULTS = 'simulation_results',

    // GENERATE Phase
    DELIVERY_PLAN = 'delivery_plan',
    RELEASE_STRATEGY = 'release_strategy',

    // RUN Phase
    EVIDENCE_PACK = 'evidence_pack',
    RELEASE_PACKET = 'release_packet',

    // OBSERVE Phase
    OPS_BASELINE = 'ops_baseline',
    INCIDENT_REPORT = 'incident_report',

    // IMPROVE Phase
    ENHANCEMENT_REQUESTS = 'enhancement_requests',
    LEARNING_RECORD = 'learning_record',
}

/**
 * Tag prefix for lifecycle artifacts.
 * Full tag format: artifact:<kind>
 * Example: artifact:problem_statement
 */
export const LIFECYCLE_ARTIFACT_TAG_PREFIX = 'artifact:';

/**
 * Creates a full artifact tag from a kind.
 */
export function createArtifactTag(kind: LifecycleArtifactKind): string {
    return `${LIFECYCLE_ARTIFACT_TAG_PREFIX}${kind}`;
}

/**
 * Extracts the artifact kind from a full tag.
 * Returns null if the tag is not a valid artifact tag.
 */
export function parseArtifactTag(tag: string): LifecycleArtifactKind | null {
    if (!tag.startsWith(LIFECYCLE_ARTIFACT_TAG_PREFIX)) {
        return null;
    }
    const kind = tag.slice(LIFECYCLE_ARTIFACT_TAG_PREFIX.length);
    return Object.values(LifecycleArtifactKind).includes(kind as LifecycleArtifactKind)
        ? (kind as LifecycleArtifactKind)
        : null;
}

// ============================================================================
// Artifact Metadata
// ============================================================================

/**
 * Prisma ArtifactType mapping for lifecycle artifacts.
 * Maps lifecycle artifact kind to the coarse Prisma enum.
 */
export type PrismaArtifactType =
    | 'DIAGRAM'
    | 'DOCUMENT'
    | 'CODE'
    | 'TEST'
    | 'DESIGN'
    | 'SCRIPT'
    | 'REPORT'
    | 'PRESENTATION';

/**
 * UI placement info for lifecycle artifacts.
 */
export interface ArtifactPlacement {
    surface: 'app' | 'canvas' | 'preview' | 'deploy';
    param: string; // drawer/panel/segment value
    paramType: 'drawer' | 'panel' | 'segment';
}

/**
 * Metadata for each lifecycle artifact kind.
 */
export interface LifecycleArtifactMetadata {
    kind: LifecycleArtifactKind;
    phase: LifecyclePhase;
    label: string;
    description: string;
    icon: string;
    defaultArtifactType: PrismaArtifactType;
    placement: ArtifactPlacement;
    requiredUpstream: LifecycleArtifactKind[];
}

/**
 * Complete catalog of lifecycle artifact metadata.
 */
export const LIFECYCLE_ARTIFACT_CATALOG: Record<LifecycleArtifactKind, LifecycleArtifactMetadata> = {
    // INTENT Phase
    [LifecycleArtifactKind.IDEA_BRIEF]: {
        kind: LifecycleArtifactKind.IDEA_BRIEF,
        phase: LifecyclePhase.INTENT,
        label: 'Idea Brief',
        description: 'Initial idea description with value proposition and target users',
        icon: '💡',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'app', param: 'idea', paramType: 'drawer' },
        requiredUpstream: [],
    },
    [LifecycleArtifactKind.RESEARCH_PACK]: {
        kind: LifecycleArtifactKind.RESEARCH_PACK,
        phase: LifecyclePhase.INTENT,
        label: 'Research Pack',
        description: 'User research, market analysis, and discovery findings',
        icon: '🔍',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'app', param: 'research', paramType: 'drawer' },
        requiredUpstream: [LifecycleArtifactKind.IDEA_BRIEF],
    },
    [LifecycleArtifactKind.PROBLEM_STATEMENT]: {
        kind: LifecycleArtifactKind.PROBLEM_STATEMENT,
        phase: LifecyclePhase.INTENT,
        label: 'Problem Statement',
        description: 'Refined problem definition with success metrics',
        icon: '🎯',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'app', param: 'problem', paramType: 'drawer' },
        requiredUpstream: [LifecycleArtifactKind.RESEARCH_PACK],
    },

    // SHAPE Phase
    [LifecycleArtifactKind.REQUIREMENTS]: {
        kind: LifecycleArtifactKind.REQUIREMENTS,
        phase: LifecyclePhase.SHAPE,
        label: 'Requirements',
        description: 'Epics, capabilities, and requirements with acceptance criteria',
        icon: '📋',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'canvas', param: 'requirements', paramType: 'panel' },
        requiredUpstream: [LifecycleArtifactKind.PROBLEM_STATEMENT],
    },
    [LifecycleArtifactKind.ADR]: {
        kind: LifecycleArtifactKind.ADR,
        phase: LifecyclePhase.SHAPE,
        label: 'Architecture Decision Record',
        description: 'Key architectural decisions with context and consequences',
        icon: '🏛️',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'canvas', param: 'adr', paramType: 'panel' },
        requiredUpstream: [LifecycleArtifactKind.REQUIREMENTS],
    },
    [LifecycleArtifactKind.UX_SPEC]: {
        kind: LifecycleArtifactKind.UX_SPEC,
        phase: LifecyclePhase.SHAPE,
        label: 'UX Specification',
        description: 'User flows, IA notes, accessibility considerations',
        icon: '🎨',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'canvas', param: 'ux', paramType: 'panel' },
        requiredUpstream: [LifecycleArtifactKind.REQUIREMENTS],
    },
    [LifecycleArtifactKind.THREAT_MODEL]: {
        kind: LifecycleArtifactKind.THREAT_MODEL,
        phase: LifecyclePhase.SHAPE,
        label: 'Threat Model',
        description: 'Security analysis using STRIDE framework',
        icon: '🛡️',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'canvas', param: 'threat', paramType: 'panel' },
        requiredUpstream: [LifecycleArtifactKind.REQUIREMENTS, LifecycleArtifactKind.ADR],
    },

    // VALIDATE Phase
    [LifecycleArtifactKind.VALIDATION_REPORT]: {
        kind: LifecycleArtifactKind.VALIDATION_REPORT,
        phase: LifecyclePhase.VALIDATE,
        label: 'Validation Report',
        description: 'Comprehensive validation results with findings and recommendations',
        icon: '✅',
        defaultArtifactType: 'REPORT',
        placement: { surface: 'preview', param: 'validation', paramType: 'panel' },
        requiredUpstream: [
            LifecycleArtifactKind.REQUIREMENTS,
            LifecycleArtifactKind.UX_SPEC,
            LifecycleArtifactKind.THREAT_MODEL,
        ],
    },
    [LifecycleArtifactKind.SIMULATION_RESULTS]: {
        kind: LifecycleArtifactKind.SIMULATION_RESULTS,
        phase: LifecyclePhase.VALIDATE,
        label: 'Simulation Results',
        description: 'Behavior simulation scenarios and outcomes',
        icon: '🔬',
        defaultArtifactType: 'REPORT',
        placement: { surface: 'preview', param: 'validation', paramType: 'panel' },
        requiredUpstream: [LifecycleArtifactKind.REQUIREMENTS],
    },

    // GENERATE Phase
    [LifecycleArtifactKind.DELIVERY_PLAN]: {
        kind: LifecycleArtifactKind.DELIVERY_PLAN,
        phase: LifecyclePhase.GENERATE,
        label: 'Delivery Plan',
        description: 'Milestones, work items, and timeline',
        icon: '📅',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'deploy', param: 'configure', paramType: 'segment' },
        requiredUpstream: [LifecycleArtifactKind.VALIDATION_REPORT],
    },
    [LifecycleArtifactKind.RELEASE_STRATEGY]: {
        kind: LifecycleArtifactKind.RELEASE_STRATEGY,
        phase: LifecyclePhase.GENERATE,
        label: 'Release Strategy',
        description: 'Rollout strategy, feature flags, and rollback plan',
        icon: '🚀',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'deploy', param: 'configure', paramType: 'segment' },
        requiredUpstream: [LifecycleArtifactKind.DELIVERY_PLAN],
    },

    // RUN Phase
    [LifecycleArtifactKind.EVIDENCE_PACK]: {
        kind: LifecycleArtifactKind.EVIDENCE_PACK,
        phase: LifecyclePhase.RUN,
        label: 'Evidence Pack',
        description: 'Build artifacts, test results, and security scans',
        icon: '📦',
        defaultArtifactType: 'REPORT',
        placement: { surface: 'deploy', param: 'deployments', paramType: 'segment' },
        requiredUpstream: [LifecycleArtifactKind.RELEASE_STRATEGY],
    },
    [LifecycleArtifactKind.RELEASE_PACKET]: {
        kind: LifecycleArtifactKind.RELEASE_PACKET,
        phase: LifecyclePhase.RUN,
        label: 'Release Packet',
        description: 'Release notes, FAQ, and runbook',
        icon: '📝',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'deploy', param: 'deployments', paramType: 'segment' },
        requiredUpstream: [LifecycleArtifactKind.EVIDENCE_PACK],
    },

    // OBSERVE Phase
    [LifecycleArtifactKind.OPS_BASELINE]: {
        kind: LifecycleArtifactKind.OPS_BASELINE,
        phase: LifecyclePhase.OBSERVE,
        label: 'Operational Baseline',
        description: 'SLO targets, baseline metrics, and dashboards',
        icon: '📊',
        defaultArtifactType: 'REPORT',
        placement: { surface: 'deploy', param: 'health', paramType: 'segment' },
        requiredUpstream: [LifecycleArtifactKind.RELEASE_PACKET],
    },
    [LifecycleArtifactKind.INCIDENT_REPORT]: {
        kind: LifecycleArtifactKind.INCIDENT_REPORT,
        phase: LifecyclePhase.OBSERVE,
        label: 'Incident Report',
        description: 'Incident timeline, root cause, and mitigations',
        icon: '🚨',
        defaultArtifactType: 'REPORT',
        placement: { surface: 'deploy', param: 'health', paramType: 'segment' },
        requiredUpstream: [LifecycleArtifactKind.OPS_BASELINE],
    },

    // IMPROVE Phase
    [LifecycleArtifactKind.ENHANCEMENT_REQUESTS]: {
        kind: LifecycleArtifactKind.ENHANCEMENT_REQUESTS,
        phase: LifecyclePhase.IMPROVE,
        label: 'Enhancement Requests',
        description: 'Feature ideas, improvements, and prioritized backlog',
        icon: '💎',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'canvas', param: 'improve', paramType: 'panel' },
        requiredUpstream: [],
    },
    [LifecycleArtifactKind.LEARNING_RECORD]: {
        kind: LifecycleArtifactKind.LEARNING_RECORD,
        phase: LifecyclePhase.IMPROVE,
        label: 'Learning Record',
        description: 'Retrospective findings and process improvements',
        icon: '📚',
        defaultArtifactType: 'DOCUMENT',
        placement: { surface: 'canvas', param: 'improve', paramType: 'panel' },
        requiredUpstream: [LifecycleArtifactKind.ENHANCEMENT_REQUESTS],
    },
};

// ============================================================================
// Artifact Templates (Zod Schemas)
// ============================================================================

/**
 * Idea Brief template structure.
 */
export interface IdeaBriefPayload {
    title: string;
    oneLiner: string;
    targetUsers: string[];
    businessValue: string;
    constraints: string[];
    assumptions: string[];
}

/**
 * Research Pack template structure.
 */
export interface ResearchPackPayload {
    sources: { name: string; url?: string; type: string }[];
    marketNotes: string;
    userInsights: string[];
    risks: string[];
    openQuestions: string[];
}

/**
 * Problem Statement template structure.
 */
export interface ProblemStatementPayload {
    problem: string;
    who: string;
    when: string;
    whyNow: string;
    successMetrics: { name: string; target: string; current?: string }[];
    nonGoals: string[];
}

/**
 * Requirements template structure.
 */
export interface RequirementsPayload {
    epics: {
        id: string;
        title: string;
        description?: string;
        capabilities: {
            id: string;
            title: string;
            requirements: {
                id: string;
                statement: string;
                priority: 'must' | 'should' | 'could' | 'wont';
                acceptanceCriteria: string[];
                nfrTags: string[];
            }[];
        }[];
    }[];
}

/**
 * ADR template structure.
 */
export interface AdrPayload {
    context: string;
    decision: string;
    options: { name: string; pros: string[]; cons: string[] }[];
    consequences: string;
    status: 'proposed' | 'accepted' | 'superseded' | 'deprecated';
}

/**
 * UX Spec template structure.
 */
export interface UxSpecPayload {
    primaryFlows: { name: string; steps: string[]; notes?: string }[];
    iaNotes: string;
    a11yNotes: string;
    contentNotes: string;
    edgeCases: string[];
}

/**
 * Threat Model template structure.
 */
export interface ThreatModelPayload {
    assets: { name: string; description: string }[];
    actors: { name: string; description: string; type: 'internal' | 'external' }[];
    threats: {
        asset: string;
        category: 'spoofing' | 'tampering' | 'repudiation' | 'info_disclosure' | 'denial_of_service' | 'elevation';
        description: string;
        severity: 'low' | 'medium' | 'high' | 'critical';
    }[];
    mitigations: { threat: string; control: string; status: 'planned' | 'implemented' | 'verified' }[];
    residualRisk: string;
}

/**
 * Validation Report template structure.
 */
export interface ValidationReportPayload {
    coverageSummary: { total: number; passed: number; failed: number; skipped: number };
    a11yFindings: { issue: string; severity: string; recommendation: string }[];
    perfFindings: { metric: string; value: string; threshold: string; status: 'pass' | 'fail' }[];
    riskFindings: { risk: string; impact: string; likelihood: string; mitigation?: string }[];
    recommendations: string[];
}

/**
 * Simulation Results template structure.
 */
export interface SimulationResultsPayload {
    scenarios: { name: string; description: string; preconditions: string[] }[];
    outcomes: { scenario: string; result: 'pass' | 'fail' | 'partial'; notes: string }[];
    edgeCases: { case: string; behavior: string; status: 'handled' | 'unhandled' }[];
    confidenceScore: number; // 0-100
}

/**
 * Delivery Plan template structure.
 */
export interface DeliveryPlanPayload {
    milestones: { name: string; date: string; deliverables: string[] }[];
    workItems: { id: string; title: string; milestone: string; owner?: string; estimate?: string }[];
    dependencies: { from: string; to: string; type: 'blocks' | 'required_by' }[];
    estimates: { totalDays: number; confidence: 'low' | 'medium' | 'high' };
    owners: { name: string; role: string }[];
}

/**
 * Release Strategy template structure.
 */
export interface ReleaseStrategyPayload {
    strategyType: 'big_bang' | 'phased' | 'canary' | 'blue_green' | 'feature_flag';
    rolloutStages: { name: string; percentage: number; duration: string; criteria: string }[];
    featureFlags: { name: string; defaultValue: boolean; description: string }[];
    rollbackPlan: string;
    verificationSteps: { step: string; owner: string; criteria: string }[];
}

/**
 * Evidence Pack template structure.
 */
export interface EvidencePackPayload {
    builds: { id: string; status: 'success' | 'failure'; timestamp: string; artifacts: string[] }[];
    tests: { suite: string; passed: number; failed: number; skipped: number }[];
    security: { scan: string; findings: number; critical: number; high: number }[];
    approvals: { approver: string; timestamp: string; status: 'approved' | 'rejected' }[];
    links: { name: string; url: string }[];
}

/**
 * Release Packet template structure.
 */
export interface ReleasePacketPayload {
    releaseNotes: string;
    faq: { question: string; answer: string }[];
    runbook: string;
    supportContacts: { name: string; role: string; contact: string }[];
}

/**
 * Ops Baseline template structure.
 */
export interface OpsBaselinePayload {
    sloTargets: { name: string; target: string; current?: string }[];
    baselineMetrics: { metric: string; p50: string; p95: string; p99: string }[];
    dashboards: { name: string; url: string }[];
    alerts: { name: string; condition: string; severity: string }[];
}

/**
 * Incident Report template structure.
 */
export interface IncidentReportPayload {
    timeline: { timestamp: string; event: string; actor?: string }[];
    impact: string;
    rootCause: string;
    mitigations: { action: string; status: 'pending' | 'completed'; owner?: string }[];
    followUps: { action: string; dueDate: string; owner: string }[];
}

/**
 * Enhancement Requests template structure.
 */
export interface EnhancementRequestsPayload {
    items: {
        title: string;
        userValue: string;
        impact: 'low' | 'medium' | 'high';
        effort: 'low' | 'medium' | 'high';
        category: 'quick_win' | 'polish' | 'scale' | 'tech_debt' | 'feature';
        notes?: string;
    }[];
}

/**
 * Learning Record template structure.
 */
export interface LearningRecordPayload {
    whatHappened: string;
    whatWorked: string[];
    whatDidNot: string[];
    decisions: string[];
    actionItems: { action: string; owner: string; dueDate?: string }[];
}

/**
 * Union type of all artifact payloads.
 */
export type LifecycleArtifactPayload =
    | IdeaBriefPayload
    | ResearchPackPayload
    | ProblemStatementPayload
    | RequirementsPayload
    | AdrPayload
    | UxSpecPayload
    | ThreatModelPayload
    | ValidationReportPayload
    | SimulationResultsPayload
    | DeliveryPlanPayload
    | ReleaseStrategyPayload
    | EvidencePackPayload
    | ReleasePacketPayload
    | OpsBaselinePayload
    | IncidentReportPayload
    | EnhancementRequestsPayload
    | LearningRecordPayload;

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Gets artifacts for a specific phase.
 */
export function getArtifactsForPhase(phase: LifecyclePhase): LifecycleArtifactMetadata[] {
    return Object.values(LIFECYCLE_ARTIFACT_CATALOG).filter((a) => a.phase === phase);
}

/**
 * Gets artifacts for a specific surface.
 */
export function getArtifactsForSurface(
    surface: 'app' | 'canvas' | 'preview' | 'deploy'
): LifecycleArtifactMetadata[] {
    return Object.values(LIFECYCLE_ARTIFACT_CATALOG).filter(
        (a) => a.placement.surface === surface
    );
}

/**
 * Gets the full dependency chain for an artifact (all upstream dependencies).
 */
export function getArtifactDependencyChain(kind: LifecycleArtifactKind): LifecycleArtifactKind[] {
    const chain: LifecycleArtifactKind[] = [];
    const visited = new Set<LifecycleArtifactKind>();

    function traverse(k: LifecycleArtifactKind) {
        if (visited.has(k)) return;
        visited.add(k);

        const meta = LIFECYCLE_ARTIFACT_CATALOG[k];
        for (const upstream of meta.requiredUpstream) {
            traverse(upstream);
        }
        chain.push(k);
    }

    traverse(kind);
    // Remove the artifact itself from the chain (we only want upstream)
    chain.pop();
    return chain;
}

/**
 * Validates that all upstream dependencies exist for an artifact.
 */
export function validateArtifactDependencies(
    kind: LifecycleArtifactKind,
    existingArtifacts: Set<LifecycleArtifactKind>
): { valid: boolean; missing: LifecycleArtifactKind[] } {
    const meta = LIFECYCLE_ARTIFACT_CATALOG[kind];
    const missing = meta.requiredUpstream.filter((k) => !existingArtifacts.has(k));
    return { valid: missing.length === 0, missing };
}
