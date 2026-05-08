/**
 * Worker Authentication Utilities
 *
 * Provides authentication for worker services using JWT tokens or mTLS.
 * Workers use a separate authentication path from user/admin authentication
 * to establish a trust boundary for background job callbacks.
 *
 * @doc.type module
 * @doc.purpose Worker authentication for job callbacks
 * @doc.layer core
 * @doc.pattern Security
 */

import jwt from "jsonwebtoken";
import type { FastifyRequest, FastifyReply } from "fastify";
import { getConfig } from "../../config/config.js";

export interface WorkerAuthContext {
  workerId: string;
  tenantId: string;
  workerType: "content-generation" | "other";
  issuedAt: number;
  expiresAt: number;
}

export interface WorkerTokenPayload {
  workerId: string;
  tenantId: string;
  workerType: string;
  iat: number;
  exp: number;
}

/**
 * Generate a worker JWT token for authentication.
 * This should be called during worker initialization and the token
 * should be stored securely for use in callback requests.
 */
export function generateWorkerToken(
  workerId: string,
  tenantId: string,
  workerType: string = "content-generation",
): string {
  const secret = process.env.WORKER_JWT_SECRET;

  if (!secret) {
    throw new Error("WORKER_JWT_SECRET must be configured for worker authentication");
  }

  const now = Math.floor(Date.now() / 1000);
  const expiresIn = 24 * 60 * 60; // 24 hours

  const payload: WorkerTokenPayload = {
    workerId,
    tenantId,
    workerType,
    iat: now,
    exp: now + expiresIn,
  };

  return jwt.sign(payload, secret, {
    algorithm: "HS256",
    expiresIn: `${expiresIn}s`,
  });
}

/**
 * Validate a worker JWT token and extract the auth context.
 * Throws an error if the token is invalid or expired.
 */
export function validateWorkerToken(token: string): WorkerAuthContext {
  const secret = process.env.WORKER_JWT_SECRET;

  if (!secret) {
    throw new Error("WORKER_JWT_SECRET must be configured for worker authentication");
  }

  try {
    const payload = jwt.verify(token, secret) as WorkerTokenPayload;

    if (!payload.workerId || !payload.tenantId || !payload.workerType) {
      throw new Error("Invalid worker token: missing required fields");
    }

    return {
      workerId: payload.workerId,
      tenantId: payload.tenantId,
      workerType: payload.workerType as WorkerAuthContext["workerType"],
      issuedAt: payload.iat,
      expiresAt: payload.exp,
    };
  } catch (error) {
    if (error instanceof jwt.JsonWebTokenError) {
      throw new Error(`Invalid worker token: ${error.message}`);
    }
    throw error;
  }
}

/**
 * Fastify preHandler to authenticate worker requests using JWT.
 * Extracts the token from the Authorization header and validates it.
 * Attaches the worker auth context to the request object.
 */
export async function workerAuthMiddleware(
  request: FastifyRequest,
  reply: FastifyReply,
): Promise<void> {
  const authHeader = request.headers.authorization;

  if (!authHeader || typeof authHeader !== "string") {
    return reply.status(401).send({
      error: "Unauthorized",
      message: "Missing Authorization header",
    });
  }

  if (!authHeader.startsWith("Bearer ")) {
    return reply.status(401).send({
      error: "Unauthorized",
      message: "Invalid Authorization header format",
    });
  }

  const token = authHeader.slice(7); // Remove "Bearer " prefix

  try {
    const authContext = validateWorkerToken(token);
    
    // Attach worker auth context to request
    (request as FastifyRequest & { workerAuth?: WorkerAuthContext }).workerAuth = authContext;
  } catch (error) {
    return reply.status(401).send({
      error: "Unauthorized",
      message: error instanceof Error ? error.message : "Invalid worker token",
    });
  }
}

/**
 * Helper to get worker auth context from request.
 * Throws if not authenticated.
 */
export function getWorkerAuth(request: FastifyRequest): WorkerAuthContext {
  const workerAuth = (request as FastifyRequest & { workerAuth?: WorkerAuthContext }).workerAuth;
  
  if (!workerAuth) {
    throw new Error("Worker authentication required");
  }
  
  return workerAuth;
}
