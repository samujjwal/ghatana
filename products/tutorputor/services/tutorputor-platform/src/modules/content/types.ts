/**
 * Content Module Domain Types
 *
 * Presentation-layer interfaces for the content domain. These represent
 * mapped/serialized forms of Prisma model data returned by service methods
 * (lowercased enums, ISO date strings, optional fields).
 *
 * Raw Prisma model types are imported from @tutorputor/core/db.
 */

import type { RiskLevel } from "@tutorputor/contracts/v1/types";

// ============================================================================
// Content Asset Types (presentation layer)
// ============================================================================

export type ContentAssetType =
  | "explainer"
  | "module"
  | "example_set"
  | "simulation"
  | "animation"
  | "assessment"
  | "pathway"
  | "reference_pack";

export type ContentAssetStatus =
  | "draft"
  | "validating"
  | "review"
  | "approved"
  | "published"
  | "archived";

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

export type ArtifactManifestType =
  | "worked_example"
  | "simulation"
  | "animation"
  | "assessment";

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
  reviewState?: string;
  searchableText?: string;
}

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

export interface ArtifactManifest {
  id: string;
  assetId: string;
  manifestType: ArtifactManifestType;
  version: string;
  claimRef?: string;
  manifest: Record<string, unknown>;
  schema?: string;
  isValid: boolean;
  validationErrors?: string[];
  generatedBy?: "human" | "ai" | "hybrid";
  generationId?: string;
  createdAt: string;
  updatedAt: string;
}

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

// ============================================================================
// Generation Types
// ============================================================================

export type GenerationJobType =
  | "claim"
  | "explainer"
  | "worked_example"
  | "simulation"
  | "animation"
  | "assessment"
  | "evaluation";

export type GenerationJobStatus =
  | "pending"
  | "running"
  | "completed"
  | "failed"
  | "cancelled";

export type GenerationRequestStatus =
  | "draft"
  | "planning"
  | "planned"
  | "executing"
  | "completed"
  | "failed"
  | "cancelled";

export type ReviewPath = "auto_publish" | "human_review" | "expert_review";

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
  riskLevel: string;
  reviewPath: ReviewPath;
  totalJobs: number;
  completedJobs: number;
  failedJobs: number;
  plannedAssets?: PlannedAssetDescriptor[];
  artifactNeeds?: Record<string, number>;
  riskFactors?: string[];
  estimatedCost?: GenerationCostEstimate;
  plannedAt?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface GenerationJob {
  id: string;
  requestId: string;
  jobType: GenerationJobType;
  status: GenerationJobStatus;
  progress: number;
  retryCount: number;
  maxRetries: number;
  targetRef?: string;
  inputPrompt?: string;
  parameters?: Record<string, unknown>;
  outputAssetId?: string;
  outputData?: Record<string, unknown>;
  diagnostics?: Record<string, unknown>;
  errorMessage?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface GenerationRequestWithJobs extends GenerationRequest {
  jobs: GenerationJob[];
}

export interface GenerationExecutionSnapshot {
  request: GenerationRequestWithJobs;
  progress: GenerationExecutionProgress;
  events: GenerationExecutionEvent[];
}

export interface GenerationExecutionProgress {
  totalJobs: number;
  completedJobs: number;
  failedJobs: number;
  runningJobs: number;
  pendingJobs: number;
  cancelledJobs: number;
  completionPercent: number;
  terminal: boolean;
  cost?: GenerationExecutionCostSummary;
  latestWorkerStage?: string;
  latestWorkerMessage?: string;
}

export interface GenerationExecutionEvent {
  type: string;
  at: string;
  requestId: string;
  jobId?: string;
  jobType?: string;
  status: string;
  message: string;
  stage?: string;
  progressPercent?: number;
  diagnostics?: Record<string, unknown>;
  cost?: GenerationExecutionWorkerTelemetryCost;
}

export interface GenerationExecutionCostSummary {
  estimatedTokens: number;
  actualTokens: number;
  estimatedCostUsd: number;
  actualCostUsd: number;
}

export interface GenerationExecutionWorkerTelemetryCost {
  model?: string;
  generationTimeMs?: number;
  estimatedTokens?: number;
  actualTokens?: number;
  estimatedCostUsd?: number;
  actualCostUsd?: number;
}

export interface GenerationExecutionWorkerTelemetry {
  at: string;
  requestId: string;
  jobId: string;
  stage: string;
  message: string;
  status?: string;
  progressPercent?: number;
  diagnostics?: Record<string, unknown>;
  cost?: GenerationExecutionWorkerTelemetryCost;
}

// ============================================================================
// Planning Types
// ============================================================================

export interface GenerationRequestConfig {
  maxBudgetUsd?: number;
  preferredModels?: string[];
  quality?: "standard" | "high" | "premium";
  maxRetries?: number;
  excludeJobTypes?: string[];
  urgent?: boolean;
  minQualityScore?: number;
  learnerArchetype?: string;
}

export interface CreateGenerationRequestInput {
  tenantId: string;
  title: string;
  description?: string;
  domain: string;
  conceptId?: string;
  targetGrades?: string[];
  requestedBy: string;
  requestConfig?: GenerationRequestConfig;
}

export interface PlannedAssetDescriptor {
  jobType: GenerationJobType;
  targetRef: string;
  description: string;
  estimatedTokens: number;
  dependsOn?: string[];
}

export interface GenerationCostEstimate {
  totalTokens: number;
  estimatedSpendUsd?: number;
  estimatedCostUsd?: number;
  perJobEstimates?: Record<string, number>;
  cacheSavingsUsd?: number;
  embeddingCalls?: number;
  llmCalls?: number;
  estimatedDurationMs?: number;
}

export interface GenerationRoutingDecision {
  useCache: boolean;
  estimatedSpendUsd: number;
  selectedProvider?: string;
  reason?: string;
}

export interface PlanningResult {
  requestId: string;
  plannedAssets: PlannedAssetDescriptor[];
  artifactNeeds: Record<string, number>;
  riskLevel: RiskLevel;
  riskFactors: string[];
  reviewPath: ReviewPath;
  estimatedCost: GenerationCostEstimate;
  routingDecision: GenerationRoutingDecision;
  totalJobs: number;
}

// ============================================================================
// Recommendation Types
// ============================================================================

export type RecommendationEdgeType =
  | "PREREQUISITE"
  | "FOLLOW_UP"
  | "RELATED"
  | "ALTERNATIVE"
  | "DEEPENING"
  | "PROGRESSION";

export type RecommendationSource =
  | "rule_based"
  | "feedback_boosted"
  | "outcome_aware"
  | "manual";

export interface RecommendationEdge {
  id: string;
  tenantId: string;
  sourceAssetId: string;
  targetAssetId: string;
  edgeType: RecommendationEdgeType;
  source: RecommendationSource;
  weight: number;
  pathwayAffinity: number;
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface NextStepSuggestion {
  asset: ContentAsset;
  edgeType: RecommendationEdgeType;
  weight: number;
  pathwayAffinity: number;
  reason: string;
}

export interface RelatedAssetsResponse {
  assetId: string;
  related: NextStepSuggestion[];
  total: number;
}

// ============================================================================
// Search Types
// ============================================================================

export type RankingSignal =
  | "lexical"
  | "semantic"
  | "quality"
  | "recency"
  | "popularity"
  | "learner_fit";

export interface HybridSearchOptions {
  tenantId: string;
  query: string;
  assetTypes?: ContentAssetType[];
  domain?: string;
  limit?: number;
  offset?: number;
  explain?: boolean;
  weights?: Partial<Record<RankingSignal, number>>;
}

export interface HybridSearchResult {
  asset: ContentAsset;
  ranking: RankingExplanation;
  highlights: Array<{ field: string; snippet: string }>;
}

export interface HybridSearchResponse {
  results: HybridSearchResult[];
  total: number;
  took: number;
  rankingSignals: RankingSignal[];
}

export interface RankingExplanation {
  score: number;
  signals: Array<{
    source: RankingSignal;
    weight: number;
    rawScore: number;
    contribution: number;
  }>;
  matchReason: string;
}

// ============================================================================
// Remediation Types
// ============================================================================

export type RemediationAction =
  | "apply_quality_predictions"
  | "recompute_asset_outcomes"
  | "refresh_recommendation_edges"
  | "scan_adaptive_drift"
  | "promote_experiment_winners"
  | "evaluate_active_experiments";

export interface RemediationPolicyWeights {
  quality: number;
  outcomes: number;
  drift: number;
  experiments: number;
  recommendations: number;
}

export interface RemediationPolicyBreakdown {
  primaryDriver: string;
  policySource: string;
  qualityPriority: number;
  outcomePriority: number;
  driftPriority: number;
  experimentPriority: number;
  recommendationPriority: number;
  learnedWeights?: RemediationPolicyWeights;
  causalWeights?: RemediationPolicyWeights;
  modelConfidence?: number;
}

export interface ExperienceRemediationSummary {
  experienceId: string;
  totalAssets: number;
  healthyAssets: number;
  watchAssets: number;
  interveneAssets: number;
  driftSignalCount: number;
  driftInsightCount: number;
  runningExperiments: number;
  promotableExperiments: number;
  recommendedActions: string[];
  qualityPredictionsApplied?: number;
  recommendationRefresh?: {
    processedAssets: number;
    updatedEdges: number;
    skippedEdges: number;
  };
  policyBreakdown?: RemediationPolicyBreakdown;
  executedActions?: string[];
}

export interface RemediationIntervention {
  action: RemediationAction;
  dimension: string;
  score: number;
  expectedImpact: number;
  confidence: number;
  source: string;
  rationale: string;
}

export interface ExperienceRemediationInterventionPlan {
  experienceId: string;
  primaryDriver: string;
  interventions: RemediationIntervention[];
}

export interface ExperienceRemediationInterventionExecution {
  experienceId: string;
  appliedActions: string[];
  skippedActions: string[];
  limit: number;
  baselineSummary: ExperienceRemediationSummary;
  summary: ExperienceRemediationSummary;
  delta: {
    healthyAssets: number;
    watchAssets: number;
    interveneAssets: number;
    driftSignalCount: number;
    promotableExperiments: number;
  };
}

export interface TenantRemediationPolicyProfile {
  tenantId: string;
  totalPublishedAssets: number;
  staleRecommendationAssets: number;
  lowConfidenceAssets: number;
  watchAssets: number;
  interveneAssets: number;
  runningExperiments: number;
  promotableExperiments: number;
  priorityWeights: RemediationPolicyWeights;
  policyModel: {
    source: string;
    sampleSize: number;
    confidence: number;
    weights: RemediationPolicyWeights;
    observedLift: RemediationPolicyWeights;
  };
  causalModel: {
    source: string;
    sampleSize: number;
    confidence: number;
    weights: RemediationPolicyWeights;
    observedLift: RemediationPolicyWeights;
  };
  policyBlend: RemediationPolicyWeights;
  recommendedFocus: string;
}

export interface TenantRemediationPolicyScenarioAnalysis {
  tenantId: string;
  baselineFocus: string;
  baselineConfidence: number;
  scenarios: Array<{
    scenario: string;
    primaryDriver: string;
    weights: RemediationPolicyWeights;
    expectedPriority: number;
  }>;
  recommendedScenario: string;
}

export interface TenantRemediationPortfolioExperience {
  experienceId: string;
  title?: string;
  priorityScore: number;
  primaryDriver: string;
  totalAssets: number;
  interveneAssets: number;
  driftSignalCount: number;
  promotableExperiments: number;
}

export interface TenantRemediationPortfolio {
  tenantId: string;
  generatedAt: string;
  experiences: TenantRemediationPortfolioExperience[];
}

export interface TenantPortfolioRemediationIntervention
  extends RemediationIntervention {
  experienceId: string;
  title?: string;
  priorityScore: number;
  primaryDriver: string;
}

export interface TenantRemediationPortfolioPlan {
  tenantId: string;
  generatedAt: string;
  interventions: TenantPortfolioRemediationIntervention[];
}

export interface TenantRemediationPortfolioExecution {
  tenantId: string;
  generatedAt: string;
  processedExperiences: number;
  appliedExperiences: number;
  totalAppliedActions: number;
  items: Array<{
    experienceId: string;
    title?: string;
    selectedActions: RemediationAction[];
    result: ExperienceRemediationInterventionExecution;
  }>;
}

// ============================================================================
// Simulation Types
// ============================================================================

export interface SimulationTemplateWithManifest {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  domain: string;
  audience?: string;
  difficulty?: string;
  license?: string;
  status: string;
  parameters?: Record<string, unknown>;
  manifest?: ArtifactManifest;
  createdAt: string;
  updatedAt: string;
}
