/**
 * Learning Unit Validator
 *
 * Validates Learning Unit structure against Evidence-Based Learning rules.
 *
 * @doc.type utility
 * @doc.purpose Validation logic for Learning Units
 * @doc.layer product
 * @doc.pattern Validator
 */

import type { LearningUnit, Claim, Evidence, Task } from '@ghatana/tutorputor-contracts/v1/learning-unit';

export interface ValidationResult {
    isValid: boolean;
    errors: string[];
    warnings: string[];
}

export function validateLearningUnit(lu: LearningUnit): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    // 1. Intent Validation
    if (!lu.intent.problem || lu.intent.problem.length < 10) {
        errors.push("Intent: Problem statement is too short or missing.");
    }
    if (!lu.intent.motivation || lu.intent.motivation.length < 10) {
        errors.push("Intent: Motivation is too short or missing.");
    }

    // 2. Claims Validation
    if (lu.claims.length === 0) {
        errors.push("Claims: At least one claim is required.");
    }
    lu.claims.forEach(claim => {
        if (!claim.bloomLevel) {
            warnings.push(`Claim "${claim.text.substring(0, 20)}...": Missing Bloom's taxonomy level.`);
        }
    });
    // Cycle detection via DFS over the prerequisite DAG
    const claimIds = new Set(lu.claims.map(c => c.id));
    const prereqMap = new Map<string, string[]>(
        lu.claims.map(c => [c.id, (c.prerequisites ?? []).filter(p => claimIds.has(p))])
    );

    const WHITE = 0, GREY = 1, BLACK = 2;
    const colour = new Map<string, number>(lu.claims.map(c => [c.id, WHITE]));

    function hasCycle(nodeId: string, path: string[]): boolean {
        colour.set(nodeId, GREY);
        for (const neighbour of (prereqMap.get(nodeId) ?? [])) {
            if (colour.get(neighbour) === GREY) {
                const cycle = [...path, nodeId, neighbour].join(' → ');
                errors.push(`Claims: Circular prerequisite detected: ${cycle}`);
                return true;
            }
            if (colour.get(neighbour) === WHITE && hasCycle(neighbour, [...path, nodeId])) {
                return true;
            }
        }
        colour.set(nodeId, BLACK);
        return false;
    }

    for (const claim of lu.claims) {
        if (colour.get(claim.id) === WHITE) {
            hasCycle(claim.id, []);
        }
    }

    // 3. Evidence Validation
    if (lu.evidence.length === 0) {
        warnings.push("Evidence: No evidence types defined.");
    }
    // Check if every claim has evidence
    lu.claims.forEach(claim => {
        const hasEvidence = lu.evidence.some(e => e.claimRef === claim.id);
        if (!hasEvidence) {
            errors.push(`Claim "${claim.text.substring(0, 20)}...": No evidence defined for this claim.`);
        }
    });

    // 4. Task Validation
    if (lu.tasks.length === 0) {
        warnings.push("Tasks: No tasks defined.");
    }
    lu.tasks.forEach(task => {
        if (!task.claimRef) {
            errors.push(`Task "${task.prompt.substring(0, 20)}...": Must reference a claim.`);
        }
        if (!task.evidenceRef) {
            errors.push(`Task "${task.prompt.substring(0, 20)}...": Must reference an evidence type.`);
        }
    });

    // 5. CBM Validation
    if (!lu.assessment.scoring) {
        errors.push("Assessment: CBM scoring matrix is missing.");
    }

    return {
        isValid: errors.length === 0,
        errors,
        warnings
    };
}
