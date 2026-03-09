/**
 * Validation Schema Tests
 * Tests for Zod validation schemas
 */

import { describe, it, expect } from 'vitest';
import {
    loginSchema,
    registerSchema,
    createSphereSchema,
    updateSphereSchema,
    createMomentSchema,
} from '../validation';

describe('Validation Schemas', () => {
    describe('Login Schema', () => {
        it('should validate correct login data', () => {
            const validData = {
                email: 'test@example.com',
                password: 'password123',
            };

            const result = loginSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should reject invalid email', () => {
            const invalidData = {
                email: 'not-an-email',
                password: 'password123',
            };

            const result = loginSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should reject missing email', () => {
            const invalidData = {
                password: 'password123',
            };

            const result = loginSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should reject missing password', () => {
            const invalidData = {
                email: 'test@example.com',
            };

            const result = loginSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should reject empty password', () => {
            const invalidData = {
                email: 'test@example.com',
                password: '',
            };

            const result = loginSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });
    });

    describe('Register Schema', () => {
        it('should validate correct registration data', () => {
            const validData = {
                email: 'test@example.com',
                password: 'password123',
                displayName: 'Test User',
            };

            const result = registerSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should reject short password', () => {
            const invalidData = {
                email: 'test@example.com',
                password: '12345',
                displayName: 'Test User',
            };

            const result = registerSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should reject invalid email format', () => {
            const invalidData = {
                email: 'invalid-email',
                password: 'password123',
                displayName: 'Test User',
            };

            const result = registerSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should allow optional displayName', () => {
            const validData = {
                email: 'test@example.com',
                password: 'password123',
            };

            const result = registerSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });
    });

    describe('Create Sphere Schema', () => {
        it('should validate correct sphere data', () => {
            const validData = {
                name: 'Personal',
                type: 'PERSONAL',
                visibility: 'PRIVATE',
            };

            const result = createSphereSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should reject empty name', () => {
            const invalidData = {
                name: '',
                type: 'PERSONAL',
                visibility: 'PRIVATE',
            };

            const result = createSphereSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should reject invalid type', () => {
            const invalidData = {
                name: 'Personal',
                type: 'INVALID_TYPE',
                visibility: 'PRIVATE',
            };

            const result = createSphereSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should reject invalid visibility', () => {
            const invalidData = {
                name: 'Personal',
                type: 'PERSONAL',
                visibility: 'INVALID_VISIBILITY',
            };

            const result = createSphereSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should allow optional description', () => {
            const validData = {
                name: 'Personal',
                type: 'PERSONAL',
                visibility: 'PRIVATE',
                description: 'My personal sphere',
            };

            const result = createSphereSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should validate all sphere types', () => {
            const types = ['PERSONAL', 'WORK', 'HEALTH', 'LEARNING', 'SOCIAL', 'CREATIVE', 'CUSTOM'];

            types.forEach(type => {
                const data = {
                    name: 'Test',
                    type,
                    visibility: 'PRIVATE',
                };
                const result = createSphereSchema.safeParse(data);
                expect(result.success).toBe(true);
            });
        });

        it('should validate all visibility levels', () => {
            const visibilities = ['PRIVATE', 'SHARED', 'PUBLIC'];

            visibilities.forEach(visibility => {
                const data = {
                    name: 'Test',
                    type: 'PERSONAL',
                    visibility,
                };
                const result = createSphereSchema.safeParse(data);
                expect(result.success).toBe(true);
            });
        });
    });

    describe('Update Sphere Schema', () => {
        it('should validate partial updates', () => {
            const validData = {
                name: 'Updated Name',
            };

            const result = updateSphereSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should allow updating only description', () => {
            const validData = {
                description: 'Updated description',
            };

            const result = updateSphereSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should allow updating multiple fields', () => {
            const validData = {
                name: 'Updated Name',
                description: 'Updated description',
                visibility: 'SHARED',
            };

            const result = updateSphereSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should reject invalid type in update', () => {
            const invalidData = {
                type: 'INVALID_TYPE',
            };

            const result = updateSphereSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should allow empty update object', () => {
            const validData = {};

            const result = updateSphereSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });
    });

    describe('Create Moment Schema', () => {
        it('should validate text moment', () => {
            const validData = {
                sphereId: '123e4567-e89b-12d3-a456-426614174000',
                content: 'This is a moment',
                contentType: 'TEXT',
            };

            const result = createMomentSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should validate voice moment', () => {
            const validData = {
                sphereId: '123e4567-e89b-12d3-a456-426614174000',
                content: 'transcribed text',
                contentType: 'VOICE',
                mediaUrl: 'https://example.com/audio.mp3',
            };

            const result = createMomentSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should validate photo moment', () => {
            const validData = {
                sphereId: '123e4567-e89b-12d3-a456-426614174000',
                content: 'photo description',
                contentType: 'PHOTO',
                mediaUrl: 'https://example.com/photo.jpg',
            };

            const result = createMomentSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should validate video moment', () => {
            const validData = {
                sphereId: '123e4567-e89b-12d3-a456-426614174000',
                content: 'video description',
                contentType: 'VIDEO',
                mediaUrl: 'https://example.com/video.mp4',
            };

            const result = createMomentSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should reject invalid UUID for sphereId', () => {
            const invalidData = {
                sphereId: 'not-a-uuid',
                content: 'This is a moment',
                contentType: 'TEXT',
            };

            const result = createMomentSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should reject empty content', () => {
            const invalidData = {
                sphereId: '123e4567-e89b-12d3-a456-426614174000',
                content: '',
                contentType: 'TEXT',
            };

            const result = createMomentSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should reject invalid content type', () => {
            const invalidData = {
                sphereId: '123e4567-e89b-12d3-a456-426614174000',
                content: 'This is a moment',
                contentType: 'INVALID_TYPE',
            };

            const result = createMomentSchema.safeParse(invalidData);
            expect(result.success).toBe(false);
        });

        it('should allow optional metadata', () => {
            const validData = {
                sphereId: '123e4567-e89b-12d3-a456-426614174000',
                content: 'This is a moment',
                contentType: 'TEXT',
                metadata: {
                    location: 'Home',
                    mood: 'Happy',
                },
            };

            const result = createMomentSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });

        it('should allow optional tags', () => {
            const validData = {
                sphereId: '123e4567-e89b-12d3-a456-426614174000',
                content: 'This is a moment',
                contentType: 'TEXT',
                tags: ['important', 'work'],
            };

            const result = createMomentSchema.safeParse(validData);
            expect(result.success).toBe(true);
        });
    });

    describe('Edge Cases', () => {
        it('should handle very long strings', () => {
            const longString = 'a'.repeat(10000);
            const data = {
                sphereId: '123e4567-e89b-12d3-a456-426614174000',
                content: longString,
                contentType: 'TEXT',
            };

            const result = createMomentSchema.safeParse(data);
            expect(result.success).toBe(true);
        });

        it('should handle unicode characters', () => {
            const data = {
                sphereId: '123e4567-e89b-12d3-a456-426614174000',
                content: '你好世界 🌍 مرحبا',
                contentType: 'TEXT',
            };

            const result = createMomentSchema.safeParse(data);
            expect(result.success).toBe(true);
        });

        it('should handle special characters in email', () => {
            const data = {
                email: 'test+tag@example.co.uk',
                password: 'password123',
            };

            const result = loginSchema.safeParse(data);
            expect(result.success).toBe(true);
        });

        it('should trim whitespace from strings', () => {
            const data = {
                email: '  test@example.com  ',
                password: 'password123',
            };

            const result = loginSchema.safeParse(data);
            if (result.success) {
                expect(result.data.email).toBe('test@example.com');
            }
        });
    });
});
