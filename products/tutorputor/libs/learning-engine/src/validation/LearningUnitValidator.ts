/**
 * Learning Unit Validator
 * 
 * Enforces publishing gates and validates Learning Unit structure
 * against the canonical schema.
 * 
 * @doc.type class
 * @doc.purpose Validate Learning Units before publish
 * @doc.layer core
 * @doc.pattern Service
 */

import type {
    LearningUnit,
    ValidationResult,
    ValidationIssue,
    Claim,
    Evidence,
    Task,
    Artifact,
    PredictionTask,
} from '@ghatana/tutorputor-contracts/v1/learning-unit';

/**
 * Publishing gates - all must pass for publish to succeed
 */
const PUBLISH_GATES = [
    'hasIntent',
    'hasMinimumClaims',
    'allClaimsHaveEvidence',
    'hasMinimumTasks',
    'predictionsRequireConfidence',
    'explainersAreNotTerminal',
    'hasMinimumTelemetry',
    'hasAssessmentConfig',
    'hasMinimumArtifacts',
] as const;

type PublishGate = (typeof PUBLISH_GATES)[number];

export class LearningUnitValidator {
    private issues: ValidationIssue[] = [];
    private gatesPassed: Set<PublishGate> = new Set();

    /**
     * Validate a Learning Unit and return detailed results
     */
    validate(lu: LearningUnit): ValidationResult {
        this.issues = [];
        this.gatesPassed = new Set();

        // Run all validation checks
        this.checkIntent(lu);
        this.checkClaims(lu);
        this.checkEvidence(lu);
        this.checkTasks(lu);
        this.checkArtifacts(lu);
        this.checkTelemetry(lu);
        this.checkAssessment(lu);
        this.checkCredential(lu);

        // Calculate score
        const totalGates = PUBLISH_GATES.length;
        const passedGates = this.gatesPassed.size;
        const score = Math.round((passedGates / totalGates) * 100);

        // Generate suggestions
        const suggestions = this.generateSuggestions(lu);

        return {
            valid: passedGates === totalGates,
            score,
            issues: this.issues,
            suggestions,
        };
    }

    /**
     * Check if LU is ready to publish (all gates pass)
     */
    canPublish(lu: LearningUnit): boolean {
        return this.validate(lu).valid;
    }

    // =========================================================================
    // Validation Checks
    // =========================================================================

    private checkIntent(lu: LearningUnit): void {
        if (!lu.intent) {
            this.addError('intent', 'Intent is required');
            return;
        }

        if (!lu.intent.problem || lu.intent.problem.length < 20) {
            this.addError('intent.problem', 'Problem statement must be at least 20 characters');
        }

        if (!lu.intent.motivation || lu.intent.motivation.length < 10) {
            this.addError('intent.motivation', 'Motivation must be at least 10 characters');
        }

        if (lu.intent.problem && lu.intent.motivation) {
            this.gatesPassed.add('hasIntent');
        }
    }

    private checkClaims(lu: LearningUnit): void {
        if (!lu.claims || lu.claims.length === 0) {
            this.addError('claims', 'At least one claim is required');
            return;
        }

        this.gatesPassed.add('hasMinimumClaims');

        // Check each claim
        lu.claims.forEach((claim, index) => {
            this.validateClaim(claim, index);
        });

        // Check for duplicate IDs
        const ids = lu.claims.map((c) => c.id);
        const duplicates = ids.filter((id, i) => ids.indexOf(id) !== i);
        if (duplicates.length > 0) {
            this.addError('claims', `Duplicate claim IDs: ${duplicates.join(', ')}`);
        }
    }

    private validateClaim(claim: Claim, index: number): void {
        const path = `claims[${index}]`;

        if (!claim.id || !claim.id.match(/^C\d+$/)) {
            this.addError(`${path}.id`, 'Claim ID must match pattern C1, C2, etc.');
        }

        if (!claim.text || claim.text.length < 10) {
            this.addError(`${path}.text`, 'Claim text must be at least 10 characters');
        }

        // Check for action verbs
        const actionVerbs = [
            'predict', 'explain', 'construct', 'compare', 'derive',
            'analyze', 'evaluate', 'create', 'apply', 'demonstrate',
            'identify', 'describe', 'calculate', 'design', 'implement',
        ];
        const hasActionVerb = actionVerbs.some((verb) =>
            claim.text.toLowerCase().includes(verb)
        );
        if (!hasActionVerb) {
            this.addWarning(`${path}.text`, 'Claim should use an action verb (predict, explain, construct, etc.)');
        }

        if (!claim.bloom) {
            this.addError(`${path}.bloom`, 'Bloom level is required');
        }
    }

    private checkEvidence(lu: LearningUnit): void {
        if (!lu.evidence || lu.evidence.length === 0) {
            this.addError('evidence', 'At least one evidence specification is required');
            return;
        }

        // Check that every claim has at least one evidence
        const claimIds = new Set(lu.claims.map((c) => c.id));
        const evidenceClaimRefs = new Set(lu.evidence.map((e) => e.claimRef));

        const claimsWithoutEvidence = [...claimIds].filter((id) => !evidenceClaimRefs.has(id));
        if (claimsWithoutEvidence.length > 0) {
            this.addError('evidence', `Claims without evidence: ${claimsWithoutEvidence.join(', ')}`);
        } else {
            this.gatesPassed.add('allClaimsHaveEvidence');
        }

        // Validate each evidence spec
        lu.evidence.forEach((evidence, index) => {
            this.validateEvidence(evidence, index, claimIds);
        });
    }

    private validateEvidence(evidence: Evidence, index: number, claimIds: Set<string>): void {
        const path = `evidence[${index}]`;

        if (!evidence.id || !evidence.id.match(/^E\d+$/)) {
            this.addError(`${path}.id`, 'Evidence ID must match pattern E1, E2, etc.');
        }

        if (!evidence.claimRef || !claimIds.has(evidence.claimRef)) {
            this.addError(`${path}.claimRef`, `Invalid claim reference: ${evidence.claimRef}`);
        }

        if (!evidence.observables || evidence.observables.length === 0) {
            this.addError(`${path}.observables`, 'At least one observable is required');
        }
    }

    private checkTasks(lu: LearningUnit): void {
        if (!lu.tasks || lu.tasks.length === 0) {
            this.addError('tasks', 'At least one task is required');
            return;
        }

        this.gatesPassed.add('hasMinimumTasks');

        const claimIds = new Set(lu.claims.map((c) => c.id));
        const evidenceIds = new Set(lu.evidence.map((e) => e.id));

        let allPredictionsHaveConfidence = true;

        lu.tasks.forEach((task, index) => {
            const path = `tasks[${index}]`;

            // Check claim and evidence refs
            if (!claimIds.has(task.claimRef)) {
                this.addError(`${path}.claimRef`, `Invalid claim reference: ${task.claimRef}`);
            }
            if (!evidenceIds.has(task.evidenceRef)) {
                this.addError(`${path}.evidenceRef`, `Invalid evidence reference: ${task.evidenceRef}`);
            }

            // Check prediction tasks require confidence
            if (task.type === 'prediction') {
                const predTask = task as PredictionTask;
                if (!predTask.confidenceRequired) {
                    this.addError(`${path}.confidenceRequired`, 'Prediction tasks must require confidence');
                    allPredictionsHaveConfidence = false;
                }
                if (!predTask.options || predTask.options.length < 2) {
                    this.addError(`${path}.options`, 'Prediction tasks must have at least 2 options');
                }
            }

            // Check simulation tasks
            if (task.type === 'simulation') {
                if (!('simulationRef' in task) || !task.simulationRef) {
                    this.addError(`${path}.simulationRef`, 'Simulation tasks must reference a simulation');
                }
                if (!('successCriteria' in task) || !task.successCriteria) {
                    this.addError(`${path}.successCriteria`, 'Simulation tasks must define success criteria');
                }
            }
        });

        if (allPredictionsHaveConfidence) {
            this.gatesPassed.add('predictionsRequireConfidence');
        }
    }

    private checkArtifacts(lu: LearningUnit): void {
        if (!lu.artifacts || lu.artifacts.length === 0) {
            this.addError('artifacts', 'At least one artifact is required');
            return;
        }

        this.gatesPassed.add('hasMinimumArtifacts');

        const claimIds = new Set(lu.claims.map((c) => c.id));
        const taskIds = new Set(lu.tasks.map((t) => t.id));
        let allExplainersScaffold = true;

        lu.artifacts.forEach((artifact, index) => {
            const path = `artifacts[${index}]`;

            // Check claim refs
            artifact.claims.forEach((claimRef) => {
                if (!claimIds.has(claimRef)) {
                    this.addError(`${path}.claims`, `Invalid claim reference: ${claimRef}`);
                }
            });

            // Explainer videos must scaffold a task (cannot be terminal)
            if (artifact.type === 'explainer_video') {
                if (!artifact.scaffolds || artifact.scaffolds.length === 0) {
                    this.addWarning(`${path}`, 'Explainer videos should scaffold at least one task (cannot be terminal content)');
                    allExplainersScaffold = false;
                } else {
                    artifact.scaffolds.forEach((taskRef) => {
                        if (!taskIds.has(taskRef)) {
                            this.addError(`${path}.scaffolds`, `Invalid task reference: ${taskRef}`);
                        }
                    });
                }
            }
        });

        if (allExplainersScaffold) {
            this.gatesPassed.add('explainersAreNotTerminal');
        }
    }

    private checkTelemetry(lu: LearningUnit): void {
        if (!lu.telemetry) {
            this.addError('telemetry', 'Telemetry configuration is required');
            return;
        }

        const requiredEvents = ['sim.start', 'assess.answer.submit', 'assess.confidence.submit'];
        const hasMinimumEvents = lu.telemetry.events && lu.telemetry.events.length >= 3;

        if (!hasMinimumEvents) {
            this.addError('telemetry.events', 'At least 3 telemetry events are required');
        } else {
            this.gatesPassed.add('hasMinimumTelemetry');
        }

        // Warn if missing critical events
        requiredEvents.forEach((event) => {
            if (!lu.telemetry.events.includes(event)) {
                this.addWarning('telemetry.events', `Missing recommended event: ${event}`);
            }
        });
    }

    private checkAssessment(lu: LearningUnit): void {
        if (!lu.assessment) {
            this.addError('assessment', 'Assessment configuration is required');
            return;
        }

        if (!lu.assessment.model) {
            this.addError('assessment.model', 'Assessment model is required');
            return;
        }

        if (!lu.assessment.scoring) {
            this.addError('assessment.scoring', 'CBM scoring matrix is required');
            return;
        }

        this.gatesPassed.add('hasAssessmentConfig');

        // Check viva triggers
        if (!lu.assessment.vivaTrigger || !lu.assessment.vivaTrigger.conditions?.length) {
            this.addWarning('assessment.vivaTrigger', 'Consider adding viva trigger conditions for overconfidence detection');
        }
    }

    private checkCredential(lu: LearningUnit): void {
        if (lu.credential) {
            if (!lu.credential.skillTags || lu.credential.skillTags.length === 0) {
                this.addWarning('credential.skillTags', 'Credentials should have at least one skill tag');
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private addError(field: string, message: string): void {
        this.issues.push({ field, severity: 'error', message });
    }

    private addWarning(field: string, message: string): void {
        this.issues.push({ field, severity: 'warning', message });
    }

    private generateSuggestions(lu: LearningUnit): string[] {
        const suggestions: string[] = [];

        // Suggest more claims for complex topics
        if (lu.claims.length === 1) {
            suggestions.push('Consider adding more claims to break down the learning objectives');
        }

        // Suggest multiple evidence types
        const evidenceTypes = new Set(lu.evidence.map((e) => e.type));
        if (evidenceTypes.size === 1) {
            suggestions.push('Consider using multiple evidence types (prediction + simulation + explanation)');
        }

        // Suggest simulation task
        const hasSimulationTask = lu.tasks.some((t) => t.type === 'simulation');
        if (!hasSimulationTask) {
            suggestions.push('Consider adding a simulation task for interactive evidence collection');
        }

        // Suggest prerequisites for advanced content
        if (lu.level === 'university' && lu.claims.every((c) => !c.prerequisites?.length)) {
            suggestions.push('Consider adding prerequisite claims for university-level content');
        }

        return suggestions;
    }
}

/**
 * Factory function for quick validation
 */
export function validateLearningUnit(lu: LearningUnit): ValidationResult {
    const validator = new LearningUnitValidator();
    return validator.validate(lu);
}

/**
 * Check if Learning Unit can be published
 */
export function canPublishLearningUnit(lu: LearningUnit): boolean {
    const validator = new LearningUnitValidator();
    return validator.canPublish(lu);
}
