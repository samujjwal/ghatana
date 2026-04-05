/**
 * Lifecycle Artifact Types
 *
 * Complete type system for YAPPC lifecycle artifacts.
 * Covers the 7-phase model (INTENT → SHAPE → VALIDATE → GENERATE → RUN → OBSERVE → IMPROVE).
 *
 * @doc.type types
 * @doc.purpose Lifecycle artifact type definitions and catalog
 * @doc.layer product
 * @doc.pattern Domain Types
 */

import { LifecyclePhase } from '@/types/lifecycle';

// ============================================================================
// LifecycleArtifactKind — const + type pattern for enum-style access
// ============================================================================

/**
 * All supported artifact kinds. Usable both as a value namespace
 * (LifecycleArtifactKind.IDEA_BRIEF) and as a discriminated string literal type.
 */
export const LifecycleArtifactKind = {
  IDEA_BRIEF: 'idea_brief',
  RESEARCH_PACK: 'research_pack',
  PROBLEM_STATEMENT: 'problem_statement',
  REQUIREMENTS: 'requirements',
  ADR: 'adr',
  UX_SPEC: 'ux_spec',
  THREAT_MODEL: 'threat_model',
  VALIDATION_REPORT: 'validation_report',
  SIMULATION_RESULTS: 'simulation_results',
  DELIVERY_PLAN: 'delivery_plan',
  RELEASE_STRATEGY: 'release_strategy',
  EVIDENCE_PACK: 'evidence_pack',
  RELEASE_PACKET: 'release_packet',
  OPS_BASELINE: 'ops_baseline',
  INCIDENT_REPORT: 'incident_report',
  ENHANCEMENT_REQUESTS: 'enhancement_requests',
  LEARNING_RECORD: 'learning_record',
} as const;

export type LifecycleArtifactKind =
  (typeof LifecycleArtifactKind)[keyof typeof LifecycleArtifactKind];

// ============================================================================
// Payload Types — kind-specific artifact content shapes
// ============================================================================

export interface IdeaBriefPayload {
  title: string;
  oneLiner: string;
  targetUsers: string[];
  businessValue: string;
  constraints: string[];
  assumptions: string[];
}

export interface ResearchPackPayload {
  sources: string[];
  marketNotes: string;
  userInsights: string[];
  risks: string[];
  openQuestions: string[];
}

export interface ProblemStatementPayload {
  problem: string;
  who: string;
  when: string;
  whyNow: string;
  successMetrics: string[];
  nonGoals: string[];
}

export interface RequirementsPayload {
  epics: Array<{
    id: string;
    title: string;
    stories: Array<{ id: string; title: string; points?: number }>;
  }>;
}

export interface AdrPayload {
  context: string;
  decision: string;
  options: Array<{ title: string; pros: string[]; cons: string[] }>;
  consequences: string;
  status: 'proposed' | 'accepted' | 'deprecated' | 'superseded';
}

export interface UxSpecPayload {
  primaryFlows: Array<{ name: string; steps: string[] }>;
  iaNotes: string;
  a11yNotes: string;
  contentNotes: string;
  edgeCases: string[];
}

export interface ThreatModelPayload {
  assets: string[];
  actors: string[];
  threats: Array<{
    id: string;
    category: string;
    description: string;
    severity: string;
  }>;
  mitigations: string[];
  residualRisk: string;
}

export interface ValidationReportPayload {
  coverageSummary: {
    total: number;
    passed: number;
    failed: number;
    skipped: number;
  };
  a11yFindings: string[];
  perfFindings: string[];
  riskFindings: string[];
  recommendations: string[];
}

export interface SimulationResultsPayload {
  scenarios: string[];
  outcomes: string[];
  edgeCases: string[];
  confidenceScore: number;
}

export interface DeliveryPlanPayload {
  milestones: Array<{ title: string; dueDate: string; items: string[] }>;
  workItems: Array<{
    id: string;
    title: string;
    phase: string;
    estimate?: number;
  }>;
}

export interface ReleaseStrategyPayload {
  releaseType: 'rolling' | 'blue-green' | 'canary' | 'feature-flag';
  environments: string[];
  rolloutSteps: string[];
  featureFlags: string[];
  rollbackPlan: string;
}

export interface EvidencePackPayload {
  testResults: string[];
  securityScans: string[];
  buildArtifacts: string[];
  complianceChecks: string[];
}

export interface ReleasePacketPayload {
  releaseNotes: string;
  faq: Array<{ q: string; a: string }>;
  runbook: string;
  contacts: string[];
}

export interface OpsBaselinePayload {
  sloTargets: Array<{ metric: string; target: number; unit: string }>;
  baselineMetrics: Array<{ name: string; value: number; unit: string }>;
  dashboards: string[];
  alerts: Array<{ name: string; condition: string; severity: string }>;
}

export interface IncidentReportPayload {
  timeline: Array<{ timestamp: string; event: string; user?: string }>;
  rootCause: string;
  impact: string;
  mitigations: string[];
  postMortemUrl: string;
}

export interface EnhancementBacklogPayload {
  requests: Array<{
    id: string;
    title: string;
    priority: string;
    votes?: number;
  }>;
  prioritization: string[];
}

export interface LearningRecordPayload {
  retrospectives: Array<{
    date: string;
    summary: string;
    actionItems: string[];
  }>;
  insights: string[];
  recommendations: string[];
}

// ============================================================================
// Placement — surface + navigation param
// ============================================================================

export interface ArtifactPlacement {
  /** The app surface that hosts this artifact */
  surface: 'app' | 'canvas' | 'preview' | 'deploy';
  /** Query param type used to navigate to the artifact */
  paramType: 'tab' | 'panel' | 'drawer' | 'stage';
  /** Query param value */
  param: string;
}

// ============================================================================
// Metadata — per-kind catalog entry
// ============================================================================

export interface LifecycleArtifactMetadata {
  kind: LifecycleArtifactKind;
  label: string;
  description: string;
  icon: string;
  phase: LifecyclePhase;
  requiredUpstream: LifecycleArtifactKind[];
  placement: ArtifactPlacement;
}

// ============================================================================
// LIFECYCLE_ARTIFACT_CATALOG — single source of truth
// ============================================================================

export const LIFECYCLE_ARTIFACT_CATALOG: Record<
  LifecycleArtifactKind,
  LifecycleArtifactMetadata
> = {
  idea_brief: {
    kind: 'idea_brief',
    label: 'Idea Brief',
    description: 'Initial idea with target users and value proposition',
    icon: '💡',
    phase: LifecyclePhase.INTENT,
    requiredUpstream: [],
    placement: { surface: 'app', paramType: 'drawer', param: 'idea' },
  },
  research_pack: {
    kind: 'research_pack',
    label: 'Research Pack',
    description: 'Market research, user insights, and competitive analysis',
    icon: '🔍',
    phase: LifecyclePhase.INTENT,
    requiredUpstream: ['idea_brief'],
    placement: { surface: 'app', paramType: 'drawer', param: 'research' },
  },
  problem_statement: {
    kind: 'problem_statement',
    label: 'Problem Statement',
    description: 'Refined problem with success metrics and non-goals',
    icon: '🎯',
    phase: LifecyclePhase.INTENT,
    requiredUpstream: ['idea_brief', 'research_pack'],
    placement: { surface: 'app', paramType: 'drawer', param: 'problem' },
  },
  requirements: {
    kind: 'requirements',
    label: 'Requirements',
    description: 'Epics and user stories for the system',
    icon: '📋',
    phase: LifecyclePhase.SHAPE,
    requiredUpstream: ['problem_statement'],
    placement: { surface: 'canvas', paramType: 'panel', param: 'requirements' },
  },
  adr: {
    kind: 'adr',
    label: 'Architecture Decision Record',
    description: 'Key architectural decisions with context and rationale',
    icon: '🏛️',
    phase: LifecyclePhase.SHAPE,
    requiredUpstream: ['requirements'],
    placement: { surface: 'canvas', paramType: 'panel', param: 'adr' },
  },
  ux_spec: {
    kind: 'ux_spec',
    label: 'UX Specification',
    description:
      'User flows, information architecture, and accessibility notes',
    icon: '🎨',
    phase: LifecyclePhase.SHAPE,
    requiredUpstream: ['requirements'],
    placement: { surface: 'canvas', paramType: 'panel', param: 'ux-spec' },
  },
  threat_model: {
    kind: 'threat_model',
    label: 'Threat Model',
    description: 'Security threats, mitigations, and residual risk assessment',
    icon: '🔒',
    phase: LifecyclePhase.VALIDATE,
    requiredUpstream: ['requirements', 'adr'],
    placement: {
      surface: 'preview',
      paramType: 'panel',
      param: 'threat-model',
    },
  },
  validation_report: {
    kind: 'validation_report',
    label: 'Validation Report',
    description: 'Test coverage, accessibility, and performance findings',
    icon: '✅',
    phase: LifecyclePhase.VALIDATE,
    requiredUpstream: ['ux_spec', 'threat_model'],
    placement: { surface: 'preview', paramType: 'panel', param: 'validation' },
  },
  simulation_results: {
    kind: 'simulation_results',
    label: 'Simulation Results',
    description: 'Scenario outcomes and confidence scoring',
    icon: '🧪',
    phase: LifecyclePhase.VALIDATE,
    requiredUpstream: ['validation_report'],
    placement: { surface: 'preview', paramType: 'panel', param: 'simulation' },
  },
  delivery_plan: {
    kind: 'delivery_plan',
    label: 'Delivery Plan',
    description: 'Milestones, work items, and delivery schedule',
    icon: '📅',
    phase: LifecyclePhase.GENERATE,
    requiredUpstream: ['simulation_results'],
    placement: {
      surface: 'canvas',
      paramType: 'panel',
      param: 'delivery-plan',
    },
  },
  release_strategy: {
    kind: 'release_strategy',
    label: 'Release Strategy',
    description: 'Rollout approach, environments, and rollback plan',
    icon: '🚀',
    phase: LifecyclePhase.GENERATE,
    requiredUpstream: ['delivery_plan'],
    placement: {
      surface: 'canvas',
      paramType: 'panel',
      param: 'release-strategy',
    },
  },
  evidence_pack: {
    kind: 'evidence_pack',
    label: 'Evidence Pack',
    description: 'Test results, security scans, and compliance checks',
    icon: '📦',
    phase: LifecyclePhase.RUN,
    requiredUpstream: ['release_strategy'],
    placement: { surface: 'deploy', paramType: 'tab', param: 'evidence' },
  },
  release_packet: {
    kind: 'release_packet',
    label: 'Release Packet',
    description: 'Release notes, FAQ, and runbook for operations',
    icon: '📄',
    phase: LifecyclePhase.RUN,
    requiredUpstream: ['evidence_pack'],
    placement: { surface: 'deploy', paramType: 'tab', param: 'release-packet' },
  },
  ops_baseline: {
    kind: 'ops_baseline',
    label: 'Ops Baseline',
    description: 'SLO targets, baseline metrics, and alert definitions',
    icon: '📊',
    phase: LifecyclePhase.OBSERVE,
    requiredUpstream: ['release_packet'],
    placement: { surface: 'deploy', paramType: 'tab', param: 'ops-baseline' },
  },
  incident_report: {
    kind: 'incident_report',
    label: 'Incident Report',
    description: 'Incident timeline, root cause, impact, and mitigations',
    icon: '🚨',
    phase: LifecyclePhase.OBSERVE,
    requiredUpstream: ['ops_baseline'],
    placement: { surface: 'deploy', paramType: 'tab', param: 'incidents' },
  },
  enhancement_requests: {
    kind: 'enhancement_requests',
    label: 'Enhancement Requests',
    description: 'Backlog of improvement requests from observations',
    icon: '✨',
    phase: LifecyclePhase.IMPROVE,
    requiredUpstream: ['incident_report'],
    placement: { surface: 'deploy', paramType: 'tab', param: 'enhancements' },
  },
  learning_record: {
    kind: 'learning_record',
    label: 'Learning Record',
    description: 'Retrospective insights and recommendations',
    icon: '📚',
    phase: LifecyclePhase.IMPROVE,
    requiredUpstream: ['enhancement_requests'],
    placement: { surface: 'deploy', paramType: 'tab', param: 'learning' },
  },
};

// ============================================================================
// Helpers
// ============================================================================

/**
 * Get all artifact kinds associated with a given lifecycle phase.
 */
export function getArtifactsForPhase(
  phase: LifecyclePhase
): LifecycleArtifactKind[] {
  return (
    Object.values(LIFECYCLE_ARTIFACT_CATALOG) as LifecycleArtifactMetadata[]
  )
    .filter((m) => m.phase === phase)
    .map((m) => m.kind);
}

/**
 * Create a consistent tag string for an artifact kind.
 */
export function createArtifactTag(kind: LifecycleArtifactKind): string {
  return `artifact:${kind}`;
}
