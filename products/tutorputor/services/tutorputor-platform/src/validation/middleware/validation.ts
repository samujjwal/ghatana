/**
 * Validation Middleware
 *
 * Fastify middleware for request validation using Zod schemas.
 *
 * @doc.type module
 * @doc.purpose Request validation middleware
 * @doc.layer platform
 * @doc.pattern Middleware
 */

import type { FastifyRequest, FastifyReply } from 'fastify';
import { ZodError, type ZodType } from 'zod';
import { createStandaloneLogger } from '@tutorputor/core/logger';
import { createStandardErrorResponse } from '../../core/middleware/standard-error-response.js';

const logger = createStandaloneLogger({ component: 'ValidationMiddleware' });

function isZodValidationError(error: unknown): error is ZodError {
  return (
    error instanceof ZodError ||
    (typeof error === "object" &&
      error !== null &&
      "issues" in error &&
      Array.isArray((error as { issues?: unknown }).issues))
  );
}

/**
 * Create a validation middleware for request body
 */
export function validateBody<T>(schema: ZodType<T>) {
  return async (
    request: FastifyRequest,
    reply: FastifyReply,
  ): Promise<FastifyReply | void> => {
    try {
      request.body = schema.parse(request.body);
    } catch (error) {
      if (isZodValidationError(error)) {
        const response = createStandardErrorResponse(
          'VALIDATION_ERROR',
          'Invalid request body',
          400,
          error.issues,
        );
        return reply.status(400).send(response);
      }
      throw error;
    }
  };
}

/**
 * Create a validation middleware for query parameters
 */
export function validateQuery<T>(schema: ZodType<T>) {
  return async (
    request: FastifyRequest,
    reply: FastifyReply,
  ): Promise<FastifyReply | void> => {
    try {
      request.query = schema.parse(request.query);
    } catch (error) {
      if (isZodValidationError(error)) {
        const response = createStandardErrorResponse(
          'VALIDATION_ERROR',
          'Invalid query parameters',
          400,
          error.issues,
        );
        return reply.status(400).send(response);
      }
      throw error;
    }
  };
}

/**
 * Create a validation middleware for path parameters
 */
export function validateParams<T>(schema: ZodType<T>) {
  return async (
    request: FastifyRequest,
    reply: FastifyReply,
  ): Promise<FastifyReply | void> => {
    try {
      request.params = schema.parse(request.params);
    } catch (error) {
      if (isZodValidationError(error)) {
        const response = createStandardErrorResponse(
          'VALIDATION_ERROR',
          'Invalid path parameters',
          400,
          error.issues,
        );
        return reply.status(400).send(response);
      }
      throw error;
    }
  };
}
