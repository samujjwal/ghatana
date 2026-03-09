/**
 * Content Studio Type Definitions
 * 
 * Types for the Unified Content Studio - an AI-first, guardrailed content
 * authoring experience for creating Learning Experiences.
 * 
 * @doc.type module
 * @doc.purpose Content Studio contracts and type definitions
 * @doc.layer contracts
 * @doc.pattern ValueObject
 */

import type { BloomLevel, Claim, Evidence, TaskType } from "./learning-unit";

// ============================================================================
// Enums & Literal Types
// ============================================================================

export type ExperienceStatus = 'draft' | 'validating' | 'review' | 'approved' | 'published' | 'archived';

export type GradeRange = 'k_2' | 'grade_3_5' | 'grade_6_8' | 'grade_9_12' | 'undergraduate' | 'graduate' | 'professional';

export type RigorLevel = 'conceptual' | 'procedural' | 'analytical' | 'synthesis';

export type MathLevel = 'none' | 'arithmetic' | 'pre_algebra' | 'algebra' | 'calculus' | 'advanced';

export type ContentScaffoldingLevel = 'high' | 'medium' | 'low' | 'none';

export type ValidationPillar = 'educational' | 'experiential' | 'safety' | 'technical' | 'accessibility';

export type AIOperation =
    | 'generate_claims'
    | 'generate_evidence'
    | 'generate_tasks'
    | 'adapt_grade'
    | 'enhance_accessibility'
    | 'validate_alignment'
    | 'suggest_improvements';

// ============================================================================
// Grade Adaptation
// ============================================================================

/**
 * Grade-level adaptation parameters for content generation.
 */
export interface GradeAdaptation {
    /** Target grade range */
    gradeRange: GradeRange;
    /** Mathematical complexity level */
    mathLevel: MathLevel;
    /** Academic rigor level */
    rigorLevel: RigorLevel;
    /** Scaffolding support level */
    scaffoldingLevel: ContentScaffoldingLevel;
    /** Vocabulary complexity (1-10 scale) */
    vocabularyComplexity: number;
    /** Reading level (Flesch-Kincaid grade level) */
    readingLevel: number;
    /** Prerequisite concepts required */
    prerequisiteConcepts: string[];
}

// ============================================================================
// Learning Experience
// ============================================================================

/**
 * A Learning Experience is the core content unit in Content Studio.
 * It represents a complete learning journey with claims, evidence, and tasks.
 */
export interface LearningExperience {
    /** Unique identifier */
    id: string;
    /** Tenant ID for multi-tenancy */
    tenantId: string;
    /** URL-friendly slug */
    slug: string;
    /** Human-readable title */
    title: string;
    /** Brief description of the learning experience */
    description: string;
    /** Current workflow status */
    status: ExperienceStatus;
    /** Version number for tracking revisions */
    version: number;
    /** Grade-level adaptation settings */
    gradeAdaptation: GradeAdaptation;
    /** Learning claims within this experience */
    claims: LearningClaim[];
    /** Estimated time to complete in minutes */
    estimatedTimeMinutes: number;
    /** Keywords for search and discovery */
    keywords: string[];
    /** Related module ID */
    moduleId?: string;
    /** Related simulation ID */
    simulationId?: string;
    /** Author user ID */
    authorId?: string;
    /** Creation timestamp */
    createdAt: Date;
    /** Last update timestamp */
    updatedAt: Date;
}

/**
 * A Learning Claim represents what the learner will be able to do.
 */
export interface LearningClaim extends Claim {
    /** Parent experience ID */
    experienceId: string;
    /** Evidence requirements for this claim */
    evidenceRequirements: LearningEvidence[];
    /** Tasks that can generate evidence for this claim */
    tasks: ExperienceTask[];
    /** Mastery threshold (0-1) */
    masteryThreshold: number;
    /** Order within the experience */
    orderIndex: number;
}

/**
 * Learning Evidence defines what observable data proves mastery.
 */
export interface LearningEvidence extends Evidence {
    /** Parent claim ID */
    claimId: string;
    /** Minimum acceptable score (0-1) */
    minimumScore: number;
    /** Weight in claim mastery calculation */
    weight: number;
}

/**
 * Experience Task is an activity that generates evidence.
 */
export interface ExperienceTask {
    /** Unique identifier */
    id: string;
    /** Parent claim ID */
    claimId: string;
    /** Task type classification */
    type: TaskType;
    /** Human-readable title */
    title: string;
    /** Task instructions */
    instructions: string;
    /** Evidence IDs this task can produce */
    evidenceIds: string[];
    /** Estimated duration in minutes */
    estimatedMinutes: number;
    /** Order within the claim */
    orderIndex: number;
}

// ============================================================================
// Validation
// ============================================================================

/**
 * Result of a validation check.
 */
export interface ValidationCheck {
    /** Unique check identifier */
    checkId: string;
    /** Validation pillar this check belongs to */
    pillar: ValidationPillar;
    /** Check name */
    name: string;
    /** Whether the check passed */
    passed: boolean;
    /** Severity if failed: 'error' | 'warning' | 'info' */
    severity: 'error' | 'warning' | 'info';
    /** Human-readable message */
    message: string;
    /** Suggested fix if failed */
    suggestion?: string;
    /** Field or section that failed */
    location?: string;
}

/**
 * Complete validation result for a Learning Experience.
 */
export interface ExperienceValidationResult {
    /** Overall validation status */
    status: 'valid' | 'invalid' | 'warnings';
    /** Whether the experience can be published */
    canPublish: boolean;
    /** Individual check results by pillar */
    checks: ValidationCheck[];
    /** Validation score (0-100) */
    score: number;
    /** Breakdown by pillar */
    pillarScores: Record<ValidationPillar, number>;
    /** Timestamp of validation */
    validatedAt: Date;
}

// ============================================================================
// AI Generation
// ============================================================================

/**
 * Request to generate content using AI.
 */
export interface AIGenerationRequest {
    /** Operation to perform */
    operation: AIOperation;
    /** Current experience state */
    experience: Partial<LearningExperience>;
    /** User's natural language prompt */
    userPrompt?: string;
    /** Target grade adaptation */
    gradeAdaptation: GradeAdaptation;
    /** Context from related content */
    context?: {
        moduleTopic?: string;
        curriculumStandards?: string[];
        relatedClaims?: string[];
    };
}

/**
 * Result of AI content generation.
 */
export interface AIGenerationResult<T> {
    /** Generated content */
    content: T;
    /** Confidence score (0-1) */
    confidence: number;
    /** Explanation of what was generated */
    explanation: string;
    /** Alternative suggestions */
    alternatives?: T[];
    /** Tokens used for cost tracking */
    tokensUsed: number;
    /** Processing time in milliseconds */
    processingTimeMs: number;
}

// ============================================================================
// Service Requests & Responses
// ============================================================================

/**
 * Request to create a new Learning Experience.
 */
export interface CreateExperienceRequest {
    /** Tenant ID */
    tenantId: string;
    /** Initial title */
    title: string;
    /** Natural language description of what to create */
    description: string;
    /** Target grade range */
    gradeRange: GradeRange;
    /** Optional module to link to */
    moduleId?: string;
    /** Author ID */
    authorId?: string;
}

/**
 * Request to refine an existing experience.
 */
export interface RefineExperienceRequest {
    /** Experience ID to refine */
    experienceId: string;
    /** Natural language refinement instructions */
    refinementPrompt: string;
    /** Specific sections to refine (optional, all if empty) */
    sections?: ('claims' | 'evidence' | 'tasks' | 'grade_adaptation')[];
}

/**
 * Request to publish an experience.
 */
export interface PublishExperienceRequest {
    /** Experience ID to publish */
    experienceId: string;
    /** Optional simulation to link */
    simulationId?: string;
    /** Publish notes */
    notes?: string;
}

/**
 * Experience operation result.
 */
export interface ExperienceOperationResult {
    /** Whether the operation succeeded */
    success: boolean;
    /** The experience (if successful) */
    experience?: LearningExperience;
    /** Validation result */
    validation?: ExperienceValidationResult;
    /** Error message (if failed) */
    error?: string;
    /** AI generation details (if applicable) */
    aiGeneration?: {
        operation: AIOperation;
        confidence: number;
        tokensUsed: number;
    };
}

// ============================================================================
// Content Studio Service Interface
// ============================================================================

/**
 * Content Studio Service Interface
 * 
 * The main service interface for the Unified Content Studio.
 */
export interface ContentStudioService {
    /**
     * Create a new Learning Experience from a natural language description.
     * AI generates claims, evidence, and tasks based on the description.
     */
    createExperience(request: CreateExperienceRequest): Promise<ExperienceOperationResult>;

    /**
     * Refine an existing experience based on user feedback.
     */
    refineExperience(request: RefineExperienceRequest): Promise<ExperienceOperationResult>;

    /**
     * Validate an experience against all 5 pillars.
     */
    validateExperience(experienceId: string): Promise<ExperienceValidationResult>;

    /**
     * Publish an experience (requires passing validation).
     */
    publishExperience(request: PublishExperienceRequest): Promise<ExperienceOperationResult>;

    /**
     * Get an experience by ID.
     */
    getExperience(experienceId: string): Promise<LearningExperience | null>;

    /**
     * List experiences with filtering.
     */
    listExperiences(filters: {
        tenantId: string;
        status?: ExperienceStatus;
        gradeRange?: GradeRange;
        authorId?: string;
        limit?: number;
        offset?: number;
    }): Promise<{ experiences: LearningExperience[]; total: number }>;

    /**
     * Adapt experience to a different grade level.
     */
    adaptGradeLevel(experienceId: string, targetGrade: GradeRange): Promise<ExperienceOperationResult>;

    /**
     * Get AI suggestions for improving an experience.
     */
    getSuggestions(experienceId: string): Promise<AIGenerationResult<string[]>>;
}
