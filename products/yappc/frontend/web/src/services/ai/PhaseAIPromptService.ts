/**
 * Phase-Specific AI Prompt Service
 * 
 * Provides intelligent, context-aware prompts and suggestions tailored to each lifecycle phase.
 * Generates recommendations based on phase requirements, existing artifacts, and best practices.
 * 
 * @doc.type service
 * @doc.purpose Phase-aware AI prompt generation
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import type { LifecyclePhase } from '@/shared/types/lifecycle';
import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

export interface PhasePrompt {
    phase: LifecyclePhase;
    title: string;
    description: string;
    suggestedActions: string[];
    artifactFocus: LifecycleArtifactKind[];
    qualityChecks: string[];
    commonPitfalls: string[];
}

export interface PhaseAIContext {
    phase: LifecyclePhase;
    projectContext?: string;
    existingArtifacts: LifecycleArtifactKind[];
    teamSize?: number;
    industry?: string;
}

export interface PhaseAISuggestion {
    type: 'create' | 'improve' | 'validate' | 'link';
    priority: 'high' | 'medium' | 'low';
    title: string;
    description: string;
    artifactKind?: LifecycleArtifactKind;
    reasoning: string;
    estimatedEffort?: string;
}

/**
 * Phase-specific prompt templates
 */
const PHASE_PROMPTS: Record<LifecyclePhase, PhasePrompt> = {
    INTENT: {
        phase: 'INTENT' as LifecyclePhase,
        title: 'Define & Research',
        description: 'Focus on understanding the problem space and gathering insights',
        suggestedActions: [
            'Document the core problem or opportunity',
            'Research existing solutions and market landscape',
            'Identify key stakeholders and their needs',
            'Define success criteria and constraints',
        ],
        artifactFocus: [LifecycleArtifactKind.IDEA_BRIEF, LifecycleArtifactKind.RESEARCH_PACK, LifecycleArtifactKind.PROBLEM_STATEMENT],
        qualityChecks: [
            'Is the problem clearly articulated?',
            'Have you researched similar solutions?',
            'Are stakeholder needs documented?',
            'Is the problem worth solving?',
        ],
        commonPitfalls: [
            'Jumping to solutions before understanding the problem',
            'Insufficient research on existing solutions',
            'Vague or overly broad problem statements',
            'Ignoring stakeholder perspectives',
        ],
    },
    SHAPE: {
        phase: 'SHAPE' as LifecyclePhase,
        title: 'Design & Plan',
        description: 'Define the solution approach and technical architecture',
        suggestedActions: [
            'Define functional and non-functional requirements',
            'Document key architectural decisions',
            'Create UX specifications and user flows',
            'Identify security threats and mitigations',
        ],
        artifactFocus: [LifecycleArtifactKind.REQUIREMENTS, LifecycleArtifactKind.ADR, LifecycleArtifactKind.UX_SPEC, LifecycleArtifactKind.THREAT_MODEL],
        qualityChecks: [
            'Are requirements clear and testable?',
            'Have technical tradeoffs been documented?',
            'Is the UX flow validated with users?',
            'Are security risks identified?',
        ],
        commonPitfalls: [
            'Overly detailed requirements that constrain design',
            'Undocumented architectural decisions',
            'Skipping UX validation',
            'Security as an afterthought',
        ],
    },
    VALIDATE: {
        phase: 'VALIDATE' as LifecyclePhase,
        title: 'Test & Verify',
        description: 'Validate assumptions and test the proposed solution',
        suggestedActions: [
            'Create validation criteria for key assumptions',
            'Run simulations or proof-of-concepts',
            'Gather feedback from stakeholders',
            'Validate technical feasibility',
        ],
        artifactFocus: [LifecycleArtifactKind.VALIDATION_REPORT, LifecycleArtifactKind.SIMULATION_RESULTS],
        qualityChecks: [
            'Have critical assumptions been tested?',
            'Is there evidence supporting the approach?',
            'Have stakeholders reviewed and approved?',
            'Are risks identified and mitigated?',
        ],
        commonPitfalls: [
            'Confirmation bias in validation',
            'Insufficient test coverage',
            'Ignoring negative results',
            'Skipping stakeholder validation',
        ],
    },
    GENERATE: {
        phase: 'GENERATE' as LifecyclePhase,
        title: 'Build & Package',
        description: 'Execute the plan and prepare for deployment',
        suggestedActions: [
            'Create detailed delivery plan',
            'Define release strategy and rollback plan',
            'Prepare deployment artifacts',
            'Set up monitoring and alerting',
        ],
        artifactFocus: [LifecycleArtifactKind.DELIVERY_PLAN, LifecycleArtifactKind.RELEASE_STRATEGY],
        qualityChecks: [
            'Is the delivery plan realistic?',
            'Are rollback procedures defined?',
            'Is the release strategy documented?',
            'Are success metrics defined?',
        ],
        commonPitfalls: [
            'Underestimating deployment complexity',
            'No rollback plan',
            'Insufficient testing in production-like environment',
            'Missing success criteria',
        ],
    },
    RUN: {
        phase: 'RUN' as LifecyclePhase,
        title: 'Deploy & Monitor',
        description: 'Execute the release and ensure smooth operation',
        suggestedActions: [
            'Collect evidence of successful deployment',
            'Package release documentation',
            'Monitor key metrics',
            'Communicate status to stakeholders',
        ],
        artifactFocus: [LifecycleArtifactKind.EVIDENCE_PACK, LifecycleArtifactKind.RELEASE_PACKET],
        qualityChecks: [
            'Is deployment evidence documented?',
            'Are all stakeholders informed?',
            'Are metrics showing expected results?',
            'Is the release packet complete?',
        ],
        commonPitfalls: [
            'Poor communication during release',
            'Insufficient monitoring',
            'Missing deployment documentation',
            'Ignoring early warning signs',
        ],
    },
    OBSERVE: {
        phase: 'OBSERVE' as LifecyclePhase,
        title: 'Monitor & Respond',
        description: 'Track system health and respond to incidents',
        suggestedActions: [
            'Establish operational baseline',
            'Monitor key performance indicators',
            'Document incidents and resolutions',
            'Gather operational insights',
        ],
        artifactFocus: [LifecycleArtifactKind.OPS_BASELINE, LifecycleArtifactKind.INCIDENT_REPORT],
        qualityChecks: [
            'Is the baseline documented?',
            'Are incidents tracked and resolved?',
            'Are SLOs being met?',
            'Is operational data being collected?',
        ],
        commonPitfalls: [
            'Reactive instead of proactive monitoring',
            'Poor incident documentation',
            'No baseline for comparison',
            'Alert fatigue from noise',
        ],
    },
    IMPROVE: {
        phase: 'IMPROVE' as LifecyclePhase,
        title: 'Learn & Enhance',
        description: 'Capture learnings and plan enhancements',
        suggestedActions: [
            'Document lessons learned',
            'Prioritize enhancement backlog',
            'Share knowledge with team',
            'Plan next iteration',
        ],
        artifactFocus: [LifecycleArtifactKind.ENHANCEMENT_REQUESTS, LifecycleArtifactKind.LEARNING_RECORD],
        qualityChecks: [
            'Are learnings documented?',
            'Is the backlog prioritized?',
            'Has knowledge been shared?',
            'Are improvements planned?',
        ],
        commonPitfalls: [
            'Not capturing lessons learned',
            'Backlog becomes a dumping ground',
            'Knowledge stays siloed',
            'Continuous improvement ignored',
        ],
    },
};

/**
 * Generate phase-specific AI suggestions
 */
export async function generatePhaseAISuggestions(
    context: PhaseAIContext
): Promise<PhaseAISuggestion[]> {
    const phasePrompt = PHASE_PROMPTS[context.phase];
    const suggestions: PhaseAISuggestion[] = [];

    // 1. Check for missing critical artifacts
    const missingArtifacts = phasePrompt.artifactFocus.filter(
        kind => !context.existingArtifacts.includes(kind)
    );

    missingArtifacts.forEach(kind => {
        suggestions.push({
            type: 'create',
            priority: 'high',
            title: `Create ${kind.replace(/_/g, ' ')}`,
            description: `This artifact is essential for the ${phasePrompt.title} phase`,
            artifactKind: kind,
            reasoning: `${kind} is a core deliverable for this phase and helps ensure quality outcomes`,
            estimatedEffort: '2-4 hours',
        });
    });

    // 2. Quality improvement suggestions
    if (context.existingArtifacts.length > 0) {
        suggestions.push({
            type: 'improve',
            priority: 'medium',
            title: 'Review artifact quality',
            description: `Ensure your ${context.phase.toLowerCase()} artifacts pass all quality checks`,
            reasoning: 'Quality checks help catch issues early and improve outcomes',
            estimatedEffort: '1-2 hours',
        });
    }

    // 3. Phase transition readiness
    const completionRate = context.existingArtifacts.length / phasePrompt.artifactFocus.length;
    if (completionRate >= 0.7) {
        suggestions.push({
            type: 'validate',
            priority: 'medium',
            title: 'Prepare for phase transition',
            description: 'Review completeness before moving to the next phase',
            reasoning: 'Ensuring phase completion reduces rework and improves flow',
            estimatedEffort: '30 minutes',
        });
    }

    // 4. Cross-artifact linking suggestions
    if (context.existingArtifacts.length >= 2) {
        suggestions.push({
            type: 'link',
            priority: 'low',
            title: 'Link related artifacts',
            description: 'Establish traceability between artifacts',
            reasoning: 'Linking artifacts improves navigation and understanding',
            estimatedEffort: '15 minutes',
        });
    }

    return suggestions;
}

/**
 * Get phase-specific prompt
 */
export function getPhasePrompt(phase: LifecyclePhase): PhasePrompt {
    return PHASE_PROMPTS[phase];
}

/**
 * Get next suggested action for a phase
 */
export function getNextAction(
    phase: LifecyclePhase,
    existingArtifacts: LifecycleArtifactKind[]
): string {
    const phasePrompt = PHASE_PROMPTS[phase];
    const missingArtifacts = phasePrompt.artifactFocus.filter(
        kind => !existingArtifacts.includes(kind)
    );

    if (missingArtifacts.length > 0) {
        return `Create ${missingArtifacts[0].replace(/_/g, ' ')}`;
    }

    return phasePrompt.suggestedActions[0];
}

/**
 * Check if phase is ready for transition
 */
export function isPhaseReady(
    phase: LifecyclePhase,
    existingArtifacts: LifecycleArtifactKind[]
): { ready: boolean; reason: string; missingArtifacts: LifecycleArtifactKind[] } {
    const phasePrompt = PHASE_PROMPTS[phase];
    const missingArtifacts = phasePrompt.artifactFocus.filter(
        kind => !existingArtifacts.includes(kind)
    );

    if (missingArtifacts.length === 0) {
        return {
            ready: true,
            reason: `All ${phase} artifacts are complete`,
            missingArtifacts: [],
        };
    }

    return {
        ready: false,
        reason: `${missingArtifacts.length} artifacts still needed`,
        missingArtifacts,
    };
}
