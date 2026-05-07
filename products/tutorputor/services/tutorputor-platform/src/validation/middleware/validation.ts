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

const logger = createStandaloneLogger({ component: 'ValidationMiddleware' });

/**
 * Create a validation middleware for request body
 */
export function validateBody<T>(schema: ZodType<T>) {
  return async (request: FastifyRequest, reply: FastifyReply): Promise<void> => {
    try {
      request.body = schema.parse(request.body);
    } catch (error) {
      if (error instanceof ZodError) {
        reply.status(400).send({
          error: 'Validation Error',
          message: 'Invalid request body',
          details: error.issues,
        });
        return;
      }
      throw error;
    }
  };
}

/**
 * Create a validation middleware for query parameters
 */
export function validateQuery<T>(schema: ZodType<T>) {
  return async (request: FastifyRequest, reply: FastifyReply): Promise<void> => {
    try {
      request.query = schema.parse(request.query);
    } catch (error) {
      if (error instanceof ZodError) {
        reply.status(400).send({
          error: 'Validation Error',
          message: 'Invalid query parameters',
          details: error.issues,
        });
        return;
      }
      throw error;
    }
  };
}

/**
 * Create a validation middleware for path parameters
 */
export function validateParams<T>(schema: ZodType<T>) {
  return async (request: FastifyRequest, reply: FastifyReply): Promise<void> => {
    try {
      request.params = schema.parse(request.params);
    } catch (error) {
      if (error instanceof ZodError) {
        reply.status(400).send({
          error: 'Validation Error',
          message: 'Invalid path parameters',
          details: error.issues,
        });
        return;
      }
      throw error;
    }
  };
}
