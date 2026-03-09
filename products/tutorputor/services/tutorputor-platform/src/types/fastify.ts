import type { TutorPrismaClient } from "@ghatana/tutorputor-db";
import type Redis from "ioredis";
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
  }
  interface FastifySchema {
    description?: string;
    tags?: string[];
    summary?: string;
    security?: any[];
  }
}
