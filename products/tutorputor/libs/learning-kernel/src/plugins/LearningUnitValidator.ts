/**
 * Learning Unit Validator Plugin.
 *
 * Validates Learning Units against the canonical schema and quality gates.
 * Implements the AuthoringTool plugin interface for integration with
 * the content creation pipeline.
 *
 * @doc.type class
 * @doc.purpose Validate Learning Units for publishing readiness
 * @doc.layer plugin
 * @doc.pattern AuthoringTool, Validator
 */

import type {
    AuthoringTool,
    PluginMetadata,
    ValidationResult,
    ValidationIssue,
} from '@ghatana/tutorputor-contracts/v1/plugin-interfaces';
import type { LearningUnit } from '@ghatana/tutorputor-contracts/v1/learning-unit';

/**
 * Validation rule definition.
 */
export interface ValidationRule {
    /** Unique rule ID */
    readonly id: string;
    /** Human-readable name */
    readonly name: string;
    /** Category of rule */
    readonly category: ValidationCategory;
    /** Severity when rule fails */
    readonly severity: 'error' | 'warning' | 'info';
    /** Rule description */
    readonly description: string;
    /** The validation function */
    readonly validate: (unit: LearningUnit) => ValidationIssue[];
}

/**
 * Validation categories.
 */
export type ValidationCategory =
    | 'schema'
    | 'structure'
    | 'competency'
    | 'assessment'
    | 'quality'
    | 'accessibility';

/**
 * Validator configuration.
 */
export interface LearningUnitValidatorConfig {
    /** Maximum warnings allowed for publishing (default: 10) */
    readonly maxWarningsForPublish?: number;
    /** Categories to skip validation for */
    readonly skipCategories?: readonly ValidationCategory[];
    /** Custom rules to add */
    readonly customRules?: readonly ValidationRule[];
}

/**
 * Learning Unit Validator Plugin.
 *
 * Features:
 * - Comprehensive schema validation
 * - Competency alignment checking
 * - Assessment quality validation
 * - Publishing gate checking
 * - Extensible rule system
 *
 * @example
 * ```typescript
 * const validator = new LearningUnitValidator();
 * registry.registerAuthoringTool(validator);
 *
 * // Validate a learning unit
 * const result = await validator.validate(myLearningUnit);
 * if (result.valid) {
 *   await publishService.publish(myLearningUnit);
 * }
 * ```
 */
export class LearningUnitValidator implements AuthoringTool {
    readonly metadata: PluginMetadata = {
        id: 'lu-validator',
        name: 'Learning Unit Validator',
        version: '1.0.0',
        type: 'authoring_tool',
        priority: 100,
        description: 'Validates Learning Units against schema and quality gates',
        author: 'TutorPutor Core Team',
        tags: ['authoring', 'validation', 'quality', 'publishing'],
        enabled: true,
    };

    private readonly config: Required<LearningUnitValidatorConfig>;
    private readonly rules: ValidationRule[];

    constructor(config?: LearningUnitValidatorConfig) {
        this.config = {
            maxWarningsForPublish: config?.maxWarningsForPublish ?? 10,
            skipCategories: config?.skipCategories ?? [],
            customRules: config?.customRules ?? [],
        };

        // Initialize with built-in rules
        this.rules = [...this.getBuiltInRules(), ...this.config.customRules];
    }

    /**
     * Validate a Learning Unit.
     */
    async validate(unit: LearningUnit): Promise<ValidationResult> {
        const issues: ValidationIssue[] = [];

        // Run all applicable rules
        for (const rule of this.rules) {
            // Skip if category is excluded
            if (this.config.skipCategories.includes(rule.category)) {
                continue;
            }

            try {
                const ruleIssues = rule.validate(unit);
                issues.push(...ruleIssues);
            } catch (error) {
                // Rule execution failed - add as error
                issues.push({
                    field: rule.id,
                    severity: 'error',
                    message: `Rule ${rule.id} failed: ${error instanceof Error ? error.message : String(error)}`,
                });
            }
        }

        // Count by severity
        const errorCount = issues.filter((i) => i.severity === 'error').length;
        const warningCount = issues.filter((i) => i.severity === 'warning').length;

        // Calculate score (0-100)
        const maxPenalty = issues.length * 10;
        const penalty = issues.reduce((sum, i) => {
            switch (i.severity) {
                case 'error': return sum + 10;
                case 'warning': return sum + 5;
                case 'info': return sum + 1;
                default: return sum;
            }
        }, 0);
        const score = Math.max(0, Math.min(100, 100 - penalty));

        return {
            valid: errorCount === 0,
            score,
            issues,
        };
    }

    /**
     * Get built-in validation rules.
     */
    private getBuiltInRules(): ValidationRule[] {
        return [
            // Schema rules
            this.createRequiredFieldRule('id', 'schema'),
            this.createRequiredFieldRule('version', 'schema'),
            this.createRequiredFieldRule('domain', 'schema'),

            // Structure rules
            {
                id: 'structure-has-claims',
                name: 'Has Claims',
                category: 'structure',
                severity: 'error',
                description: 'Learning unit must have at least one claim',
                validate: (unit) => {
                    if (!unit.claims || unit.claims.length === 0) {
                        return [{
                            field: 'claims',
                            severity: 'error',
                            message: 'Learning unit must have at least one claim',
                            suggestion: 'Add testable claims that learners can demonstrate',
                        }];
                    }
                    return [];
                },
            },
            {
                id: 'structure-has-tasks',
                name: 'Has Tasks',
                category: 'structure',
                severity: 'error',
                description: 'Learning unit must have at least one task',
                validate: (unit) => {
                    if (!unit.tasks || unit.tasks.length === 0) {
                        return [{
                            field: 'tasks',
                            severity: 'error',
                            message: 'Learning unit must have at least one task',
                            suggestion: 'Add tasks (prediction, simulation, explanation, or construction)',
                        }];
                    }
                    return [];
                },
            },

            // Competency rules
            {
                id: 'competency-claims-have-bloom',
                name: 'Claims Have Bloom Level',
                category: 'competency',
                severity: 'warning',
                description: 'All claims should specify Bloom taxonomy level',
                validate: (unit) => {
                    const issues: ValidationIssue[] = [];
                    const claims = unit.claims ?? [];
                    for (let i = 0; i < claims.length; i++) {
                        const claim = claims[i];
                        if (claim && !claim.bloom) {
                            issues.push({
                                field: `claims[${i}].bloom`,
                                severity: 'warning',
                                message: `Claim ${claim.id} is missing Bloom taxonomy level`,
                                suggestion: 'Add bloom level (remember, understand, apply, analyze, evaluate, create)',
                            });
                        }
                    }
                    return issues;
                },
            },

            // Quality rules
            {
                id: 'quality-has-intent',
                name: 'Has Intent',
                category: 'quality',
                severity: 'warning',
                description: 'Learning unit should have intent with problem and motivation',
                validate: (unit) => {
                    const issues: ValidationIssue[] = [];

                    if (!unit.intent?.problem) {
                        issues.push({
                            field: 'intent.problem',
                            severity: 'warning',
                            message: 'Intent should describe the problem being addressed',
                        });
                    }

                    if (!unit.intent?.motivation) {
                        issues.push({
                            field: 'intent.motivation',
                            severity: 'warning',
                            message: 'Intent should describe the motivation for learning',
                        });
                    }

                    return issues;
                },
            },

            // Assessment rules
            {
                id: 'assessment-evidence-coverage',
                name: 'Evidence Covers Claims',
                category: 'assessment',
                severity: 'warning',
                description: 'Each claim should have corresponding evidence',
                validate: (unit) => {
                    const issues: ValidationIssue[] = [];
                    const claims = unit.claims ?? [];
                    const evidences = unit.evidence ?? [];
                    const evidenceClaimRefs = new Set(evidences.map(e => e.claimRef));

                    for (const claim of claims) {
                        if (claim && !evidenceClaimRefs.has(claim.id)) {
                            issues.push({
                                field: `evidence`,
                                severity: 'warning',
                                message: `Claim ${claim.id} has no associated evidence`,
                                suggestion: 'Add evidence definitions that prove this claim',
                            });
                        }
                    }
                    return issues;
                },
            },

            // Accessibility rules
            {
                id: 'accessibility-artifacts-described',
                name: 'Artifacts Have Descriptions',
                category: 'accessibility',
                severity: 'info',
                description: 'All artifacts should have descriptions',
                validate: (unit) => {
                    const issues: ValidationIssue[] = [];
                    const artifacts = unit.artifacts ?? [];

                    for (let i = 0; i < artifacts.length; i++) {
                        const artifact = artifacts[i];
                        // Artifacts should have a description for accessibility
                        if (artifact && !this.hasDescription(artifact)) {
                            issues.push({
                                field: `artifacts[${i}]`,
                                severity: 'info',
                                message: `Artifact ${i + 1} could benefit from additional description`,
                                suggestion: 'Add detailed descriptions for screen readers',
                            });
                        }
                    }

                    return issues;
                },
            },
        ];
    }

    /**
     * Check if an artifact has a description.
     */
    private hasDescription(artifact: unknown): boolean {
        if (typeof artifact !== 'object' || artifact === null) {
            return false;
        }
        const obj = artifact as Record<string, unknown>;
        return typeof obj['description'] === 'string' && obj['description'].length > 0;
    }

    /**
     * Create a required field validation rule.
     */
    private createRequiredFieldRule(
        field: string,
        category: ValidationCategory
    ): ValidationRule {
        return {
            id: `required-${field}`,
            name: `Required: ${field}`,
            category,
            severity: 'error',
            description: `Field '${field}' is required`,
            validate: (unit) => {
                const value = this.getNestedValue(unit, field);
                if (value === undefined || value === null || value === '') {
                    return [{
                        field,
                        severity: 'error',
                        message: `Required field '${field}' is missing or empty`,
                    }];
                }
                return [];
            },
        };
    }

    /**
     * Get nested value from object using dot notation.
     */
    private getNestedValue(obj: unknown, path: string): unknown {
        const parts = path.split('.');
        let current: unknown = obj;

        for (const part of parts) {
            if (current === null || current === undefined) {
                return undefined;
            }
            current = (current as Record<string, unknown>)[part];
        }

        return current;
    }

    /**
     * Add a custom validation rule.
     */
    addRule(rule: ValidationRule): void {
        this.rules.push(rule);
    }
}

/**
 * Factory function to create validator.
 */
export function createLearningUnitValidator(
    config?: LearningUnitValidatorConfig
): LearningUnitValidator {
    return new LearningUnitValidator(config);
}
