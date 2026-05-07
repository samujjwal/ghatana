import * as grpc from "@grpc/grpc-js";
import * as protoLoader from "@grpc/proto-loader";
import path from "path";
import { fileURLToPath } from "url";
import CircuitBreaker from "opossum";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const LEARNER_PROFILE_PROTO_PATH = path.resolve(
  __dirname,
  "../../../../contracts/proto/learner_profile.proto",
);
const LEARNER_PROFILE_SERVICE_URL =
  process.env.LEARNER_PROFILE_SERVICE_URL || "localhost:50052";

export interface LearningStyleScores {
  visual: number;
  auditory: number;
  kinesthetic: number;
  reading: number;
}

export interface SessionPreferences {
  preferred_session_minutes: number;
  notification_frequency: string;
  preferred_time_of_day?: string;
}

export interface MasterySummary {
  average_mastery: number;
  concept_count: number;
  low_mastery_concepts: string[];
}

export interface LearnerProfileMessage {
  learner_id: string;
  preferred_difficulty: string;
  preferred_modality: string;
  preferred_pacing: string;
  adjusted_difficulty: string;
  preferences: string[];
  knowledge_gaps: string[];
  mastery_summary: MasterySummary;
  learning_style_scores: LearningStyleScores;
  session_preferences: SessionPreferences;
}

export interface GetProfileRequest {
  tenant_id: string;
  learner_id: string;
}

export interface GetProfileResponse {
  profile: LearnerProfileMessage;
}

export interface UpdateMasteryRequest {
  tenant_id: string;
  learner_id: string;
  concept_id: string;
  correct: boolean;
  confidence?: number;
  time_spent_seconds?: number;
  hints_used?: number;
  attempts?: number;
}

export interface UpdateMasteryResponse {
  learner_id: string;
  concept_id: string;
  mastery_probability: number;
  next_review_days: number;
}

export interface RecordGapRequest {
  tenant_id: string;
  learner_id: string;
  concept_id: string;
  prerequisite_id: string;
  severity?: string;
  detected_by?: string;
}

export interface RecordGapResponse {
  learner_id: string;
  concept_id: string;
  prerequisite_id: string;
  severity: string;
  status: string;
}

export interface GetRecommendationsRequest {
  tenant_id: string;
  learner_id: string;
  current_concept_id?: string;
  goal_concept_id?: string;
  available_time_minutes?: number;
}

export interface RecommendationMessage {
  concept_id: string;
  concept_name: string;
  type: string;
  reason: string;
  confidence: number;
  estimated_time_minutes: number;
  suggested_modality: string;
}

export interface GetRecommendationsResponse {
  recommendations: RecommendationMessage[];
}

interface GrpcServiceClient {
  [method: string]: (
    request: unknown,
    callback: (error: grpc.ServiceError | null, response: unknown) => void,
  ) => void;
}

type GrpcServiceConstructor = new (
  address: string,
  credentials: grpc.ChannelCredentials,
) => GrpcServiceClient;

const learnerProfilePackageDef = protoLoader.loadSync(
  LEARNER_PROFILE_PROTO_PATH,
  {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
  },
);
const learnerProfileProtoDescriptor = grpc.loadPackageDefinition(
  learnerProfilePackageDef,
) as unknown as {
  tutorputor: {
    learner_profile: { LearnerProfileService: GrpcServiceConstructor };
  };
};
const learnerProfileService =
  learnerProfileProtoDescriptor.tutorputor.learner_profile
    .LearnerProfileService;

export class LearnerProfileClient {
  private readonly client: GrpcServiceClient;
  private readonly breaker: CircuitBreaker<
    [GrpcServiceClient, string, unknown],
    unknown
  >;
  constructor() {
    this.client = new learnerProfileService(
      LEARNER_PROFILE_SERVICE_URL,
      grpc.credentials.createInsecure(),
    );

    this.breaker = new CircuitBreaker(this.executeGrpc.bind(this), {
      timeout: 2500,
      errorThresholdPercentage: 50,
      resetTimeout: 30000,
    });

    this.breaker.on("open", () => {
      console.warn("Learner profile circuit breaker opened", {
        grpcHost: LEARNER_PROFILE_SERVICE_URL,
      });
    });
    this.breaker.on("close", () => {
      console.info("Learner profile circuit breaker closed", {
        grpcHost: LEARNER_PROFILE_SERVICE_URL,
      });
    });
    this.breaker.on("failure", (error) => {
      console.error("Learner profile gRPC invocation failed", {
        grpcHost: LEARNER_PROFILE_SERVICE_URL,
        err: error,
      });
    });
    this.breaker.fallback(() => null);
  }

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

  async getProfile(
    request: GetProfileRequest,
  ): Promise<GetProfileResponse | null> {
    const result = await this.breaker.fire(this.client, "GetProfile", request);
    return result as GetProfileResponse | null;
  }

  async updateMastery(
    request: UpdateMasteryRequest,
  ): Promise<UpdateMasteryResponse | null> {
    const result = await this.breaker.fire(
      this.client,
      "UpdateMastery",
      normalizeUpdateMasteryRequest(request),
    );
    return result as UpdateMasteryResponse | null;
  }

  async recordGap(
    request: RecordGapRequest,
  ): Promise<RecordGapResponse | null> {
    const result = await this.breaker.fire(this.client, "RecordGap", {
      ...request,
      severity: request.severity ?? "MEDIUM",
      detected_by: request.detected_by ?? "SYSTEM",
    });
    return result as RecordGapResponse | null;
  }

  async getRecommendations(
    request: GetRecommendationsRequest,
  ): Promise<GetRecommendationsResponse | null> {
    const result = await this.breaker.fire(this.client, "GetRecommendations", {
      ...request,
      available_time_minutes: request.available_time_minutes ?? 0,
      current_concept_id: request.current_concept_id ?? "",
      goal_concept_id: request.goal_concept_id ?? "",
    });
    return result as GetRecommendationsResponse | null;
  }
}

function normalizeUpdateMasteryRequest(
  request: UpdateMasteryRequest,
): Required<UpdateMasteryRequest> {
  return {
    ...request,
    confidence: request.confidence ?? 0.5,
    time_spent_seconds: request.time_spent_seconds ?? 0,
    hints_used: request.hints_used ?? 0,
    attempts: request.attempts ?? 1,
  };
}
