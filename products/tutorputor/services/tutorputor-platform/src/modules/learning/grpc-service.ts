/**
 * Learner Profile gRPC Service
 *
 * Server-side gRPC boundary for learner-profile operations.
 *
 * @doc.type module
 * @doc.purpose Expose learner-profile operations over gRPC without duplicating domain logic
 * @doc.layer product
 * @doc.pattern Transport Adapter
 */

import * as grpc from "@grpc/grpc-js";
import * as protoLoader from "@grpc/proto-loader";
import path from "path";
import { fileURLToPath } from "url";
import type { createLearnerProfileService } from "./learner-profile-service.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const LEARNER_PROFILE_PROTO_PATH = path.resolve(
  __dirname,
  "../../../../contracts/proto/learner_profile.proto",
);

type LearnerProfileService = ReturnType<typeof createLearnerProfileService>;

type UnaryCall<Request> = grpc.ServerUnaryCall<Request, unknown>;
type UnaryCallback<Response> = grpc.sendUnaryData<Response>;

interface LearnerProfileServiceDefinition {
  service: grpc.ServiceDefinition;
}

export function createLearnerProfileGrpcHandlers(
  service: LearnerProfileService,
): grpc.UntypedServiceImplementation {
  return {
    async GetProfile(
      call: UnaryCall<{ tenant_id: string; learner_id: string }>,
      callback: UnaryCallback<{ profile: Record<string, unknown> }>,
    ) {
      try {
        const snapshot = await service.getPersonalizationSnapshot(
          call.request.tenant_id,
          call.request.learner_id,
        );

        callback(null, {
          profile: {
            learner_id: snapshot.learnerId,
            preferred_difficulty: snapshot.preferredDifficulty,
            preferred_modality: snapshot.preferredModality,
            preferred_pacing: snapshot.preferredPacing,
            adjusted_difficulty: snapshot.adjustedDifficulty.toUpperCase(),
            preferences: snapshot.preferences,
            knowledge_gaps: snapshot.knowledgeGaps,
            mastery_summary: {
              average_mastery: snapshot.masterySummary.averageMastery,
              concept_count: snapshot.masterySummary.conceptCount,
              low_mastery_concepts: snapshot.masterySummary.lowMasteryConcepts,
            },
            learning_style_scores: snapshot.learningStyleScores,
            session_preferences: {
              preferred_session_minutes:
                snapshot.sessionPreferences.preferredSessionMinutes,
              notification_frequency:
                snapshot.sessionPreferences.notificationFrequency,
              preferred_time_of_day:
                snapshot.sessionPreferences.preferredTimeOfDay ?? "",
            },
          },
        });
      } catch (error) {
        callback(toGrpcError(error), null);
      }
    },

    async UpdateMastery(
      call: UnaryCall<{
        tenant_id: string;
        learner_id: string;
        concept_id: string;
        correct: boolean;
        confidence?: number;
        time_spent_seconds?: number;
        hints_used?: number;
        attempts?: number;
      }>,
      callback: UnaryCallback<Record<string, unknown>>,
    ) {
      try {
        const mastery = await service.updateMastery(
          call.request.tenant_id,
          call.request.learner_id,
          {
            conceptId: call.request.concept_id,
            correct: call.request.correct,
            ...(call.request.confidence !== undefined
              ? { confidence: call.request.confidence }
              : {}),
            ...(call.request.time_spent_seconds !== undefined
              ? { timeSpentSeconds: call.request.time_spent_seconds }
              : {}),
            ...(call.request.hints_used !== undefined
              ? { hintsUsed: call.request.hints_used }
              : {}),
            ...(call.request.attempts !== undefined
              ? { attempts: call.request.attempts }
              : {}),
          },
        );

        callback(null, {
          learner_id: call.request.learner_id,
          concept_id: mastery.conceptId,
          mastery_probability: mastery.masteryProbability,
          next_review_days: calculateNextReviewDays(mastery.nextReviewAt),
        });
      } catch (error) {
        callback(toGrpcError(error), null);
      }
    },

    async RecordGap(
      call: UnaryCall<{
        tenant_id: string;
        learner_id: string;
        concept_id: string;
        prerequisite_id: string;
        severity?: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
        detected_by?:
          | "ASSESSMENT"
          | "PREREQUISITE_CHECK"
          | "ADAPTIVE_ANALYSIS"
          | "LEARNER_REPORTED"
          | "AI_PREDICTION";
      }>,
      callback: UnaryCallback<Record<string, unknown>>,
    ) {
      try {
        const gap = await service.recordKnowledgeGap(
          call.request.tenant_id,
          call.request.learner_id,
          {
            conceptId: call.request.concept_id,
            prerequisiteId: call.request.prerequisite_id,
            ...(call.request.severity !== undefined
              ? { severity: call.request.severity }
              : {}),
            ...(call.request.detected_by !== undefined
              ? { detectedBy: call.request.detected_by }
              : {}),
          },
        );

        callback(null, {
          learner_id: call.request.learner_id,
          concept_id: gap.conceptId,
          prerequisite_id: gap.prerequisiteId,
          severity: gap.severity,
          status: gap.status,
        });
      } catch (error) {
        callback(toGrpcError(error), null);
      }
    },

    async GetRecommendations(
      call: UnaryCall<{
        tenant_id: string;
        learner_id: string;
        current_concept_id?: string;
        goal_concept_id?: string;
        available_time_minutes?: number;
      }>,
      callback: UnaryCallback<{ recommendations: Record<string, unknown>[] }>,
    ) {
      try {
        const recommendations = await service.getRecommendations(
          call.request.tenant_id,
          call.request.learner_id,
          {
            ...(call.request.current_concept_id
              ? { currentConceptId: call.request.current_concept_id }
              : {}),
            ...(call.request.goal_concept_id
              ? { goalConceptId: call.request.goal_concept_id }
              : {}),
            ...(call.request.available_time_minutes !== undefined
              ? { availableTimeMinutes: call.request.available_time_minutes }
              : {}),
          },
        );

        callback(null, {
          recommendations: recommendations.map((recommendation) => ({
            concept_id: recommendation.conceptId,
            concept_name: recommendation.conceptName,
            type: recommendation.type.toUpperCase(),
            reason: recommendation.reason,
            confidence: recommendation.confidence,
            estimated_time_minutes: recommendation.estimatedTimeMinutes,
            suggested_modality: recommendation.suggestedModality,
          })),
        });
      } catch (error) {
        callback(toGrpcError(error), null);
      }
    },
  };
}

export function createLearnerProfileGrpcServer(
  service: LearnerProfileService,
): grpc.Server {
  const server = new grpc.Server();
  const definition = loadLearnerProfileServiceDefinition();
  server.addService(definition.service, createLearnerProfileGrpcHandlers(service));
  return server;
}

export async function bindLearnerProfileGrpcServer(
  server: grpc.Server,
  address: string,
): Promise<number> {
  return new Promise((resolve, reject) => {
    server.bindAsync(
      address,
      grpc.ServerCredentials.createInsecure(),
      (error, port) => {
        if (error) {
          reject(error);
          return;
        }
        resolve(port);
      },
    );
  });
}

function loadLearnerProfileServiceDefinition(): LearnerProfileServiceDefinition {
  const packageDefinition = protoLoader.loadSync(LEARNER_PROFILE_PROTO_PATH, {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
  });

  const descriptor = grpc.loadPackageDefinition(packageDefinition) as unknown as {
    tutorputor: {
      learner_profile: {
        LearnerProfileService: LearnerProfileServiceDefinition;
      };
    };
  };

  return descriptor.tutorputor.learner_profile.LearnerProfileService;
}

function toGrpcError(error: unknown): grpc.ServiceError {
  const message = error instanceof Error ? error.message : "Unknown gRPC failure";
  return {
    name: "LearnerProfileGrpcError",
    message,
    code: grpc.status.INTERNAL,
  } as grpc.ServiceError;
}

function calculateNextReviewDays(nextReviewAt: Date | null): number {
  if (!nextReviewAt) {
    return 0;
  }

  const diffMs = nextReviewAt.getTime() - Date.now();
  return Math.max(0, Math.ceil(diffMs / (24 * 60 * 60 * 1000)));
}
