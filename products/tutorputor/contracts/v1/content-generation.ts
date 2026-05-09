/**
 * Canonical Content Generation Contracts
 *
 * TypeScript type definitions derived from the authoritative proto:
 * contracts/proto/content_generation.proto
 *
 * This file is the single source of truth for TypeScript content generation
 * contracts. All consumers must use these types to ensure consistency with
 * the proto contract used by Java AI agents and other services.
 *
 * DO NOT modify without updating the proto file and all consumers.
 *
 * @doc.type module
 * @doc.purpose Canonical TypeScript contracts for content generation
 * @doc.layer contracts
 * @doc.pattern Contract
 */

// =============================================================================
// Common Types
// =============================================================================

export interface RequestContext {
  request_id: string;
  tenant_id: string;
  timestamp: string; // ISO 8601 timestamp
  metadata: Record<string, string>;
}

export interface LearningClaim {
  claim_ref: string;
  text: string;
  bloom_level: string;
  order_index: number;
  content_needs: ContentNeeds;
}

export interface ContentNeeds {
  examples: ExampleNeeds;
  simulation: SimulationNeeds;
  animation: AnimationNeeds;
}

export interface ExampleNeeds {
  required: boolean;
  types: string[];
  count: number;
  necessity: number;
  rationale: string;
}

export interface SimulationNeeds {
  required: boolean;
  interaction_type: string;
  complexity: string;
  necessity: number;
  rationale: string;
}

export interface AnimationNeeds {
  required: boolean;
  animation_type: string;
  duration_seconds: number;
  necessity: number;
  rationale: string;
}

// =============================================================================
// Generate Claims
// =============================================================================

export interface GenerateClaimsRequest {
  context: RequestContext;
  topic: string;
  grade_level: string;
  domain: string;
  max_claims: number;
  context_params: Record<string, string>;
  language: string;
}

export interface GenerateClaimsResponse {
  context: RequestContext;
  claims: LearningClaim[];
  validation: ValidationResult;
  metadata: GenerationMetadata;
}

// =============================================================================
// Analyze Content Needs
// =============================================================================

export interface AnalyzeContentNeedsRequest {
  request_id: string;
  tenant_id: string;
  claim_text: string;
  bloom_level: string;
  domain: string;
  grade_level: string;
  context: Record<string, string>;
}

export interface AnalyzeContentNeedsResponse {
  request_id: string;
  content_needs: ContentNeeds;
  metadata: GenerationMetadata;
}

export interface ValidationResult {
  valid: boolean;
  confidence_score: number;
  issues: string[];
  suggestions: string[];
}

export interface GenerationMetadata {
  model_name: string;
  tokens_used: number;
  generation_time_ms: number;
  temperature: number;
  prompt_hash: string;
  timestamp: string;
}

// =============================================================================
// Generate Examples
// =============================================================================

export interface GenerateExamplesRequest {
  request_id: string;
  tenant_id: string;
  claim_ref: string;
  claim_text: string;
  example_types: string[];
  count: number;
  domain: string;
  grade_level: string;
  context: Record<string, string>;
}

export interface GenerateExamplesResponse {
  request_id: string;
  examples: Example[];
  metadata: GenerationMetadata;
}

export interface Example {
  example_id: string;
  type: string;
  title: string;
  description: string;
  content: string;
  tags: string[];
  relevance_score: number;
}

// =============================================================================
// Generate Simulation
// =============================================================================

export interface GenerateSimulationRequest {
  request_id: string;
  tenant_id: string;
  claim_ref: string;
  claim_text: string;
  interaction_type: string;
  complexity: string;
  domain: string;
  grade_level: string;
  context: Record<string, string>;
}

export interface GenerateSimulationResponse {
  request_id: string;
  manifest: SimulationManifest;
  metadata: GenerationMetadata;
}

export interface SimulationManifest {
  manifest_id: string;
  version: string;
  domain: string;
  title: string;
  description: string;
  entities: Entity[];
  steps: Step[];
  keyframes: Keyframe[];
  domain_config: string; // JSON string
}

export interface Entity {
  id: string;
  label: string;
  entity_type: string;
  visual: string; // JSON string
  position: Position;
}

export interface Position {
  x: number;
  y: number;
  z: number;
}

export interface Step {
  id: string;
  description: string;
  duration: number;
  actions: string[]; // JSON strings
}

export interface Keyframe {
  id: string;
  time_ms: number;
  state: string; // JSON string
}

// =============================================================================
// Generate Animation
// =============================================================================

export interface GenerateAnimationRequest {
  request_id: string;
  tenant_id: string;
  claim_ref: string;
  claim_text: string;
  animation_type: string;
  duration_seconds: number;
  domain: string;
  grade_level: string;
  context: Record<string, string>;
}

export interface GenerateAnimationResponse {
  request_id: string;
  manifest: AnimationManifest;
  metadata: GenerationMetadata;
}

export interface AnimationManifest {
  manifest_id: string;
  version: string;
  title: string;
  description: string;
  duration_seconds: number;
  scenes: AnimationScene[];
  cues: AnimationCue[];
  narration_script: string;
}

export interface AnimationScene {
  scene_id: string;
  start_time_ms: number;
  end_time_ms: number;
  description: string;
  visual_elements: string[];
}

export interface AnimationCue {
  cue_id: string;
  time_ms: number;
  type: string; // 'highlight', 'focus', 'label', 'fade'
  target: string;
  effect: string;
}

// =============================================================================
// Validate Content
// =============================================================================

export interface ValidateContentRequest {
  request_id: string;
  tenant_id: string;
  experience_id: string;
  title: string;
  description: string;
  claim_texts: string[];
  domain: string;
  metadata: Record<string, string>;
}

export interface ValidateContentResponse {
  request_id: string;
  status: "valid" | "invalid" | "warning";
  overall_score: number;
  can_publish: boolean;
  dimension_scores: Record<string, number>; // educational, experiential, safety, technical, accessibility
  issues: ValidationIssue[];
  issue_count: number;
  metadata: GenerationMetadata;
}

export interface ValidationIssue {
  issue_id: string;
  dimension: string; // educational, experiential, safety, technical, accessibility
  severity: "error" | "warning" | "info";
  message: string;
  suggestion: string;
}

// =============================================================================
// Enhance Content
// =============================================================================

export interface EnhanceContentRequest {
  request_id: string;
  tenant_id: string;
  experience_id: string;
  title: string;
  description: string;
  claim_texts: string[];
  domain: string;
  grade_level: string;
  enhancement_types: string[]; // engagement, real_world, assessment, scaffolding
  context: Record<string, string>;
}

export interface EnhanceContentResponse {
  request_id: string;
  enhancements: Enhancement[];
  overall_confidence: number;
  enhancement_count: number;
  metadata: GenerationMetadata;
}

export interface Enhancement {
  enhancement_id: string;
  type: string; // engagement, real_world, assessment, scaffolding
  title: string;
  description: string;
  rationale: string;
  confidence: number;
  implementation: string; // JSON string with implementation details
}

// =============================================================================
// Health Check
// =============================================================================

export interface HealthCheckRequest {
  service_name: string;
}

export interface HealthCheckResponse {
  status: string;
  timestamp: string;
  service_details: Record<string, string>;
  dependencies: string[];
}
