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

  interface FastifyRequest {
    correlationId?: string;
  }

  interface FastifySchema {
    description?: string;
    tags?: string[];
    summary?: string;
    security?: unknown[];
  }

  /**
   * Route-level config extension.
   *
   * Set `config.public = true` on any route that must bypass the global JWT
   * authentication guard. This is the **single authoritative** mechanism for
   * declaring a route as public — the hand-maintained allow-list in setup.ts
   * has been removed (F-014).
   *
   * Example usage:
   * ```ts
   * fastify.get('/health', { config: { public: true } }, handler)
   * ```
   */
  interface FastifyContextConfig {
    /** When true, the global JWT guard skips this route. */
    public?: boolean;
  }
}
