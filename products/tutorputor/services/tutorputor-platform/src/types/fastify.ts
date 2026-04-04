import type { TutorPrismaClient } from "@tutorputor/core/db";
import type Redis from "ioredis";
import type { LearnerProfileService } from "../modules/learning/learner-profile-service.js";
import type { LearnerProfileGrpcRuntimeState } from "../modules/learning/grpc-runtime-state.js";
import "fastify";

/**
 * Fastify type augmentation for TutorPutor platform.
 *
 * @doc.type types
 * @doc.purpose Extend Fastify instance with custom decorators
 * @doc.layer core
 * @doc.pattern Type Augmentation
 */
declare module "fastify" {
  interface FastifyInstance {
    /**
     * Prisma database client instance
     */
    prisma: TutorPrismaClient;

    /**
     * Redis cache client instance
     */
    redis: Redis;

    /**
     * Learner personalization service shared across learning features
     */
    learnerProfileService: LearnerProfileService;

    /**
     * Optional learner-profile gRPC listener state for health and readiness checks
     */
    learnerProfileGrpcRuntimeState?: LearnerProfileGrpcRuntimeState;
  }
  interface FastifySchema {
    description?: string;
    tags?: string[];
    summary?: string;
    security?: any[];
  }
}
