import * as grpc from "@grpc/grpc-js";
import * as protoLoader from "@grpc/proto-loader";
import path from "path";
import { fileURLToPath } from "url";
import CircuitBreaker from "opossum";
import { createStandaloneLogger } from "@tutorputor/core/logger";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Proto paths - adjusting for probable location relative to this file
// Project: products/tutorputor/services/tutorputor-platform/src/clients
// Protos: products/tutorputor/contracts/proto
const AI_LEARNING_PROTO_PATH = path.resolve(
  __dirname,
  "../../../../contracts/proto/ai_learning.proto",
);
const CONTENT_GENERATION_PROTO_PATH = path.resolve(
  __dirname,
  "../../../../contracts/proto/content_generation.proto",
);

const GRPC_HOST = process.env.AI_SERVICE_URL || "localhost:50051";

// ─── TypeScript interfaces mirroring the proto message definitions ────────────

// ai_learning.proto
export interface LearningNode {
  id: string;
  title: string;
  type: string;
  description: string;
  estimated_minutes: number;
  prerequisites: string[];
  learning_objectives: string[];
}

export interface GeneratePathRequest {
  subject: string;
  goal: string;
  learner_level: string;
  preferences?: string[];
  context_id?: string;
}

export interface GeneratePathResponse {
  path_id: string;
  title: string;
  description: string;
  nodes: LearningNode[];
}

export interface AnswerSubmission {
  question_id: string;
  text_response: string;
  selected_option_id: string;
  question_text: string;
}

export interface GradeAssessmentRequest {
  assessment_id: string;
  student_id: string;
  answers: AnswerSubmission[];
}

export interface QuestionFeedback {
  question_id: string;
  score: number;
  feedback_text: string;
  correct_answer_explanation: string;
  is_correct: boolean;
}

export interface GradeAssessmentResponse {
  total_score: number;
  feedback: QuestionFeedback[];
  overall_comments: string;
  passed: boolean;
}

export interface AssessmentItemChoiceProto {
  id: string;
  label: string;
  is_correct: boolean;
  rationale: string;
}

export interface AssessmentItemProto {
  prompt: string;
  type: string;
  choices: AssessmentItemChoiceProto[];
  points: number;
  correct_answer_explanation: string;
}

export interface GenerateItemsRequest {
  topic: string;
  objectives: string[];
  difficulty: string;
  count: number;
  learner_level: string;
}

export interface GenerateItemsResponse {
  items: AssessmentItemProto[];
}

export interface RemediationRequest {
  student_id: string;
  topic: string;
  struggle_concepts: string[];
}

export interface RemedialResource {
  title: string;
  url: string;
  type: string;
  reasoning: string;
}

export interface RemediationResponse {
  resources: RemedialResource[];
}

// content_generation.proto
export interface RequestContext {
  request_id: string;
  tenant_id: string;
  timestamp?: unknown;
  metadata: Record<string, string>;
}

export interface ContentClaim {
  claim_id: string;
  claim_text: string;
  claim_ref: string;
  keywords: string[];
  difficulty_level: string;
  domain: string;
  metadata: Record<string, string>;
}

export interface ContentExample {
  example_id: string;
  claim_ref: string;
  scenario: string;
  question: string;
  answer: string;
  explanation: string;
  difficulty: string;
  concepts: string[];
}

export interface SimulationStep {
  step_id: string;
  order_index: number;
  title: string;
  description: string;
  action_type: string;
  parameters: Record<string, string>;
  expected_outcomes: string[];
}

export interface SimulationContent {
  simulation_id: string;
  claim_ref: string;
  title: string;
  description: string;
  steps: SimulationStep[];
  parameters: Record<string, string>;
  difficulty: string;
  estimated_duration_minutes: number;
}

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
  claims: ContentClaim[];
  generation_model: string;
  generated_at?: unknown;
  confidence_score: number;
  warnings: string[];
}

export interface AnalyzeContentNeedsRequest {
  context: RequestContext;
  claims: ContentClaim[];
  target_grade_level: string;
  learning_objective: string;
  available_resources: string[];
}

export interface ContentGap {
  gap_type: string;
  description: string;
  severity: string;
  affected_claims: string[];
}

export interface ContentSuggestion {
  suggestion_type: string;
  description: string;
  priority: string;
  target_claims: string[];
}

export interface ContentNeedsAnalysis {
  gaps: ContentGap[];
  suggestions: ContentSuggestion[];
  recommended_examples_count: number;
  recommended_simulations_count: number;
  complexity_score: number;
}

export interface AnalyzeContentNeedsResponse {
  context: RequestContext;
  analysis: ContentNeedsAnalysis;
  analyzed_at?: unknown;
}

export interface GenerateExamplesRequest {
  context: RequestContext;
  claim_ref: string;
  claim_text: string;
  grade_level: string;
  count: number;
  example_types: string[];
  context_params: Record<string, string>;
}

export interface GenerateExamplesResponse {
  context: RequestContext;
  examples: ContentExample[];
  generation_model: string;
  generated_at?: unknown;
  confidence_score: number;
  warnings: string[];
}

export interface GenerateSimulationRequest {
  context: RequestContext;
  claim_ref: string;
  claim_text: string;
  grade_level: string;
  simulation_type: string;
  max_steps: number;
  duration_minutes: number;
  context_params: Record<string, string>;
}

export interface GenerateSimulationResponse {
  context: RequestContext;
  simulation: SimulationContent;
  generation_model: string;
  generated_at?: unknown;
  confidence_score: number;
  warnings: string[];
}

export interface ValidateContentRequest {
  context: RequestContext;
  claim?: ContentClaim;
  example?: ContentExample;
  simulation?: SimulationContent;
  validation_type: string;
  validation_rules: string[];
}

export interface ValidationIssue {
  issue_type: string;
  severity: string;
  description: string;
  location: string;
  suggested_fixes: string[];
}

export interface ValidationResult {
  is_valid: boolean;
  confidence_score: number;
  issues: ValidationIssue[];
  suggestions: string[];
  overall_assessment: string;
}

export interface ValidateContentResponse {
  context: RequestContext;
  result: ValidationResult;
  validated_at?: unknown;
}

// ─── Internal gRPC plumbing types ─────────────────────────────────────────────

/** Minimal interface for a dynamically-generated gRPC service client. */
interface GrpcServiceClient {
  [method: string]: (
    request: unknown,
    callback: (error: grpc.ServiceError | null, response: any) => void,
  ) => void;
}

/** Constructor type obtained by navigating the loadPackageDefinition tree. */
type GrpcServiceConstructor = new (
  address: string,
  credentials: grpc.ChannelCredentials,
) => GrpcServiceClient;

// Load AiLearning package definition
const learningPackageDef = protoLoader.loadSync(AI_LEARNING_PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true,
});
const learningProtoDescriptor = grpc.loadPackageDefinition(
  learningPackageDef,
) as unknown as {
  tutorputor: { ai_learning: { AiLearningService: GrpcServiceConstructor } };
};
const aiLearningService =
  learningProtoDescriptor.tutorputor.ai_learning.AiLearningService;

// Load ContentGeneration package definition
const contentPackageDef = protoLoader.loadSync(CONTENT_GENERATION_PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true,
});
const contentProtoDescriptor = grpc.loadPackageDefinition(
  contentPackageDef,
) as unknown as {
  tutorputor: {
    content_generation: { ContentGenerationService: GrpcServiceConstructor };
  };
};
// The package name in content_generation.proto is 'tutorputor.content_generation'
const contentGenService =
  contentProtoDescriptor.tutorputor.content_generation.ContentGenerationService;

export class AiClient {
  private learningClient: GrpcServiceClient;
  private contentClient: GrpcServiceClient;
  private breaker: CircuitBreaker<
    [GrpcServiceClient, string, unknown],
    unknown
  >;
  private logger = createStandaloneLogger({ component: "AiClient" });

  constructor() {
    // In a real scenario, we might want to use different credentials or secure channels
    // using grpc.credentials.createInsecure() for internal mesh
    this.learningClient = new aiLearningService(
      GRPC_HOST,
      grpc.credentials.createInsecure(),
    );
    this.contentClient = new contentGenService(
      GRPC_HOST,
      grpc.credentials.createInsecure(),
    );

    // Initialize Circuit Breaker
    const breakerOptions = {
      timeout: 3000, // If function takes longer than 3 seconds, trigger failure
      errorThresholdPercentage: 50, // When 50% of requests fail, trip breaker
      resetTimeout: 30000, // After 30 seconds, try again
    };

    // We use a generic breaker execution wrapper
    this.breaker = new CircuitBreaker(
      this.executeGrpc.bind(this),
      breakerOptions,
    );
    this.breaker.on("open", () => {
      this.logger.warn({
        message: "AI service circuit breaker opened",
        grpcHost: GRPC_HOST,
      });
    });

    this.breaker.on("halfOpen", () => {
      this.logger.info({
        message: "AI service circuit breaker half-open",
        grpcHost: GRPC_HOST,
      });
    });

    this.breaker.on("close", () => {
      this.logger.info({
        message: "AI service circuit breaker closed",
        grpcHost: GRPC_HOST,
      });
    });

    this.breaker.on("failure", (error) => {
      this.logger.error({
        message: "AI gRPC invocation failed",
        grpcHost: GRPC_HOST,
        err: error,
      });
    });

    this.breaker.fallback(() => {
      this.logger.warn({
        message: "AI Service Circuit Breaker Open or Timeout",
        action: "fallback",
        grpcHost: GRPC_HOST,
      });
      return null; // Return null to signal fallback to caller logic
    });

    this.logger.info({
      message: "AiClient initialized",
      grpcHost: GRPC_HOST,
    });
  }

  /**
   * Generic wrapper to promisify gRPC calls, executed via the circuit breaker.
   */
  private executeGrpc(
    client: GrpcServiceClient,
    method: string,
    request: unknown,
  ): Promise<unknown> {
    return new Promise((resolve, reject) => {
      const fn = client[method];
      if (!fn) {
        reject(new Error(`Method ${method} not found on gRPC client`));
        return;
      }
      fn(request, (error, response) => {
        if (error) reject(error);
        else resolve(response);
      });
    });
  }

  // --- Learning Path Methods ---

  async generateLearningPath(
    request: GeneratePathRequest,
  ): Promise<GeneratePathResponse | null> {
    const result = await this.breaker.fire(
      this.learningClient,
      "GenerateLearningPath",
      request,
    );
    return result as GeneratePathResponse | null;
  }

  async gradeAssessment(
    request: GradeAssessmentRequest,
  ): Promise<GradeAssessmentResponse | null> {
    const result = await this.breaker.fire(
      this.learningClient,
      "GradeAssessment",
      request,
    );
    return result as GradeAssessmentResponse | null;
  }

  async generateAssessmentItems(
    request: GenerateItemsRequest,
  ): Promise<GenerateItemsResponse | null> {
    const result = await this.breaker.fire(
      this.learningClient,
      "GenerateAssessmentItems",
      request,
    );
    return result as GenerateItemsResponse | null;
  }

  async suggestRemediation(
    request: RemediationRequest,
  ): Promise<RemediationResponse | null> {
    const result = await this.breaker.fire(
      this.learningClient,
      "SuggestRemediation",
      request,
    );
    return result as RemediationResponse | null;
  }

  // --- Content Generation Methods ---

  async generateClaims(
    request: GenerateClaimsRequest,
  ): Promise<GenerateClaimsResponse | null> {
    const result = await this.breaker.fire(
      this.contentClient,
      "GenerateClaims",
      request,
    );
    return result as GenerateClaimsResponse | null;
  }

  async validateContent(
    request: ValidateContentRequest,
  ): Promise<ValidateContentResponse | null> {
    const result = await this.breaker.fire(
      this.contentClient,
      "ValidateContent",
      request,
    );
    return result as ValidateContentResponse | null;
  }

  async analyzeContentNeeds(
    request: AnalyzeContentNeedsRequest,
  ): Promise<AnalyzeContentNeedsResponse | null> {
    const result = await this.breaker.fire(
      this.contentClient,
      "AnalyzeContentNeeds",
      request,
    );
    return result as AnalyzeContentNeedsResponse | null;
  }

  async generateExamples(
    request: GenerateExamplesRequest,
  ): Promise<GenerateExamplesResponse | null> {
    const result = await this.breaker.fire(
      this.contentClient,
      "GenerateExamples",
      request,
    );
    return result as GenerateExamplesResponse | null;
  }

  async generateSimulation(
    request: GenerateSimulationRequest,
  ): Promise<GenerateSimulationResponse | null> {
    const result = await this.breaker.fire(
      this.contentClient,
      "GenerateSimulation",
      request,
    );
    return result as GenerateSimulationResponse | null;
  }
}

// Singleton instance
export const aiClient = new AiClient();
