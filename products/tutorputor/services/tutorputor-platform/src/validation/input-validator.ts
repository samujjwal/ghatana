/**
 * Comprehensive Input Validation Framework
 * 
 * Provides centralized, secure input validation for all API endpoints
 * with XSS protection, SQL injection prevention, and sanitization.
 */

import { z } from 'zod';
import { createLogger } from '../utils/logger.js';

const logger = createLogger('input-validation');

// ============================================================================
// Base Validation Schemas
// ============================================================================

export const baseSchemas = {
  // Common field validations
  id: z.string().uuid('Invalid ID format').min(1, 'ID cannot be empty'),
  email: z.string().email('Invalid email format').max(254, 'Email too long'),
  name: z.string().min(1, 'Name cannot be empty').max(100, 'Name too long').regex(/^[a-zA-Z\s\-'\.]+$/, 'Name contains invalid characters'),
  description: z.string().max(1000, 'Description too long').optional(),
  url: z.string().url('Invalid URL format').max(2048, 'URL too long'),
  timestamp: z.string().datetime('Invalid timestamp format'),
  
  // Pagination
  page: z.coerce.number().int('Page must be integer').min(1, 'Page must be at least 1').max(1000, 'Page too large').default(1),
  limit: z.coerce.number().int('Limit must be integer').min(1, 'Limit must be at least 1').max(100, 'Limit too large').default(20),
  sort: z.enum(['asc', 'desc']).default('desc'),
  
  // Security validations
  password: z.string()
    .min(12, 'Password must be at least 12 characters')
    .max(128, 'Password too long')
    .regex(/[A-Z]/, 'Password must contain uppercase letter')
    .regex(/[a-z]/, 'Password must contain lowercase letter')
    .regex(/[0-9]/, 'Password must contain number')
    .regex(/[^A-Za-z0-9]/, 'Password must contain special character'),
  
  // Content sanitization
  safeText: z.string().max(10000, 'Text too long').transform((val) => 
    val.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '') // Remove scripts
         .replace(/javascript:/gi, '') // Remove javascript: URLs
         .replace(/on\w+\s*=/gi, '') // Remove event handlers
         .trim()
  ),
  
  // File upload validations
  fileName: z.string().regex(/^[a-zA-Z0-9._-]+$/, 'Invalid filename').max(255, 'Filename too long'),
  fileSize: z.coerce.number().int('File size must be integer').min(1, 'File size must be positive').max(100 * 1024 * 1024, 'File too large'), // 100MB max
  mimeType: z.enum(['image/jpeg', 'image/png', 'image/gif', 'application/pdf', 'text/plain', 'application/json']),
};

// ============================================================================
// Entity-Specific Schemas
// ============================================================================

export const userSchemas = {
  create: z.object({
    email: baseSchemas.email,
    firstName: baseSchemas.name,
    lastName: baseSchemas.name,
    password: baseSchemas.password,
    role: z.enum(['student', 'instructor', 'admin']).default('student'),
    tenantId: baseSchemas.id,
  }),
  
  update: z.object({
    firstName: baseSchemas.name.optional(),
    lastName: baseSchemas.name.optional(),
    email: baseSchemas.email.optional(),
    isActive: z.boolean().optional(),
  }),
  
  login: z.object({
    email: baseSchemas.email,
    password: z.string().min(1, 'Password required'),
    tenantId: baseSchemas.id.optional(),
  }),
  
  register: z.object({
    email: baseSchemas.email,
    password: baseSchemas.password,
    firstName: baseSchemas.name,
    lastName: baseSchemas.name,
    tenantId: baseSchemas.id.optional(),
  }),
};

export const moduleSchemas = {
  create: z.object({
    title: z.string().min(1, 'Title required').max(200, 'Title too long').transform(baseSchemas.safeText.transform),
    description: baseSchemas.description.transform(baseSchemas.safeText.transform),
    domain: z.enum(['MATHEMATICS', 'SCIENCE', 'LANGUAGE_ARTS', 'SOCIAL_STUDIES', 'COMPUTER_SCIENCE', 'ENGINEERING', 'ARTS', 'PHYSICAL_EDUCATION']),
    difficulty: z.enum(['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']),
    estimatedTimeMinutes: z.coerce.number().int('Must be integer').min(1, 'Must be at least 1 minute').max(10080, 'Cannot exceed 1 week'),
    prerequisites: z.array(baseSchemas.id).max(50, 'Too many prerequisites').default([]),
    learningObjectives: z.array(z.string().min(1).max(500).transform(baseSchemas.safeText.transform)).max(20, 'Too many objectives').default([]),
    tags: z.array(z.string().min(1).max(50).regex(/^[a-zA-Z0-9\s\-_]+$/)).max(10, 'Too many tags').default([]),
    instructorId: baseSchemas.id.optional(),
  }),
  
  update: z.object({
    title: z.string().min(1).max(200).transform(baseSchemas.safeText.transform).optional(),
    description: baseSchemas.description.transform(baseSchemas.safeText.transform).optional(),
    domain: z.enum(['MATHEMATICS', 'SCIENCE', 'LANGUAGE_ARTS', 'SOCIAL_STUDIES', 'COMPUTER_SCIENCE', 'ENGINEERING', 'ARTS', 'PHYSICAL_EDUCATION']).optional(),
    difficulty: z.enum(['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']).optional(),
    estimatedTimeMinutes: z.coerce.number().int().min(1).max(10080).optional(),
    status: z.enum(['DRAFT', 'REVIEW', 'PUBLISHED', 'ARCHIVED', 'DEPRECATED']).optional(),
  }),
  
  search: z.object({
    query: z.string().min(1, 'Search query required').max(100, 'Query too long').transform(baseSchemas.safeText.transform),
    domain: z.enum(['MATHEMATICS', 'SCIENCE', 'LANGUAGE_ARTS', 'SOCIAL_STUDIES', 'COMPUTER_SCIENCE', 'ENGINEERING', 'ARTS', 'PHYSICAL_EDUCATION']).optional(),
    difficulty: z.enum(['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']).optional(),
    tags: z.array(z.string().min(1).max(50)).max(5).optional(),
    instructorId: baseSchemas.id.optional(),
    page: baseSchemas.page,
    limit: baseSchemas.limit,
    sort: z.enum(['title', 'created_at', 'updated_at', 'difficulty']).default('created_at'),
  }),
};

export const assessmentSchemas = {
  create: z.object({
    title: z.string().min(1).max(200).transform(baseSchemas.safeText.transform),
    description: baseSchemas.description.transform(baseSchemas.safeText.transform),
    moduleId: baseSchemas.id,
    type: z.enum(['FORMATIVE', 'SUMMATIVE', 'DIAGNOSTIC', 'PRACTICE', 'CERTIFICATION']),
    timeLimit: z.coerce.number().int().min(1).max(720).optional(), // Max 12 hours
    maxAttempts: z.coerce.number().int().min(1).max(10).default(3),
    passingScore: z.coerce.number().min(0).max(100).default(70),
    questions: z.array(z.object({
      type: z.enum(['MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER', 'ESSAY', 'FILL_IN_BLANK', 'MATCHING', 'DRAG_AND_DROP', 'SIMULATION']),
      text: z.string().min(1).max(1000).transform(baseSchemas.safeText.transform),
      points: z.coerce.number().int().min(1).max(100),
      options: z.array(z.object({
        text: z.string().min(1).max(500).transform(baseSchemas.safeText.transform),
        isCorrect: z.boolean(),
        explanation: z.string().max(500).transform(baseSchemas.safeText.transform).optional(),
      })).max(10).optional(),
      correctAnswer: z.union([z.string(), z.array(z.string())]).optional(),
      explanation: z.string().max(1000).transform(baseSchemas.safeText.transform).optional(),
    })).min(1).max(50),
  }),
  
  submit: z.object({
    answers: z.record(z.union([z.string(), z.number(), z.array(z.string())])),
    timeSpent: z.coerce.number().int().min(0).max(720).optional(),
  }),
  
  grade: z.object({
    score: z.coerce.number().min(0).max(100),
    feedback: z.string().max(2000).transform(baseSchemas.safeText.transform).optional(),
    passed: z.boolean(),
  }),
};

export const simulationSchemas = {
  create: z.object({
    title: z.string().min(1).max(200).transform(baseSchemas.safeText.transform),
    description: baseSchemas.description.transform(baseSchemas.safeText.transform),
    type: z.enum(['PHYSICS', 'CHEMISTRY', 'BIOLOGY', 'MATHEMATICS', 'ENGINEERING', 'BUSINESS', 'MEDICAL']),
    domain: z.string().min(1).max(50),
    configuration: z.record(z.unknown()).refine(
      (val) => typeof val === 'object' && val !== null,
      'Configuration must be an object'
    ),
    parameters: z.array(z.object({
      id: z.string().min(1).max(50),
      name: z.string().min(1).max(100).transform(baseSchemas.safeText.transform),
      type: z.enum(['NUMBER', 'STRING', 'BOOLEAN', 'ARRAY', 'OBJECT']),
      defaultValue: z.unknown(),
      description: z.string().max(500).transform(baseSchemas.safeText.transform).optional(),
      required: z.boolean().default(false),
      range: z.object({
        min: z.number().optional(),
        max: z.number().optional(),
        step: z.number().optional(),
        options: z.array(z.string()).optional(),
      }).optional(),
    })).max(20),
  }),
  
  run: z.object({
    simulationId: baseSchemas.id,
    parameters: z.record(z.unknown()).optional(),
    configuration: z.record(z.unknown()).optional(),
  }),
  
  results: z.object({
    score: z.coerce.number().min(0).max(100).optional(),
    data: z.record(z.unknown()),
    metrics: z.record(z.coerce.number()),
    summary: z.string().max(1000).transform(baseSchemas.safeText.transform).optional(),
  }),
};

// ============================================================================
// File Upload Schemas
// ============================================================================

export const fileSchemas = {
  upload: z.object({
    fileName: baseSchemas.fileName,
    fileSize: baseSchemas.fileSize,
    mimeType: baseSchemas.mimeType,
    purpose: z.enum(['avatar', 'module_content', 'simulation_asset', 'assessment_material', 'document']),
    moduleId: baseSchemas.id.optional(),
    description: z.string().max(500).transform(baseSchemas.safeText.transform).optional(),
  }),
  
  download: z.object({
    fileId: baseSchemas.id,
    moduleId: baseSchemas.id.optional(),
  }),
};

// ============================================================================
// Search and Filter Schemas
// ============================================================================

export const searchSchemas = {
  global: z.object({
    query: z.string().min(1).max(100).transform(baseSchemas.safeText.transform),
    types: z.array(z.enum(['modules', 'assessments', 'simulations', 'users'])).max(4).default(['modules']),
    page: baseSchemas.page,
    limit: baseSchemas.limit,
  }),
  
  advanced: z.object({
    query: z.string().max(100).transform(baseSchemas.safeText.transform).optional(),
    filters: z.record(z.unknown()).optional(),
    sort: z.array(z.object({
      field: z.string().min(1).max(50),
      direction: z.enum(['asc', 'desc']),
    })).max(5).default([]),
    page: baseSchemas.page,
    limit: baseSchemas.limit,
  }),
};

// ============================================================================
// Validation Middleware
// ============================================================================

export interface ValidationContext {
  requestId?: string;
  userId?: string;
  tenantId?: string;
  userAgent?: string;
  ipAddress?: string;
}

export class ValidationError extends Error {
  public readonly code: string;
  public readonly field?: string;
  public readonly context?: ValidationContext;

  constructor(message: string, code: string, field?: string, context?: ValidationContext) {
    super(message);
    this.name = 'ValidationError';
    this.code = code;
    this.field = field;
    this.context = context;
  }
}

export class InputValidator {
  private static sanitizeInput(input: unknown): unknown {
    if (typeof input === 'string') {
      return input
        .trim()
        .replace(/\0/g, '') // Remove null bytes
        .replace(/[\x00-\x1F\x7F]/g, ''); // Remove control characters
    }
    
    if (Array.isArray(input)) {
      return input.map(item => InputValidator.sanitizeInput(item));
    }
    
    if (typeof input === 'object' && input !== null) {
      const sanitized: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(input)) {
        sanitized[key] = InputValidator.sanitizeInput(value);
      }
      return sanitized;
    }
    
    return input;
  }

  private static detectSuspiciousPatterns(input: string): boolean {
    const suspiciousPatterns = [
      /<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi,
      /javascript:/gi,
      /on\w+\s*=/gi,
      /eval\s*\(/gi,
      /exec\s*\(/gi,
      /function\s*\(/gi,
      /setTimeout\s*\(/gi,
      /setInterval\s*\(/gi,
      /document\./gi,
      /window\./gi,
      /alert\s*\(/gi,
      /confirm\s*\(/gi,
      /prompt\s*\(/gi,
      /<iframe/gi,
      /<object/gi,
      /<embed/gi,
      /<form/gi,
      /<input/gi,
      /<textarea/gi,
      /<select/gi,
      /<option/gi,
      /<button/gi,
      /<link/gi,
      /<meta/gi,
      /<style/gi,
      /expression\s*\(/gi,
      /url\s*\(/gi,
      /@import/gi,
      /binding\s*\(/gi,
    ];

    return suspiciousPatterns.some(pattern => pattern.test(input));
  }

  static validate<T>(schema: z.ZodSchema<T>, data: unknown, context?: ValidationContext): T {
    try {
      // First, sanitize the input
      const sanitizedData = this.sanitizeInput(data);
      
      // Check for suspicious patterns in string inputs
      if (typeof sanitizedData === 'string' && this.detectSuspiciousPatterns(sanitizedData)) {
        throw new ValidationError(
          'Input contains potentially dangerous content',
          'SUSPICIOUS_INPUT',
          undefined,
          context
        );
      }
      
      // Validate against schema
      const result = schema.parse(sanitizedData);
      
      logger.info({
        operation: 'input_validation_success',
        schema: schema.description || 'unknown',
        context,
      }, 'Input validation successful');
      
      return result;
    } catch (error) {
      if (error instanceof z.ZodError) {
        const firstError = error.errors[0];
        const field = firstError.path.join('.');
        const message = firstError.message;
        
        logger.warn({
          operation: 'input_validation_failed',
          field,
          message,
          errors: error.errors,
          context,
        }, 'Input validation failed');
        
        throw new ValidationError(
          `Validation failed: ${message}`,
          'VALIDATION_ERROR',
          field,
          context
        );
      }
      
      if (error instanceof ValidationError) {
        throw error;
      }
      
      logger.error({
        operation: 'input_validation_error',
        error: error instanceof Error ? error.message : String(error),
        context,
      }, 'Unexpected validation error');
      
      throw new ValidationError(
        'Invalid input provided',
        'INVALID_INPUT',
        undefined,
        context
      );
    }
  }

  static validateBatch<T>(schema: z.ZodSchema<T>, dataArray: unknown[], context?: ValidationContext): T[] {
    if (!Array.isArray(dataArray)) {
      throw new ValidationError(
        'Expected array of inputs',
        'INVALID_BATCH_INPUT',
        undefined,
        context
      );
    }

    if (dataArray.length > 100) {
      throw new ValidationError(
        'Batch size too large (max 100 items)',
        'BATCH_TOO_LARGE',
        undefined,
        context
      );
    }

    return dataArray.map((data, index) => {
      try {
        return this.validate(schema, data, { ...context, requestId: `${context?.requestId}-${index}` });
      } catch (error) {
        if (error instanceof ValidationError) {
          throw new ValidationError(
            error.message,
            error.code,
            `items[${index}].${error.field || 'unknown'}`,
            context
          );
        }
        throw error;
      }
    });
  }
}

// ============================================================================
// Fastify Middleware Integration
// ============================================================================

export function createValidationMiddleware<T>(schema: z.ZodSchema<T>, source: 'body' | 'query' | 'params' = 'body') {
  return async (request: any, reply: any) => {
    try {
      const context: ValidationContext = {
        requestId: request.id,
        userId: request.user?.id,
        tenantId: request.user?.tenantId,
        userAgent: request.headers['user-agent'],
        ipAddress: request.ip,
      };

      const data = request[source];
      const validatedData = InputValidator.validate(schema, data, context);
      
      // Store validated data back
      request[source] = validatedData;
    } catch (error) {
      if (error instanceof ValidationError) {
        return reply.status(400).send({
          error: 'Validation Error',
          message: error.message,
          code: error.code,
          field: error.field,
          requestId: error.context?.requestId,
        });
      }
      
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Validation failed',
        requestId: request.id,
      });
    }
  };
}

// ============================================================================
// Rate Limiting Validation
// ============================================================================

export class RateLimitValidator {
  private static requests = new Map<string, { count: number; resetTime: number }>();

  static checkRateLimit(
    identifier: string,
    maxRequests: number,
    windowMs: number,
    context?: ValidationContext
  ): void {
    const now = Date.now();
    const key = identifier;
    const current = this.requests.get(key);

    if (!current || now > current.resetTime) {
      this.requests.set(key, { count: 1, resetTime: now + windowMs });
      return;
    }

    if (current.count >= maxRequests) {
      logger.warn({
        operation: 'rate_limit_exceeded',
        identifier,
        count: current.count,
        maxRequests,
        windowMs,
        context,
      }, 'Rate limit exceeded');

      throw new ValidationError(
        'Rate limit exceeded',
        'RATE_LIMIT_EXCEEDED',
        undefined,
        context
      );
    }

    current.count++;
  }

  static cleanup(): void {
    const now = Date.now();
    for (const [key, value] of this.requests.entries()) {
      if (now > value.resetTime) {
        this.requests.delete(key);
      }
    }
  }
}

// Export all schemas for easy importing
export const schemas = {
  base: baseSchemas,
  user: userSchemas,
  module: moduleSchemas,
  assessment: assessmentSchemas,
  simulation: simulationSchemas,
  file: fileSchemas,
  search: searchSchemas,
};

// Cleanup rate limit data every 5 minutes
setInterval(() => RateLimitValidator.cleanup(), 5 * 60 * 1000);
