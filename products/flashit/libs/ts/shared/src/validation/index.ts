/**
 * Validation Schemas
 * Zod schemas for request validation
 */

import { z } from 'zod';

// Auth schemas
export const loginSchema = z.object({
    email: z.string().email().trim(),
    password: z.string().min(1),
});

export const registerSchema = z.object({
    email: z.string().email().trim(),
    password: z.string().min(6),
    displayName: z.string().optional(),
});

// Sphere schemas
export const createSphereSchema = z.object({
    name: z.string().min(1),
    type: z.enum(['PERSONAL', 'WORK', 'HEALTH', 'LEARNING', 'SOCIAL', 'CREATIVE', 'CUSTOM']),
    visibility: z.enum(['PRIVATE', 'SHARED', 'PUBLIC']),
    description: z.string().optional(),
});

export const updateSphereSchema = createSphereSchema.partial();

// Moment schemas
export const createMomentSchema = z.object({
    sphereId: z.string().uuid(),
    content: z.string().min(1),
    contentType: z.enum(['TEXT', 'VOICE', 'PHOTO', 'VIDEO']),
    mediaUrl: z.string().url().optional(),
    metadata: z.record(z.string(), z.any()).optional(),
    tags: z.array(z.string()).optional(),
});

export const updateMomentSchema = z.object({
    content: z.string().min(1).optional(),
    contentType: z.enum(['TEXT', 'VOICE', 'PHOTO', 'VIDEO']).optional(),
    mediaUrl: z.string().url().optional(),
    metadata: z.record(z.string(), z.any()).optional(),
    tags: z.array(z.string()).optional(),
});

// Export types
export type LoginSchema = z.infer<typeof loginSchema>;
export type RegisterSchema = z.infer<typeof registerSchema>;
export type CreateSphereSchema = z.infer<typeof createSphereSchema>;
export type UpdateSphereSchema = z.infer<typeof updateSphereSchema>;
export type CreateMomentSchema = z.infer<typeof createMomentSchema>;
export type UpdateMomentSchema = z.infer<typeof updateMomentSchema>;
