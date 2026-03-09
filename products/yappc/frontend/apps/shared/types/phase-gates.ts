/**
 * Phase Gate System
 *
 * Defines phase gates for transitioning between YAPPC lifecycle phases.
 * Gates enforce artifact completeness and validation rules.
 *
 * @doc.type shared-types
 * @doc.purpose Phase gate definitions and validation
 * @doc.layer shared
 * @doc.pattern Type System
 */

import { LifecyclePhase } from './lifecycle';
import { LifecycleArtifactKind } from './lifecycle-artifacts';

// ============================================================================
// Gate Types
// ============================================================================

/**
 * Summary of an Item for gate context.
 */
export interface ItemSummary {
    id: string;
    title: string;
    artifactKind: LifecycleArtifactKind;
    status: 'draft' | 'complete' | 'validated';
    lastUpdated: string;
}

/**
 * Context provided to gate validation functions.
 */
export interface GateContext {
    projectId: string;
    currentPhase: LifecyclePhase;
    targetPhase: LifecyclePhase;
    lifecycleArtifactItemsByKind: Partial<Record<LifecycleArtifactKind, ItemSummary>>;
    userId?: string;
}

/**
 * Result of a single validation rule check.
 */
export interface ValidationResult {
    passed: boolean;
    errors: string[];
    warnings: string[];
    infos: string[];
}

/**
 * Validation rule definition.
 */
export interface ValidationRule {
    id: string;
    name: string;
    description: string;
    severity: 'error' | 'warning' | 'info';
    check: (context: GateContext) => ValidationResult | Promise<ValidationResult>;
}

/**
 * Phase gate definition.
 */
export interface PhaseGate {
    id: string;
    fromPhase: LifecyclePhase;
    toPhase: LifecyclePhase;
    name: string;
    description: string;
    requiredLifecycleArtifacts: LifecycleArtifactKind[];
    optionalLifecycleArtifacts: LifecycleArtifactKind[];
    validationRules: ValidationRule[];
    canBypass: boolean;
    bypassRoles?: string[];
}

/**
 * Status of a phase gate for a project.
 */
export interface GateStatus {
    gateId: string;
    status: 'blocked' | 'ready' | 'passed' | 'bypassed';
    missingArtifacts: LifecycleArtifactKind[];
    validationResults: ValidationResult[];
    canBypass: boolean;
    blockedReason?: string;
    lastChecked: string;
}

// ============================================================================
// Validation Rules (Reusable)
// ============================================================================

/**
 * Creates a rule that checks if required artifacts exist.
 */
function createArtifactExistsRule(
    id: string,
    name: string,
    requiredArtifacts: LifecycleArtifactKind[]
): ValidationRule {
    return {
        id,
        name,
        description: `Checks that ${requiredArtifacts.join(', ')} artifacts exist`,
        severity: 'error',
        check: (context: GateContext): ValidationResult => {
            const missing = requiredArtifacts.filter(
                (kind) => !context.lifecycleArtifactItemsByKind[kind]
            );
            return {
                passed: missing.length === 0,
                errors: missing.map((k) => `Missing required artifact: ${k}`),
                warnings: [],
                infos: [],
            };
        },
    };
}

/**
 * Creates a rule that checks artifact status.
 */
function createArtifactStatusRule(
    id: string,
    name: string,
    artifacts: LifecycleArtifactKind[],
    requiredStatus: 'complete' | 'validated'
): ValidationRule {
    return {
        id,
        name,
        description: `Checks that ${artifacts.join(', ')} are ${requiredStatus}`,
        severity: 'error',
        check: (context: GateContext): ValidationResult => {
            const issues: string[] = [];
            for (const kind of artifacts) {
                const item = context.lifecycleArtifactItemsByKind[kind];
                if (item && item.status !== requiredStatus) {
                    issues.push(`${kind} is ${item.status}, expected ${requiredStatus}`);
                }
            }
            return {
                passed: issues.length === 0,
                errors: issues,
                warnings: [],
                infos: [],
            };
        },
    };
}

// ============================================================================
// Phase Gates Definition
// ============================================================================

/**
 * Gate: INTENT → SHAPE
 * Ensures problem understanding is complete before shaping.
 */
const GATE_INTENT_TO_SHAPE: PhaseGate = {
    id: 'gate_intent_shape',
    fromPhase: LifecyclePhase.INTENT,
    toPhase: LifecyclePhase.SHAPE,
    name: 'Intent → Shape',
    description: 'Validate problem understanding before shaping the solution',
    requiredLifecycleArtifacts: [
        LifecycleArtifactKind.PROBLEM_STATEMENT,
    ],
    optionalLifecycleArtifacts: [
        LifecycleArtifactKind.IDEA_BRIEF,
        LifecycleArtifactKind.RESEARCH_PACK,
    ],
    validationRules: [
        createArtifactExistsRule(
            'intent_problem_exists',
            'Problem Statement Exists',
            [LifecycleArtifactKind.PROBLEM_STATEMENT]
        ),
        {
            id: 'intent_metrics_defined',
            name: 'Success Metrics Defined',
            description: 'Problem statement must have success metrics',
            severity: 'warning',
            check: (context: GateContext): ValidationResult => {
                const ps = context.lifecycleArtifactItemsByKind[LifecycleArtifactKind.PROBLEM_STATEMENT];
                if (!ps) {
                    return { passed: false, errors: [], warnings: ['No problem statement to check'], infos: [] };
                }
                // In real implementation, we'd check the artifact content
                return { passed: true, errors: [], warnings: [], infos: ['Success metrics should be defined'] };
            },
        },
    ],
    canBypass: false,
};

/**
 * Gate: SHAPE → VALIDATE
 * Ensures solution design is complete before validation.
 */
const GATE_SHAPE_TO_VALIDATE: PhaseGate = {
    id: 'gate_shape_validate',
    fromPhase: LifecyclePhase.SHAPE,
    toPhase: LifecyclePhase.VALIDATE,
    name: 'Shape → Validate',
    description: 'Validate solution design before testing',
    requiredLifecycleArtifacts: [
        LifecycleArtifactKind.REQUIREMENTS,
    ],
    optionalLifecycleArtifacts: [
        LifecycleArtifactKind.ADR,
        LifecycleArtifactKind.UX_SPEC,
        LifecycleArtifactKind.THREAT_MODEL,
    ],
    validationRules: [
        createArtifactExistsRule(
            'shape_requirements_exists',
            'Requirements Exist',
            [LifecycleArtifactKind.REQUIREMENTS]
        ),
        {
            id: 'shape_security_review',
            name: 'Security Consideration',
            description: 'Threat model should be created for security-sensitive projects',
            severity: 'warning',
            check: (context: GateContext): ValidationResult => {
                const tm = context.lifecycleArtifactItemsByKind[LifecycleArtifactKind.THREAT_MODEL];
                return {
                    passed: true,
                    errors: [],
                    warnings: tm ? [] : ['Consider creating a threat model for security review'],
                    infos: [],
                };
            },
        },
    ],
    canBypass: true,
    bypassRoles: ['ADMIN'],
};

/**
 * Gate: VALIDATE → GENERATE
 * Ensures validation is complete before generation.
 */
const GATE_VALIDATE_TO_GENERATE: PhaseGate = {
    id: 'gate_validate_generate',
    fromPhase: LifecyclePhase.VALIDATE,
    toPhase: LifecyclePhase.GENERATE,
    name: 'Validate → Generate',
    description: 'Confirm validation before generating delivery artifacts',
    requiredLifecycleArtifacts: [
        LifecycleArtifactKind.VALIDATION_REPORT,
    ],
    optionalLifecycleArtifacts: [
        LifecycleArtifactKind.SIMULATION_RESULTS,
    ],
    validationRules: [
        createArtifactExistsRule(
            'validate_report_exists',
            'Validation Report Exists',
            [LifecycleArtifactKind.VALIDATION_REPORT]
        ),
        createArtifactStatusRule(
            'validate_report_complete',
            'Validation Report Complete',
            [LifecycleArtifactKind.VALIDATION_REPORT],
            'complete'
        ),
    ],
    canBypass: true,
    bypassRoles: ['ADMIN'],
};

/**
 * Gate: GENERATE → RUN
 * Ensures delivery plan is ready before running.
 */
const GATE_GENERATE_TO_RUN: PhaseGate = {
    id: 'gate_generate_run',
    fromPhase: LifecyclePhase.GENERATE,
    toPhase: LifecyclePhase.RUN,
    name: 'Generate → Run',
    description: 'Confirm delivery plan before deployment',
    requiredLifecycleArtifacts: [
        LifecycleArtifactKind.DELIVERY_PLAN,
        LifecycleArtifactKind.RELEASE_STRATEGY,
    ],
    optionalLifecycleArtifacts: [],
    validationRules: [
        createArtifactExistsRule(
            'generate_plan_exists',
            'Delivery Plan Exists',
            [LifecycleArtifactKind.DELIVERY_PLAN]
        ),
        createArtifactExistsRule(
            'generate_strategy_exists',
            'Release Strategy Exists',
            [LifecycleArtifactKind.RELEASE_STRATEGY]
        ),
    ],
    canBypass: false,
};

/**
 * Gate: RUN → OBSERVE
 * Ensures deployment is complete before observing.
 */
const GATE_RUN_TO_OBSERVE: PhaseGate = {
    id: 'gate_run_observe',
    fromPhase: LifecyclePhase.RUN,
    toPhase: LifecyclePhase.OBSERVE,
    name: 'Run → Observe',
    description: 'Confirm deployment before monitoring',
    requiredLifecycleArtifacts: [
        LifecycleArtifactKind.EVIDENCE_PACK,
        LifecycleArtifactKind.RELEASE_PACKET,
    ],
    optionalLifecycleArtifacts: [],
    validationRules: [
        createArtifactExistsRule(
            'run_evidence_exists',
            'Evidence Pack Exists',
            [LifecycleArtifactKind.EVIDENCE_PACK]
        ),
        createArtifactStatusRule(
            'run_evidence_validated',
            'Evidence Pack Validated',
            [LifecycleArtifactKind.EVIDENCE_PACK],
            'validated'
        ),
    ],
    canBypass: false,
};

/**
 * Gate: OBSERVE → IMPROVE
 * Ensures operational baseline is established before improving.
 */
const GATE_OBSERVE_TO_IMPROVE: PhaseGate = {
    id: 'gate_observe_improve',
    fromPhase: LifecyclePhase.OBSERVE,
    toPhase: LifecyclePhase.IMPROVE,
    name: 'Observe → Improve',
    description: 'Establish operational baseline before improvement cycle',
    requiredLifecycleArtifacts: [
        LifecycleArtifactKind.OPS_BASELINE,
    ],
    optionalLifecycleArtifacts: [
        LifecycleArtifactKind.INCIDENT_REPORT,
    ],
    validationRules: [
        createArtifactExistsRule(
            'observe_baseline_exists',
            'Ops Baseline Exists',
            [LifecycleArtifactKind.OPS_BASELINE]
        ),
    ],
    canBypass: true,
    bypassRoles: ['ADMIN', 'EDITOR'],
};

// ============================================================================
// Exports
// ============================================================================

/**
 * All phase gates in order.
 */
export const PHASE_GATES: PhaseGate[] = [
    GATE_INTENT_TO_SHAPE,
    GATE_SHAPE_TO_VALIDATE,
    GATE_VALIDATE_TO_GENERATE,
    GATE_GENERATE_TO_RUN,
    GATE_RUN_TO_OBSERVE,
    GATE_OBSERVE_TO_IMPROVE,
];

/**
 * Map of phase gates by ID.
 */
export const PHASE_GATES_BY_ID: Record<string, PhaseGate> = Object.fromEntries(
    PHASE_GATES.map((g) => [g.id, g])
);

/**
 * Get the gate for a specific transition.
 */
export function getGateForTransition(
    from: LifecyclePhase,
    to: LifecyclePhase
): PhaseGate | undefined {
    return PHASE_GATES.find((g) => g.fromPhase === from && g.toPhase === to);
}

/**
 * Get all gates that must be passed to reach a target phase from current phase.
 */
export function getGatesForPath(
    from: LifecyclePhase,
    to: LifecyclePhase
): PhaseGate[] {
    const phases = Object.values(LifecyclePhase);
    const fromIndex = phases.indexOf(from);
    const toIndex = phases.indexOf(to);

    if (fromIndex >= toIndex) {
        return []; // Going backward doesn't require gates
    }

    const gates: PhaseGate[] = [];
    for (let i = fromIndex; i < toIndex; i++) {
        const gate = getGateForTransition(phases[i], phases[i + 1]);
        if (gate) {
            gates.push(gate);
        }
    }
    return gates;
}

// ============================================================================
// Gate Validation Functions
// ============================================================================

/**
 * Validates a single phase gate.
 */
export async function validateGate(
    gate: PhaseGate,
    context: GateContext
): Promise<GateStatus> {
    const missingArtifacts = gate.requiredLifecycleArtifacts.filter(
        (kind) => !context.lifecycleArtifactItemsByKind[kind]
    );

    const validationResults: ValidationResult[] = [];
    for (const rule of gate.validationRules) {
        const result = await Promise.resolve(rule.check(context));
        validationResults.push(result);
    }

    const hasErrors = validationResults.some((r) => !r.passed && r.errors.length > 0);
    const allPassed = missingArtifacts.length === 0 && !hasErrors;

    let status: GateStatus['status'] = 'blocked';
    if (allPassed) {
        status = 'ready';
    } else if (gate.canBypass) {
        status = 'blocked'; // Still blocked but can bypass
    }

    const blockedReasons: string[] = [];
    if (missingArtifacts.length > 0) {
        blockedReasons.push(`Missing artifacts: ${missingArtifacts.join(', ')}`);
    }
    for (const result of validationResults) {
        if (!result.passed) {
            blockedReasons.push(...result.errors);
        }
    }

    return {
        gateId: gate.id,
        status,
        missingArtifacts,
        validationResults,
        canBypass: gate.canBypass,
        blockedReason: blockedReasons.length > 0 ? blockedReasons.join('; ') : undefined,
        lastChecked: new Date().toISOString(),
    };
}

/**
 * Validates a phase transition.
 */
export async function validatePhaseTransition(
    context: GateContext
): Promise<{ canTransition: boolean; gateStatus?: GateStatus; gate?: PhaseGate }> {
    const gate = getGateForTransition(context.currentPhase, context.targetPhase);

    if (!gate) {
        // No gate for this transition (e.g., going backward)
        return { canTransition: true };
    }

    const gateStatus = await validateGate(gate, context);
    const canTransition = gateStatus.status === 'ready' || gateStatus.status === 'passed';

    return { canTransition, gateStatus, gate };
}
