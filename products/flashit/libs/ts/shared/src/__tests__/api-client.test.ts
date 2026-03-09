/**
 * FlashitApiClient Tests
 * Tests for the shared API client
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { FlashitApiClient } from '../api/client';
import type { User, Sphere, Moment, CreateSphereRequest, CreateMomentRequest } from '../types';

// Mock fetch globally
global.fetch = vi.fn();

describe('FlashitApiClient', () => {
    let client: FlashitApiClient;
    let mockGetToken: ReturnType<typeof vi.fn>;
    let mockOnTokenChange: ReturnType<typeof vi.fn>;
    let mockOnUnauthorized: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        vi.resetAllMocks();
        (global.fetch as ReturnType<typeof vi.fn>).mockReset();

        mockGetToken = vi.fn().mockResolvedValue('mock-token');
        mockOnTokenChange = vi.fn().mockResolvedValue(undefined);
        mockOnUnauthorized = vi.fn();

        client = new FlashitApiClient({
            baseURL: 'http://localhost:2900',
            getToken: mockGetToken,
            onTokenChange: mockOnTokenChange,
            onUnauthorized: mockOnUnauthorized,
        });
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    describe('Authentication', () => {
        it('should login successfully', async () => {
            const mockUser: User = {
                id: 'user-123',
                email: 'test@example.com',
                displayName: 'Test User',
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            };

            const mockResponse = {
                user: mockUser,
                token: 'new-token',
            };

            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => mockResponse,
            });

            const result = await client.login('test@example.com', 'password123');

            expect(result.user).toEqual(mockUser);
            expect(result.token).toBe('new-token');
            expect(mockOnTokenChange).toHaveBeenCalledWith('new-token');
        });

        it('should register successfully', async () => {
            const mockUser: User = {
                id: 'user-123',
                email: 'test@example.com',
                displayName: 'Test User',
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            };

            const mockResponse = {
                user: mockUser,
                token: 'new-token',
            };

            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => mockResponse,
            });

            const result = await client.register('test@example.com', 'password123', 'Test User');

            expect(result.user).toEqual(mockUser);
            expect(result.token).toBe('new-token');
            expect(mockOnTokenChange).toHaveBeenCalledWith('new-token');
        });

        it('should get current user', async () => {
            const mockUser: User = {
                id: 'user-123',
                email: 'test@example.com',
                displayName: 'Test User',
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            };

            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ user: mockUser }),
            });

            const result = await client.getCurrentUser();

            expect(result).toEqual(mockUser);
            expect(mockGetToken).toHaveBeenCalled();
        });

        it('should logout successfully', async () => {
            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => ({}),
            });

            await client.logout();

            expect(mockOnTokenChange).toHaveBeenCalledWith(null);
        });

        it('should call onUnauthorized on 401 response', async () => {
            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: false,
                status: 401,
                json: async () => ({ error: 'Unauthorized' }),
            });

            await expect(client.getCurrentUser()).rejects.toThrow();
            expect(mockOnUnauthorized).toHaveBeenCalled();
        });
    });

    describe('Sphere Operations', () => {
        it('should get all spheres', async () => {
            const mockSpheres: Sphere[] = [
                {
                    id: 'sphere-1',
                    userId: 'user-123',
                    name: 'Personal',
                    description: null,
                    type: 'PERSONAL',
                    visibility: 'PRIVATE',
                    createdAt: new Date().toISOString(),
                    updatedAt: new Date().toISOString(),
                    deletedAt: null,
                },
            ];

            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => mockSpheres,
            });

            const result = await client.getSpheres();

            expect(result).toEqual(mockSpheres);
            expect(mockGetToken).toHaveBeenCalled();
        });

        it('should get sphere by id', async () => {
            const mockSphere: Sphere = {
                id: 'sphere-1',
                userId: 'user-123',
                name: 'Personal',
                description: null,
                type: 'PERSONAL',
                visibility: 'PRIVATE',
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
                deletedAt: null,
            };

            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => mockSphere,
            });

            const result = await client.getSphere('sphere-1');

            expect(result).toEqual(mockSphere);
        });

        it('should create sphere', async () => {
            const createData: CreateSphereRequest = {
                name: 'Work',
                type: 'WORK',
                visibility: 'PRIVATE',
            };

            const mockSphere: Sphere = {
                id: 'sphere-2',
                userId: 'user-123',
                name: createData.name,
                description: createData.description || null,
                type: createData.type,
                visibility: createData.visibility || 'PRIVATE',
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
                deletedAt: null,
            };

            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => mockSphere,
            });

            const result = await client.createSphere(createData);

            expect(result).toEqual(mockSphere);
        });

        it('should update sphere', async () => {
            const updateData = {
                name: 'Updated Name',
            };

            const mockSphere: Sphere = {
                id: 'sphere-1',
                userId: 'user-123',
                name: 'Updated Name',
                description: null,
                type: 'PERSONAL',
                visibility: 'PRIVATE',
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
                deletedAt: null,
            };

            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => mockSphere,
            });

            const result = await client.updateSphere('sphere-1', updateData);

            expect(result.name).toBe('Updated Name');
        });

        it('should delete sphere', async () => {
            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ success: true }),
            });

            await client.deleteSphere('sphere-1');

            expect(global.fetch).toHaveBeenCalledWith(
                expect.stringContaining('/api/spheres/sphere-1'),
                expect.objectContaining({ method: 'DELETE' })
            );
        });
    });

    describe('Moment Operations', () => {
        it('should get moments with filters', async () => {
            const mockMoments: Moment[] = [
                {
                    id: 'moment-1',
                    userId: 'user-123',
                    sphereId: 'sphere-1',
                    contentText: 'Test moment',
                    contentTranscript: null,
                    contentType: 'TEXT',
                    emotions: [],
                    tags: [],
                    intent: null,
                    sentimentScore: null,
                    importance: null,
                    entities: [],
                    capturedAt: new Date().toISOString(),
                    ingestedAt: new Date().toISOString(),
                    updatedAt: new Date().toISOString(),
                    deletedAt: null,
                    metadata: null,
                    version: 1,
                },
            ];

            const mockResponse = {
                moments: mockMoments,
                nextCursor: null,
                total: 1,
            };

            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => mockResponse,
            });

            const result = await client.getMoments({ sphereIds: ['sphere-1'] });

            expect(result).toEqual(mockResponse);
        });

        it('should create moment', async () => {
            const createData: CreateMomentRequest = {
                sphereId: 'sphere-1',
                content: {
                    text: 'New moment',
                    type: 'TEXT',
                },
            };

            const mockMoment: Moment = {
                id: 'moment-2',
                userId: 'user-123',
                sphereId: 'sphere-1',
                contentText: 'New moment',
                contentTranscript: null,
                contentType: 'TEXT',
                emotions: [],
                tags: [],
                intent: null,
                sentimentScore: null,
                importance: null,
                entities: [],
                capturedAt: new Date().toISOString(),
                ingestedAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
                deletedAt: null,
                metadata: null,
                version: 1,
            };

            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => mockMoment,
            });

            const result = await client.createMoment(createData);

            expect(result).toEqual(mockMoment);
        });

        it('should search moments', async () => {
            const mockMoments: Moment[] = [
                {
                    id: 'moment-1',
                    userId: 'user-123',
                    sphereId: 'sphere-1',
                    contentText: 'Searchable content',
                    contentTranscript: null,
                    contentType: 'TEXT',
                    emotions: [],
                    tags: [],
                    intent: null,
                    sentimentScore: null,
                    importance: null,
                    entities: [],
                    capturedAt: new Date().toISOString(),
                    ingestedAt: new Date().toISOString(),
                    updatedAt: new Date().toISOString(),
                    deletedAt: null,
                    metadata: null,
                    version: 1,
                },
            ];

            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ moments: mockMoments }),
            });

            const result = await client.searchMoments('searchable');

            expect(result).toEqual(mockMoments);
        });

        it('should delete moment', async () => {
            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ success: true }),
            });

            await client.deleteMoment('moment-1');

            expect(global.fetch).toHaveBeenCalledWith(
                expect.stringContaining('/api/moments/moment-1'),
                expect.objectContaining({ method: 'DELETE' })
            );
        });
    });

    describe('Error Handling', () => {
        it('should throw on network error', async () => {
            (global.fetch as ReturnType<typeof vi.fn>).mockRejectedValueOnce(
                new Error('Network error')
            );

            await expect(client.getSpheres()).rejects.toThrow();
        });

        it('should throw on 404 error', async () => {
            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: false,
                status: 404,
                statusText: 'Not Found',
                json: async () => ({ message: 'Not found' }),
            });

            await expect(client.getSphere('nonexistent')).rejects.toThrow();
        });

        it('should throw on 500 error', async () => {
            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: false,
                status: 500,
                statusText: 'Internal Server Error',
                json: async () => ({ message: 'Internal server error' }),
            });

            await expect(client.getSpheres()).rejects.toThrow();
        });

        it('should handle malformed JSON response', async () => {
            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => {
                    throw new Error('Invalid JSON');
                },
            });

            await expect(client.getSpheres()).rejects.toThrow();
        });
    });

    describe('Request Headers', () => {
        it('should include authorization header when token exists', async () => {
            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => [],
            });

            await client.getSpheres();

            expect(global.fetch).toHaveBeenCalledWith(
                expect.any(String),
                expect.objectContaining({
                    headers: expect.objectContaining({
                        Authorization: 'Bearer mock-token',
                    }),
                })
            );
        });

        it('should not include authorization header when no token', async () => {
            mockGetToken.mockResolvedValueOnce(null);

            const mockUser: User = {
                id: 'user-123',
                email: 'test@example.com',
                displayName: 'Test User',
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            };

            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ user: mockUser, token: 'new-token' }),
            });

            await client.login('test@example.com', 'password');

            const calls = (global.fetch as ReturnType<typeof vi.fn>).mock.calls;
            const headers = calls[0][1]?.headers as Record<string, string>;

            expect(headers?.Authorization).toBeUndefined();
        });

        it('should include content-type header for POST requests', async () => {
            (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
                ok: true,
                json: async () => ({ sphere: {} }),
            });

            await client.createSphere({
                name: 'Test',
                type: 'PERSONAL',
                visibility: 'PRIVATE',
            });

            expect(global.fetch).toHaveBeenCalledWith(
                expect.any(String),
                expect.objectContaining({
                    headers: expect.objectContaining({
                        'Content-Type': 'application/json',
                    }),
                })
            );
        });
    });
});
