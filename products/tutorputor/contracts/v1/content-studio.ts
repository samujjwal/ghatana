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
import type { RiskLevel } from "./types";

// ============================================================================
// Enums & Literal Types
// ============================================================================

export type ExperienceStatus = "draft" | "review" | "published" | "archived";

export type GradeRange =
  | "k_2"
  | "grade_3_5"
  | "grade_6_8"
  | "grade_9_12"
  | "undergraduate"
  | "graduate"
  | "professional";

export type RigorLevel =
  | "conceptual"
  | "procedural"
  | "analytical"
  | "synthesis";

export type MathLevel =
  | "none"
  | "arithmetic"
  | "pre_algebra"
  | "algebra"
  | "calculus"
  | "advanced";

export type ContentScaffoldingLevel = "high" | "medium" | "low" | "none";

export type ValidationPillar =
  | "educational"
  | "experiential"
  | "safety"
  | "technical"
  | "accessibility";

export type AIOperation =
  | "generate_claims"
  | "generate_evidence"
  | "generate_tasks"
  | "adapt_grade"
  | "enhance_accessibility"
  | "validate_alignment"
  | "suggest_improvements";

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
  severity: "error" | "warning" | "info";
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
  status: "valid" | "invalid" | "warnings";
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
// Authoring Lifecycle Events
// ============================================================================

/**
 * Canonical event types for the authoring lifecycle.
 * Each major state transition writes an event record for observability.
 */
export type AuthoringEventType =
  | "CREATED"
  | "UPDATED"
  | "VALIDATED"
  | "PUBLISHED"
  | "UNPUBLISHED"
  | "ARCHIVED"
  | "CONTENT_CHANGED"
  | "CLAIMS_GENERATED"
  | "GRADE_ADAPTED"
  | "REFINED"
  | "REVIEW_SUBMITTED"
  | "REVIEW_DECISION"
  | "SIMULATION_LINKED"
  | "SIMULATION_UNLINKED"
  | "ANALYTICS_VIEWED"
  | "ANALYTICS_UPDATED";

/**
 * An authoring lifecycle event record.
 */
export interface AuthoringEvent {
  /** Unique event identifier */
  id: string;
  /** Experience this event relates to */
  experienceId: string;
  /** Event type */
  eventType: AuthoringEventType;
  /** User or system actor that triggered the event */
  actorId: string;
  /** Event-specific metadata */
  metadata?: Record<string, unknown>;
  /** When the event occurred */
  createdAt: Date;
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
  sections?: ("claims" | "evidence" | "tasks" | "grade_adaptation")[];
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
  createExperience(
    request: CreateExperienceRequest,
  ): Promise<ExperienceOperationResult>;

  /**
   * Refine an existing experience based on user feedback.
   */
  refineExperience(
    request: RefineExperienceRequest,
  ): Promise<ExperienceOperationResult>;

  /**
   * Validate an experience against all 5 pillars.
   */
  validateExperience(experienceId: string): Promise<ExperienceValidationResult>;

  /**
   * Publish an experience (requires passing validation).
   */
  publishExperience(
    request: PublishExperienceRequest,
  ): Promise<ExperienceOperationResult>;

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
  adaptGradeLevel(
    experienceId: string,
    targetGrade: GradeRange,
  ): Promise<ExperienceOperationResult>;

  /**
   * Get AI suggestions for improving an experience.
   */
  getSuggestions(experienceId: string): Promise<AIGenerationResult<string[]>>;
}

// ============================================================================
// Canonical Content Asset Types (P1.1)
// ============================================================================

/**
 * Asset type classification for canonical content.
 */
export type ContentAssetType =
  | "explainer"
  | "module"
  | "example_set"
  | "simulation"
  | "animation"
  | "assessment"
  | "pathway"
  | "reference_pack";

/**
 * Lifecycle status for canonical assets.
 */
export type ContentAssetStatus =
  | "draft"
  | "validating"
  | "review"
  | "approved"
  | "published"
  | "archived";

/**
 * Typed block classification within a content asset.
 */
export type ContentBlockType =
  | "text_explainer"
  | "worked_example"
  | "data_table"
  | "visual_sequence"
  | "simulation_entry"
  | "animation_entry"
  | "question_set"
  | "task"
  | "reflection"
  | "hint"
  | "tutor_prompt"
  | "evidence_capture";

/**
 * Artifact manifest type classification.
 */
export type ArtifactManifestType =
  | "worked_example"
  | "simulation"
  | "animation"
  | "assessment";

/**
 * Canonical content asset — the Single root aggregate for discoverable content.
 */
export interface ContentAsset {
  id: string;
  tenantId: string;
  slug: string;
  title: string;
  assetType: ContentAssetType;
  domain: string;
  conceptId?: string;

  status: ContentAssetStatus;
  currentVersion: number;
  qualityScore?: number;

  semanticIndexStatus?: "pending" | "indexed" | "stale";
  recommendationStatus?: "pending" | "computed" | "stale";
  tags?: string[];

  targetGrades: string[];
  difficultyLevel?: string;

  authorId: string;
  lastEditedBy?: string;
  publishedAt?: string;
  createdAt: string;
  updatedAt: string;

  promptHash?: string;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  confidenceScore?: number;

  legacyModuleId?: string;
  legacyExperienceId?: string;
}

/**
 * Immutable revision record for a content asset.
 */
export interface ContentAssetRevision {
  id: string;
  assetId: string;
  version: number;
  changeNote?: string;
  snapshot: Record<string, unknown>;
  qualityScore?: number;
  validationId?: string;
  createdBy: string;
  createdAt: string;
}

/**
 * Typed content block within a canonical asset.
 */
export interface ContentBlock {
  id: string;
  assetId: string;
  blockRef: string;
  blockType: ContentBlockType;
  orderIndex: number;
  title?: string;
  payload: Record<string, unknown>;
  claimRefs?: string[];
  evidenceRefs?: string[];
  createdAt: string;
  updatedAt: string;
}

/**
 * Machine-validatable artifact manifest attached to a content asset.
 */
export interface ArtifactManifest {
  id: string;
  assetId: string;
  manifestType: ArtifactManifestType;
  version: string;
  claimRef?: string;
  manifest: Record<string, unknown>;
  schema?: string;
  isValid: boolean;
  validationErrors?: ValidationCheck[];
  generatedBy?: "ai" | "human" | "hybrid";
  generationId?: string;
  createdAt: string;
  updatedAt: string;
}

// ============================================================================
// Semantic Chunking & Embedding Types (P2.1)
// ============================================================================

/**
 * Source of a semantic chunk.
 */
export type ChunkSource = "block" | "claim" | "manifest" | "metadata";

/**
 * Status of embedding generation for a chunk.
 */
export type EmbeddingStatus =
  | "pending"
  | "processing"
  | "ready"
  | "failed"
  | "stale";

/**
 * A semantically meaningful text fragment extracted from a content asset.
 */
export interface SemanticChunk {
  id: string;
  assetId: string;
  chunkRef: string;
  source: ChunkSource;
  sourceRef: string;
  sequenceIdx: number;
  text: string;
  tokenCount: number;
  contentHash: string;
  embeddingStatus: EmbeddingStatus;
  domain?: string;
  claimRefs?: string[];
  tags?: string[];
  createdAt: string;
  updatedAt: string;
}

/**
 * Request to trigger semantic indexing for one or more assets.
 */
export interface SemanticIndexRequest {
  tenantId: string;
  assetIds: string[];
  /** Force re-chunking even if content hash matches */
  force?: boolean;
}

/**
 * Result of semantic indexing for an asset.
 */
export interface SemanticIndexResult {
  assetId: string;
  chunksCreated: number;
  chunksUpdated: number;
  chunksStale: number;
  embeddingsPending: number;
}

// ============================================================================
// P2.2 — Hybrid Search Ranking
// ============================================================================

/**
 * Ranking signal source contributing to the final search score.
 */
export type RankingSignal =
  | "lexical"
  | "semantic"
  | "quality"
  | "recency"
  | "popularity"
  | "learner_fit";

/**
 * Explanation of how a search result was ranked.
 */
export interface RankingExplanation {
  /** Overall combined score */
  score: number;
  /** Individual signal contributions */
  signals: Array<{
    source: RankingSignal;
    weight: number;
    rawScore: number;
    contribution: number;
  }>;
  /** Why this result matched */
  matchReason: string;
}

/**
 * A hybrid search result combining lexical and semantic signals.
 */
export interface HybridSearchResult {
  asset: ContentAsset;
  ranking: RankingExplanation;
  highlights: Array<{
    field: string;
    snippet: string;
  }>;
}

/**
 * Options for hybrid search.
 */
export interface HybridSearchOptions {
  tenantId: string;
  query: string;
  assetTypes?: ContentAssetType[];
  domain?: string;
  limit?: number;
  offset?: number;
  /** Whether to include ranking explanation metadata */
  explain?: boolean;
  /** Signal weights override (must sum to ~1.0) */
  weights?: Partial<Record<RankingSignal, number>>;
}

/**
 * Response from hybrid search.
 */
export interface HybridSearchResponse {
  results: HybridSearchResult[];
  total: number;
  took: number;
  /** Signals used for ranking */
  rankingSignals: RankingSignal[];
}

// ============================================================================
// P2.3 — Related Assets & Recommendation Edges
// ============================================================================

/**
 * Type of learning relationship between two assets.
 */
export type RecommendationEdgeType =
  | "prerequisite"
  | "follow_up"
  | "related"
  | "alternative"
  | "deeper_dive";

/**
 * Source/method that generated a recommendation edge.
 */
export type RecommendationSource =
  | "rule_based"
  | "semantic"
  | "outcome_aware"
  | "manual";

/**
 * A directed learning relationship between two content assets.
 */
export interface RecommendationEdge {
  id: string;
  sourceAssetId: string;
  targetAssetId: string;
  edgeType: RecommendationEdgeType;
  source: RecommendationSource;
  weight: number;
  confidence?: number;
  reason?: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

/**
 * A next-step suggestion combining asset + recommendation context.
 */
export interface NextStepSuggestion {
  asset: ContentAsset;
  edge: RecommendationEdge;
  reason: string;
}

/**
 * Response for related assets endpoint.
 */
export interface RelatedAssetsResponse {
  prerequisites: NextStepSuggestion[];
  followUps: NextStepSuggestion[];
  related: NextStepSuggestion[];
  alternatives: NextStepSuggestion[];
}

/**
 * Request to bootstrap recommendation edges for an asset.
 */
export interface BootstrapEdgesRequest {
  tenantId: string;
  assetId: string;
  strategies: RecommendationSource[];
}

// =============================================================================
// P3.1 — Generation Planner & Request Model
// =============================================================================

/**
 * Status lifecycle for generation requests.
 */
export type GenerationRequestStatus =
  | "draft"
  | "planning"
  | "planned"
  | "executing"
  | "completed"
  | "failed"
  | "cancelled";

/**
 * Status for individual generation jobs.
 */
export type GenerationJobStatus =
  | "pending"
  | "running"
  | "completed"
  | "failed"
  | "cancelled";

/**
 * Types of generation jobs.
 */
export type GenerationJobType =
  | "claim"
  | "explainer"
  | "worked_example"
  | "simulation"
  | "animation"
  | "assessment"
  | "evaluation";

/**
 * Review routing path determined by planner.
 */
export type ReviewPath = "auto_publish" | "human_review" | "expert_review";

/**
 * Descriptor for a planned asset generation output.
 */
export interface PlannedAssetDescriptor {
  jobType: GenerationJobType;
  targetRef: string;
  description: string;
  estimatedTokens?: number;
  dependsOn?: string[];
}

/**
 * Cost estimation for a generation request.
 */
export interface GenerationCostEstimate {
  totalTokens: number;
  embeddingCalls: number;
  llmCalls: number;
  estimatedDurationMs: number;
}

/**
 * Canonical generation request.
 */
export interface GenerationRequest {
  id: string;
  tenantId: string;
  title: string;
  description?: string;
  domain: string;
  conceptId?: string;
  targetGrades?: string[];
  requestedBy: string;
  status: GenerationRequestStatus;
  plannedAssets?: PlannedAssetDescriptor[];
  artifactNeeds?: Record<string, number>;
  riskLevel: RiskLevel;
  riskFactors?: string[];
  reviewPath: ReviewPath;
  estimatedCost?: GenerationCostEstimate;
  totalJobs: number;
  completedJobs: number;
  failedJobs: number;
  plannedAt?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Individual generation job within a request.
 */
export interface GenerationJob {
  id: string;
  requestId: string;
  jobType: GenerationJobType;
  targetRef?: string;
  inputPrompt?: string;
  parameters?: Record<string, unknown>;
  status: GenerationJobStatus;
  progress: number;
  outputAssetId?: string;
  outputData?: Record<string, unknown>;
  diagnostics?: Record<string, unknown>;
  errorMessage?: string;
  retryCount: number;
  maxRetries: number;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Result of the planning phase.
 */
export interface PlanningResult {
  requestId: string;
  plannedAssets: PlannedAssetDescriptor[];
  artifactNeeds: Record<string, number>;
  riskLevel: RiskLevel;
  riskFactors: string[];
  reviewPath: ReviewPath;
  estimatedCost: GenerationCostEstimate;
  totalJobs: number;
}

/**
 * Request to create a generation request.
 */
export interface CreateGenerationRequestInput {
  tenantId: string;
  title: string;
  description?: string;
  domain: string;
  conceptId?: string;
  targetGrades?: string[];
  requestedBy: string;
}

/**
 * Response for a generation request with its jobs.
 */
export interface GenerationRequestWithJobs extends GenerationRequest {
  jobs: GenerationJob[];
}

// =============================================================================
// P3.3 — Evaluation & Guardrail Scorecards
// =============================================================================

export type EvaluationStatus =
  | "pending"
  | "running"
  | "passed"
  | "failed"
  | "skipped";

export type PublishRecommendation = "auto_publish" | "manual_review" | "block";

export interface EvaluationIssue {
  dimension: string;
  severity: "info" | "warning" | "error";
  message: string;
  detail?: string;
}

export interface EvaluationRecord {
  id: string;
  tenantId: string;

  assetId?: string;
  generationJobId?: string;
  generationRequestId?: string;

  coherenceScore?: number;
  completenessScore?: number;
  safetyScore?: number;
  accessibilityScore?: number;
  manifestValidityScore?: number;

  overallScore?: number;
  status: EvaluationStatus;
  recommendation: PublishRecommendation;

  issues?: EvaluationIssue[];
  diagnostics?: Record<string, unknown>;
  errorMessage?: string;

  createdAt: string;
  updatedAt: string;
}

export interface EvaluationScorecard {
  evaluationId: string;
  generationRequestId?: string;
  overallScore: number;
  recommendation: PublishRecommendation;
  dimensions: {
    coherence: number;
    completeness: number;
    safety: number;
    accessibility: number;
    manifestValidity: number;
  };
  issues: EvaluationIssue[];
  blockedReasons: string[];
}

export interface TriggerEvaluationInput {
  tenantId: string;
  generationRequestId: string;
}

// =============================================================================
// P3.4 — Generation Review Decision
// =============================================================================

export type GenerationReviewDecisionStatus =
  | "pending"
  | "approved"
  | "rejected"
  | "regeneration_requested";

export interface GenerationReviewDecision {
  id: string;
  tenantId: string;
  requestId: string;
  status: GenerationReviewDecisionStatus;
  reviewedBy?: string;
  decisionNote?: string;
  regenerateJobIds?: string[];
  reviewedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SubmitReviewDecisionInput {
  requestId: string;
  status: "approved" | "rejected" | "regeneration_requested";
  decisionNote?: string;
  regenerateJobIds?: string[];
}

// =============================================================================
// P4.1 — Explorer Telemetry Events
// =============================================================================

export type ExplorerEventType =
  | "impression"
  | "click"
  | "query_reformulation"
  | "asset_start"
  | "asset_complete"
  | "next_step_select"
  | "ranking_feedback";

export interface ExplorerEvent {
  id: string;
  tenantId: string;
  userId?: string;
  sessionId?: string;
  eventType: ExplorerEventType;
  query?: string;
  assetId?: string;
  assetType?: string;
  position?: number;
  score?: number;
  feedbackLabel?: string;
  feedbackScore?: number;
  metadata?: Record<string, unknown>;
  occurredAt: string;
}

export interface TrackExplorerEventInput {
  userId?: string;
  sessionId?: string;
  eventType: ExplorerEventType;
  query?: string;
  assetId?: string;
  assetType?: string;
  position?: number;
  score?: number;
  feedbackLabel?: string;
  feedbackScore?: number;
  metadata?: Record<string, unknown>;
  occurredAt?: string;
}

export interface TrackBatchEventsInput {
  events: TrackExplorerEventInput[];
}

// =============================================================================
// P4.3 — Regeneration Candidates
// =============================================================================

export type RegenerationTrigger =
  | "poor_discovery_performance"
  | "poor_learning_outcomes"
  | "misconception_pattern"
  | "stale_curriculum"
  | "safety_concern"
  | "low_evaluation_score"
  | "manual_flagged";

export type RegenerationCandidateStatus =
  | "open"
  | "queued"
  | "in_progress"
  | "resolved"
  | "dismissed";

export interface RegenerationCandidate {
  id: string;
  tenantId: string;
  assetId: string;
  assetType?: string;
  trigger: RegenerationTrigger;
  severity: RiskLevel;
  reason: string;
  evidence?: Record<string, unknown>;
  priority: number;
  status: RegenerationCandidateStatus;
  generationRequestId?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRegenerationCandidateInput {
  assetId: string;
  assetType?: string;
  trigger: RegenerationTrigger;
  severity?: RiskLevel;
  reason: string;
  evidence?: Record<string, unknown>;
  priority?: number;
}

// =============================================================================
// P4.4 — Closed-Loop Publish & Reindex
// =============================================================================

export interface PublishAssetInput {
  assetId: string;
  changeNote?: string;
  bypassEvaluationCheck?: boolean;
}

export interface PublishResult {
  assetId: string;
  published: boolean;
  reason?: string;
  publishedAt?: string;
  semanticIndexStatus?: "pending" | "indexed" | "stale";
  recommendationStatus?: "pending" | "computed" | "stale";
  semanticIndexQueued?: boolean;
  recommendationRecomputeQueued?: boolean;
}
