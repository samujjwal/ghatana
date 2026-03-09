/**
 * Content Studio Validator Plugin.
 *
 * Validates Learning Experiences created via Content Studio.
 * Implements the AuthoringTool plugin interface for integration with
 * the content creation pipeline.
 *
 * @doc.type class
 * @doc.purpose Validate Learning Experiences for publishing readiness
 * @doc.layer plugin
 * @doc.pattern AuthoringTool, Validator
 */

import type {
    AuthoringTool,
    PluginMetadata,
    ValidationResult,
    ValidationIssue,
} from '@ghatana/tutorputor-contracts/v1/plugin-interfaces';
import type {
    LearningExperience,
    LearningClaim,
    ValidationPillar,
    GradeAdaptation,
} from '@ghatana/tutorputor-contracts/v1/content-studio';
import type { BloomLevel } from '@ghatana/tutorputor-contracts/v1/learning-unit';

/**
 * Validation rule definition for Content Studio.
 */
export interface ContentStudioValidationRule {
    /** Unique rule ID */
    readonly id: string;
    /** Human-readable name */
    readonly name: string;
    /** Category/Pillar of rule */
    readonly pillar: ValidationPillar;
    /** Severity when rule fails */
    readonly severity: 'error' | 'warning' | 'info';
    /** Rule description */
    readonly description: string;
    /** The validation function */
    readonly validate: (experience: LearningExperience) => ValidationIssue[];
}

/**
 * Validator configuration.
 */
export interface ContentStudioValidatorConfig {
    /** Maximum warnings allowed for publishing (default: 10) */
    readonly maxWarningsForPublish?: number;
    /** Pillars to skip validation for */
    readonly skipPillars?: readonly ValidationPillar[];
    /** Custom rules to add */
    readonly customRules?: readonly ContentStudioValidationRule[];
    /** Minimum score required for publishing (0-100) */
    readonly minimumPublishScore?: number;
}

const BLOOM_ORDER: BloomLevel[] = ['remember', 'understand', 'apply', 'analyze', 'evaluate', 'create'];

function getBloomLevel(bloom: string): number {
    return BLOOM_ORDER.indexOf(bloom as BloomLevel);
}

/**
 * Content Studio Validator Plugin.
 *
 * Features:
 * - 5-Pillar validation (Educational, Experiential, Safety, Technical, Accessibility)
 * - Grade-level appropriateness checking
 * - Claims-Evidence-Task alignment validation
 * - Publishing gate checking
 * - Extensible rule system
 *
 * @example
 * ```typescript
 * const validator = new ContentStudioValidator();
 * registry.registerAuthoringTool(validator);
 *
 * // Validate a learning experience
 * const result = await validator.validate(myExperience);
 * if (result.valid) {
 *   await publishService.publish(myExperience);
 * }
 * ```
 */
export class ContentStudioValidator implements AuthoringTool {
    readonly metadata: PluginMetadata = {
        id: 'content-studio-validator',
        name: 'Content Studio Validator',
        version: '1.0.0',
        type: 'authoring_tool',
        priority: 100,
        description: 'Validates Learning Experiences against 5-pillar quality gates',
        author: 'TutorPutor Core Team',
        tags: ['authoring', 'validation', 'content-studio', 'quality', 'publishing'],
        enabled: true,
    };

    private readonly config: Required<ContentStudioValidatorConfig>;
    private readonly rules: ContentStudioValidationRule[];

    constructor(config?: ContentStudioValidatorConfig) {
        this.config = {
            maxWarningsForPublish: config?.maxWarningsForPublish ?? 10,
            skipPillars: config?.skipPillars ?? [],
            customRules: config?.customRules ?? [],
            minimumPublishScore: config?.minimumPublishScore ?? 70,
        };

        // Initialize with built-in rules
        this.rules = [...this.getBuiltInRules(), ...this.config.customRules];
    }

    /**
     * Validate a Learning Experience.
     * 
     * Note: This method accepts LearningExperience but the interface expects LearningUnit.
     * The implementation handles both through duck typing.
     */
    async validate(experience: unknown): Promise<ValidationResult> {
        const exp = experience as LearningExperience;
        const issues: ValidationIssue[] = [];

        // Run all applicable rules
        for (const rule of this.rules) {
            // Skip if pillar is excluded
            if (this.config.skipPillars.includes(rule.pillar)) {
                continue;
            }

            try {
                const ruleIssues = rule.validate(exp);
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
            valid: errorCount === 0 && warningCount <= this.config.maxWarningsForPublish,
            score,
            issues,
        };
    }

    /**
     * Check if experience can be published.
     */
    async canPublish(experience: LearningExperience): Promise<boolean> {
        const result = await this.validate(experience);
        return result.valid && result.score >= this.config.minimumPublishScore;
    }

    /**
     * Get validation issues by pillar.
     */
    getIssuesByPillar(issues: ValidationIssue[], pillar: ValidationPillar): ValidationIssue[] {
        return issues.filter(i => i.field?.startsWith(pillar));
    }

    /**
     * Get built-in validation rules.
     */
    private getBuiltInRules(): ContentStudioValidationRule[] {
        return [
            // ============ Educational Pillar ============
            {
                id: 'educational-has-claims',
                name: 'Has Learning Claims',
                pillar: 'educational',
                severity: 'error',
                description: 'Experience must have at least one learning claim',
                validate: (exp) => {
                    if (!exp.claims || exp.claims.length === 0) {
                        return [{
                            field: 'educational.claims',
                            severity: 'error',
                            message: 'Experience must have at least one learning claim',
                            suggestion: 'Add testable learning claims that describe what learners will be able to do',
                        }];
                    }
                    return [];
                },
            },
            {
                id: 'educational-claims-bloom',
                name: 'Claims Have Bloom Level',
                pillar: 'educational',
                severity: 'warning',
                description: 'All claims should specify Bloom taxonomy level',
                validate: (exp) => {
                    const issues: ValidationIssue[] = [];
                    const claims = exp.claims ?? [];

                    for (let i = 0; i < claims.length; i++) {
                        const claim = claims[i];
                        if (claim && !claim.bloom) {
                            issues.push({
                                field: `educational.claims[${i}].bloom`,
                                severity: 'warning',
                                message: `Claim ${claim.id} is missing Bloom taxonomy level`,
                                suggestion: 'Add bloom level (remember, understand, apply, analyze, evaluate, create)',
                            });
                        }
                    }
                    return issues;
                },
            },
            {
                id: 'educational-bloom-progression',
                name: 'Bloom Progression',
                pillar: 'educational',
                severity: 'info',
                description: 'Claims should show cognitive progression',
                validate: (exp) => {
                    const claims = exp.claims ?? [];
                    if (claims.length < 2) return [];

                    const levels = claims
                        .filter(c => c.bloom)
                        .map(c => getBloomLevel(c.bloom));

                    const hasProgression = levels.some((lvl, i) => i > 0 && lvl > levels[i - 1]!);

                    if (!hasProgression) {
                        return [{
                            field: 'educational.bloom-progression',
                            severity: 'info',
                            message: 'Consider ordering claims to show cognitive progression',
                            suggestion: 'Arrange claims from lower Bloom levels (remember, understand) to higher (analyze, create)',
                        }];
                    }
                    return [];
                },
            },
            {
                id: 'educational-grade-appropriate',
                name: 'Grade Appropriate',
                pillar: 'educational',
                severity: 'warning',
                description: 'Content should match target grade level',
                validate: (exp) => {
                    const grade = exp.gradeAdaptation;
                    if (!grade) {
                        return [{
                            field: 'educational.gradeAdaptation',
                            severity: 'warning',
                            message: 'Grade adaptation settings not configured',
                            suggestion: 'Configure grade range and adaptation settings',
                        }];
                    }

                    const gradeRanges: Record<string, [number, number]> = {
                        k_2: [1, 3],
                        grade_3_5: [3, 5],
                        grade_6_8: [4, 7],
                        grade_9_12: [6, 9],
                        undergraduate: [7, 10],
                        graduate: [8, 10],
                        professional: [8, 10],
                    };

                    const [min, max] = gradeRanges[grade.gradeRange] || [1, 10];
                    if (grade.vocabularyComplexity < min || grade.vocabularyComplexity > max) {
                        return [{
                            field: 'educational.vocabularyComplexity',
                            severity: 'warning',
                            message: `Vocabulary complexity (${grade.vocabularyComplexity}) may not match ${grade.gradeRange}`,
                            suggestion: `Consider vocabulary level ${min}-${max} for this grade range`,
                        }];
                    }
                    return [];
                },
            },

            // ============ Experiential Pillar ============
            {
                id: 'experiential-has-tasks',
                name: 'Has Learning Tasks',
                pillar: 'experiential',
                severity: 'error',
                description: 'Claims must have associated tasks',
                validate: (exp) => {
                    const issues: ValidationIssue[] = [];
                    const claims = exp.claims ?? [];

                    for (const claim of claims) {
                        if (!claim.tasks || claim.tasks.length === 0) {
                            issues.push({
                                field: `experiential.claims[${claim.id}].tasks`,
                                severity: 'error',
                                message: `Claim ${claim.id} has no associated tasks`,
                                suggestion: 'Add tasks for learners to demonstrate mastery',
                            });
                        }
                    }
                    return issues;
                },
            },
            {
                id: 'experiential-has-evidence',
                name: 'Has Evidence Requirements',
                pillar: 'experiential',
                severity: 'error',
                description: 'Claims must have evidence requirements',
                validate: (exp) => {
                    const issues: ValidationIssue[] = [];
                    const claims = exp.claims ?? [];

                    for (const claim of claims) {
                        if (!claim.evidenceRequirements || claim.evidenceRequirements.length === 0) {
                            issues.push({
                                field: `experiential.claims[${claim.id}].evidence`,
                                severity: 'error',
                                message: `Claim ${claim.id} has no evidence requirements`,
                                suggestion: 'Define what observable evidence proves mastery',
                            });
                        }
                    }
                    return issues;
                },
            },
            {
                id: 'experiential-task-variety',
                name: 'Task Type Variety',
                pillar: 'experiential',
                severity: 'info',
                description: 'Experience should include variety of task types',
                validate: (exp) => {
                    const allTasks = (exp.claims ?? []).flatMap(c => c.tasks ?? []);
                    const taskTypes = new Set(allTasks.map(t => t.type));

                    if (taskTypes.size < 2 && allTasks.length >= 3) {
                        return [{
                            field: 'experiential.task-variety',
                            severity: 'info',
                            message: 'Consider adding variety of task types',
                            suggestion: 'Mix prediction, simulation, explanation, and construction tasks',
                        }];
                    }
                    return [];
                },
            },

            // ============ Safety Pillar ============
            {
                id: 'safety-no-pii',
                name: 'No PII in Content',
                pillar: 'safety',
                severity: 'error',
                description: 'Content should not contain personally identifiable information',
                validate: (exp) => {
                    const piiPatterns = [
                        /\b\d{3}-\d{2}-\d{4}\b/,  // SSN
                        /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b/,  // Email
                        /\b\d{3}[-.]?\d{3}[-.]?\d{4}\b/,  // Phone
                    ];

                    const contentToCheck = [
                        exp.title,
                        exp.description,
                        ...(exp.claims ?? []).map(c => c.text),
                        ...(exp.claims ?? []).flatMap(c => c.tasks ?? []).map(t => t.instructions),
                    ].join(' ');

                    for (const pattern of piiPatterns) {
                        if (pattern.test(contentToCheck)) {
                            return [{
                                field: 'safety.pii',
                                severity: 'error',
                                message: 'Potential PII detected in content',
                                suggestion: 'Remove any personal information (emails, phone numbers, SSNs)',
                            }];
                        }
                    }
                    return [];
                },
            },
            {
                id: 'safety-has-author',
                name: 'Has Author Attribution',
                pillar: 'safety',
                severity: 'warning',
                description: 'Content should have author attribution',
                validate: (exp) => {
                    if (!exp.authorId) {
                        return [{
                            field: 'safety.author',
                            severity: 'warning',
                            message: 'Content lacks author attribution',
                            suggestion: 'Ensure content is attributed to an author',
                        }];
                    }
                    return [];
                },
            },

            // ============ Technical Pillar ============
            {
                id: 'technical-has-slug',
                name: 'Has Valid Slug',
                pillar: 'technical',
                severity: 'error',
                description: 'Experience must have a valid URL slug',
                validate: (exp) => {
                    const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
                    if (!exp.slug || !slugPattern.test(exp.slug)) {
                        return [{
                            field: 'technical.slug',
                            severity: 'error',
                            message: 'Invalid or missing URL slug',
                            suggestion: 'Use lowercase letters, numbers, and hyphens only',
                        }];
                    }
                    return [];
                },
            },
            {
                id: 'technical-unique-ids',
                name: 'Unique IDs',
                pillar: 'technical',
                severity: 'error',
                description: 'All claims and tasks must have unique IDs',
                validate: (exp) => {
                    const claimIds = (exp.claims ?? []).map(c => c.id);
                    const taskIds = (exp.claims ?? []).flatMap(c => c.tasks ?? []).map(t => t.id);
                    const allIds = [...claimIds, ...taskIds];

                    const duplicates = allIds.filter((id, i) => allIds.indexOf(id) !== i);

                    if (duplicates.length > 0) {
                        return [{
                            field: 'technical.ids',
                            severity: 'error',
                            message: `Duplicate IDs found: ${[...new Set(duplicates)].join(', ')}`,
                            suggestion: 'Ensure all claims and tasks have unique identifiers',
                        }];
                    }
                    return [];
                },
            },

            // ============ Accessibility Pillar ============
            {
                id: 'accessibility-description-length',
                name: 'Adequate Description',
                pillar: 'accessibility',
                severity: 'warning',
                description: 'Descriptions should be meaningful and not too long',
                validate: (exp) => {
                    const desc = exp.description ?? '';
                    const wordCount = desc.split(/\s+/).length;

                    if (wordCount < 10) {
                        return [{
                            field: 'accessibility.description',
                            severity: 'warning',
                            message: 'Description too brief - add more context',
                            suggestion: 'Provide at least 10 words of description for accessibility',
                        }];
                    }
                    if (wordCount > 200) {
                        return [{
                            field: 'accessibility.description',
                            severity: 'info',
                            message: 'Description may be too long - consider summarizing',
                            suggestion: 'Keep descriptions under 200 words for readability',
                        }];
                    }
                    return [];
                },
            },
            {
                id: 'accessibility-task-instructions',
                name: 'Clear Task Instructions',
                pillar: 'accessibility',
                severity: 'warning',
                description: 'Tasks should have clear, detailed instructions',
                validate: (exp) => {
                    const issues: ValidationIssue[] = [];
                    const allTasks = (exp.claims ?? []).flatMap(c => c.tasks ?? []);

                    for (const task of allTasks) {
                        if ((task.instructions?.length ?? 0) < 20) {
                            issues.push({
                                field: `accessibility.tasks[${task.id}].instructions`,
                                severity: 'warning',
                                message: `Task "${task.title}" has brief instructions`,
                                suggestion: 'Expand task instructions for clarity',
                            });
                        }
                    }
                    return issues;
                },
            },
            {
                id: 'accessibility-reading-level',
                name: 'Reading Level Match',
                pillar: 'accessibility',
                severity: 'info',
                description: 'Reading level should match target grade',
                validate: (exp) => {
                    const grade = exp.gradeAdaptation;
                    if (!grade) return [];

                    const gradeToReading: Record<string, [number, number]> = {
                        k_2: [0, 2],
                        grade_3_5: [3, 5],
                        grade_6_8: [6, 8],
                        grade_9_12: [9, 12],
                        undergraduate: [12, 16],
                        graduate: [14, 18],
                        professional: [14, 20],
                    };

                    const [min, max] = gradeToReading[grade.gradeRange] || [0, 20];
                    if (grade.readingLevel < min || grade.readingLevel > max) {
                        return [{
                            field: 'accessibility.readingLevel',
                            severity: 'info',
                            message: `Reading level (${grade.readingLevel}) may not match ${grade.gradeRange}`,
                            suggestion: `Consider reading level ${min}-${max} for this grade range`,
                        }];
                    }
                    return [];
                },
            },
        ];
    }
}

/**
 * Factory function to create Content Studio validator.
 * 
 * @doc.type function
 * @doc.purpose Factory for ContentStudioValidator
 * @doc.layer plugin
 * @doc.pattern Factory
 */
export function createContentStudioValidator(
    config?: ContentStudioValidatorConfig
): ContentStudioValidator {
    return new ContentStudioValidator(config);
}
