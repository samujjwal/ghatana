/**
 * Request validation middleware using Zod schemas for type-safe validation.
 *
 * <p><b>Purpose</b><br>
 * Provides runtime validation of HTTP request body, query parameters, and URL
 * parameters using Zod schemas. Ensures requests match expected structure and
 * types before reaching route handlers, preventing invalid data from entering
 * the application.
 *
 * <p><b>Validation Targets</b><br>
 * - Body: POST/PUT request payloads (JSON)
 * - Query: URL query string parameters (?key=value)
 * - Params: URL path parameters (/users/:id)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * app.post('/users', 
 *   validateRequest({ body: createUserSchema }),
 *   createUserHandler
 * );
 * }</pre>
 *
 * <p><b>Error Handling</b><br>
 * On validation failure, throws RequestValidationError with 400 status and
 * detailed field-level error messages from Zod. Error middleware transforms
 * this into JSON response with field-specific validation issues.
 *
 * <p><b>Error Response Format</b><br>
 * <pre>{@code
 * {
 *   "error": "Request validation failed",
 *   "code": "validation_error",
 *   "details": [{ path: ["email"], message: "Invalid email format" }]
 * }
 * }</pre>
 *
 * @doc.type middleware
 * @doc.purpose Runtime request validation with Zod schemas
 * @doc.layer backend
 * @doc.pattern Middleware
 */
import type { Request, Response, NextFunction } from 'express';
import type { AnyZodObject, ZodError } from 'zod';

export interface ValidationSchemas {
  body?: AnyZodObject;
  query?: AnyZodObject;
  params?: AnyZodObject;
}

export class RequestValidationError extends Error {
  status = 400;
  details: ZodError['issues'];

  constructor(message: string, details: ZodError['issues']) {
    super(message);
    this.name = 'RequestValidationError';
    this.details = details;
  }
}

export function validateRequest(schemas: ValidationSchemas) {
  return (req: Request, _res: Response, next: NextFunction) => {
    try {
      if (schemas.body) {
        const result = schemas.body.safeParse(req.body);
        if (!result.success) {
          throw new RequestValidationError('Invalid request body', result.error.issues);
        }
        req.body = result.data;
      }

      if (schemas.query) {
        const result = schemas.query.safeParse(req.query);
        if (!result.success) {
          throw new RequestValidationError('Invalid query parameters', result.error.issues);
        }
        req.query = result.data;
      }

      if (schemas.params) {
        const result = schemas.params.safeParse(req.params);
        if (!result.success) {
          throw new RequestValidationError('Invalid route parameters', result.error.issues);
        }
        req.params = result.data;
      }

      next();
    } catch (error) {
      next(error);
    }
  };
}

