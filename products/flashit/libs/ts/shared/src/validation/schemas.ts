/**
 * Zod validation schemas shared across Flashit applications
 */

import { z } from 'zod';

/**
 * User validation schemas
 */
export const loginSchema = z.object({
    email: z.string().email(),
    password: z.string().min(1),
});

export const registerSchema = z.object({
    email: z.string().email(),
    password: z.string().min(8).max(100),
    displayName: z.string().min(1).max(255).optional(),
});

/**
 * Sphere validation schemas
 */
export const sphereTypeSchema = z.enum(['PERSONAL', 'WORK', 'HEALTH', 'LEARNING', 'SOCIAL', 'CREATIVE', 'CUSTOM']);
export const sphereVisibilitySchema = z.enum(['PRIVATE', 'SHARED', 'PUBLIC']);

export const createSphereSchema = z.object({
    name: z.string().min(1).max(255),
    description: z.string().max(1000).optional(),
    type: sphereTypeSchema,
    visibility: sphereVisibilitySchema.optional(),
});

export const updateSphereSchema = z.object({
    name: z.string().min(1).max(255).optional(),
    description: z.string().max(1000).optional(),
    type: sphereTypeSchema.optional(),
    visibility: sphereVisibilitySchema.optional(),
});

/**
 * Moment validation schemas
 */
export const contentTypeSchema = z.enum(['TEXT', 'VOICE', 'VIDEO', 'IMAGE', 'MIXED']);

export const momentContentSchema = z.object({
    text: z.string().min(1),
    transcript: z.string().optional(),
    type: contentTypeSchema,
});

export const momentSignalsSchema = z.object({
    emotions: z.array(z.string()).optional(),
    tags: z.array(z.string()).optional(),
    intent: z.string().optional(),
    sentimentScore: z.number().min(-1).max(1).optional(),
    importance: z.number().int().min(1).max(5).optional(),
    entities: z.array(z.string()).optional(),
});

export const createMomentSchema = z.object({
    sphereId: z.string().uuid().optional(),
    content: momentContentSchema,
    signals: momentSignalsSchema.optional(),
    metadata: z.record(z.string(), z.unknown()).optional(),
    capturedAt: z.string().datetime().optional(),
});

export const updateMomentSchema = z.object({
    content: momentContentSchema.partial().optional(),
    signals: momentSignalsSchema.partial().optional(),
    metadata: z.record(z.string(), z.unknown()).optional(),
});

export const searchMomentsSchema = z.object({
    sphereIds: z.array(z.string().uuid()).optional(),
    query: z.string().optional(),
    tags: z.array(z.string()).optional(),
    emotions: z.array(z.string()).optional(),
    startDate: z.string().datetime().optional(),
    endDate: z.string().datetime().optional(),
    limit: z.coerce.number().int().min(1).max(100).default(20),
    cursor: z.string().optional(),
});

/**
 * Media validation schemas
 */
export const uploadUrlSchema = z.object({
    fileName: z.string().min(1),
    mimeType: z.string().min(1),
    sizeBytes: z.number().int().positive(),
});

export const progressiveUploadInitSchema = z.object({
    fileName: z.string().min(1),
    mimeType: z.string().min(1),
    totalSize: z.number().int().positive(),
    chunkSize: z.number().int().positive(),
});
